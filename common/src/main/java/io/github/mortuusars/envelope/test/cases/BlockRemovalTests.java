package io.github.mortuusars.envelope.test.cases;

import com.mojang.serialization.DataResult;
import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.lib.mortaar.bugger.test.BuggerTests;
import io.github.mortuusars.envelope.world.block.LetterBlockEntity;
import io.github.mortuusars.envelope.world.block.PackageBlockEntity;
import io.github.mortuusars.envelope.world.block.mailbox.InboxStorage;
import io.github.mortuusars.envelope.world.block.mailbox.MailboxBlockEntity;
import io.github.mortuusars.envelope.world.item.component.Id;
import io.github.mortuusars.envelope.world.mail.MailService;
import io.github.mortuusars.envelope.world.mail.address.type.BlockAddress;
import io.github.mortuusars.envelope.world.mail.address.type.CustomAddress;
import io.github.mortuusars.envelope.world.mail.delivery.Delivery;
import io.github.mortuusars.envelope.world.mail.delivery.DeliveryPhase;
import io.github.mortuusars.envelope.world.mail.delivery.DeliveryRoute;
import io.github.mortuusars.envelope.world.mail.dropoff.BlockDropOffHandler;
import io.github.mortuusars.envelope.world.mail.dropoff.MailDropOffContext;
import io.github.mortuusars.envelope.world.mail.dropoff.MailDropOffResult;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Port tests (1.21.11, phase 2 D7): block removal matrix.
 * <p>
 * 1.21.11 split Block#onRemove into BlockEntity#preRemoveSideEffects (drops, clearing mail) and
 * Block#affectNeighborsAfterRemoval (unregistering the mailbox). LevelChunk skips both when the update flags contain
 * 256 or lack 1/64, which is what /setblock, /fill and /clone (non-destroy modes) do. Those leftovers are cleaned
 * passively when mail is sent to the address (port decision D8, {@code Mailboxes#removeIfMailboxIsGone}), so the
 * command cases send one letter after the removal and expect it to be returned before checking for leftovers.
 * <p>
 * Pistons, water and CarryOn need ticks or another mod and are checked by hand in phase 3b.
 */
public class BlockRemovalTests extends BuggerTests {
    private static final Item MARKER = Items.DIAMOND;
    private static final int MARKER_COUNT = 7;

    private enum Removal {
        SURVIVAL_BREAK, CREATIVE_BREAK, EXPLOSION, SETBLOCK_AIR, FILL_AIR, CLONE_OVER, REPLACED_BY_BLOCK;

        /**
         * Vanilla commands replace blocks without drops and without removal side effects, so the mailbox is only
         * cleaned up when mail is sent to it (D8).
         */
        boolean isCommand() {
            return this == SETBLOCK_AIR || this == FILL_AIR || this == CLONE_OVER;
        }

        boolean dropsContents() {
            return !isCommand();
        }
    }

    public BlockRemovalTests() {
        for (Removal removal : Removal.values()) {
            add("BlockRemoval_Mailbox_" + removal, player -> mailbox(player, removal, true));
            // Control group: the same removal on an empty mailbox must find no marker drops,
            // which shows the drop count can tell "dropped" from "not dropped".
            add("BlockRemoval_EmptyMailboxControl_" + removal, player -> mailbox(player, removal, false));
        }

        // Control group for the send step: a letter sent to a mailbox that is still in place must be delivered,
        // which shows the send step can tell "delivered" from "returned".
        add("BlockRemoval_SendToIntactMailboxControl", BlockRemovalTests::sendToIntactMailbox);

        add("BlockRemoval_Package_SurvivalBreak", player -> singleDrop(player, Envelope.Blocks.PACKAGE.get().defaultBlockState(),
              Envelope.Items.PACKAGE.get(), (level, pos) -> {
                  if (level.getBlockEntity(pos) instanceof PackageBlockEntity be) {
                      be.setUnpackWhenBroken(false);
                      be.setPackage(new ItemStack(Envelope.Items.PACKAGE.get()));
                  }
              }));
        add("BlockRemoval_Letter_SurvivalBreak", player -> singleDrop(player, Envelope.Blocks.LETTER.get().defaultBlockState(),
              Envelope.Items.LETTER.get(), (level, pos) -> {
                  if (level.getBlockEntity(pos) instanceof LetterBlockEntity be) {
                      be.setLetter(new ItemStack(Envelope.Items.LETTER.get()));
                  }
              }));
    }

    // --

    private static DataResult<Boolean> mailbox(ServerPlayer player, Removal removal, boolean withContents) {
        ServerLevel level = player.level();
        BlockPos pos = SaveLoadTests.testPos(player);
        clearItems(level, pos);

        level.setBlock(pos, Envelope.Blocks.MAILBOX.get().defaultBlockState(), 3);
        if (!(level.getBlockEntity(pos) instanceof MailboxBlockEntity mailbox)) {
            return DataResult.error(() -> "No mailbox block entity at " + pos);
        }
        mailbox.setAddress(new BlockAddress("envelope_test_removal"));
        BlockAddress address = mailbox.getAddress();
        if (withContents) {
            mailbox.setItem(0, new ItemStack(MARKER, MARKER_COUNT));
            mailbox.addMail(new ItemStack(Items.PAPER));
        }

        remove(player, level, pos, removal);

        List<String> problems = new ArrayList<>();

        if (removal.isCommand()) {
            MailDropOffResult result = send(level, address);
            if (!(result instanceof MailDropOffResult.Returned)) {
                problems.add("a letter sent after the removal was not returned (result: "
                      + (result == MailDropOffResult.CONSUME ? "CONSUME" : result == MailDropOffResult.PASS ? "PASS" : result) + ")");
            }
        }

        int expectedDrops = withContents && removal.dropsContents() ? MARKER_COUNT : 0;
        int drops = countDropped(level, pos, MARKER);
        if (drops != expectedDrops) {
            problems.add("dropped " + drops + " marker items, expected " + expectedDrops);
        }

        if (MailService.of(level).getMailboxes().getAtPosition(pos) != null) {
            problems.add("mailbox is still registered at " + pos.toShortString());
        }

        if (InboxStorage.get(level).getForDelivery(address).isPresent()) {
            problems.add("InboxStorage still holds an inbox for " + address.getString());
        }

        // Placing a mailbox at the same spot must not bring the old mail back
        level.setBlock(pos, Envelope.Blocks.MAILBOX.get().defaultBlockState(), 3);
        if (level.getBlockEntity(pos) instanceof MailboxBlockEntity replaced && !replaced.getAllMail().isEmpty()) {
            problems.add("a new mailbox at the same position has old mail: " + replaced.getAllMail());
        }

        cleanUp(level, pos);
        return problems.isEmpty()
              ? DataResult.success(true)
              : DataResult.error(() -> removal + (withContents ? "" : " (empty control)") + ": " + String.join("; ", problems));
    }

    private static DataResult<Boolean> sendToIntactMailbox(ServerPlayer player) {
        ServerLevel level = player.level();
        BlockPos pos = SaveLoadTests.testPos(player);
        clearItems(level, pos);
        level.setBlock(pos, Envelope.Blocks.MAILBOX.get().defaultBlockState(), 3);
        if (!(level.getBlockEntity(pos) instanceof MailboxBlockEntity mailbox)) {
            return DataResult.error(() -> "No mailbox block entity at " + pos);
        }
        mailbox.setAddress(new BlockAddress("envelope_test_removal"));
        BlockAddress address = mailbox.getAddress();

        MailDropOffResult result = send(level, address);
        boolean registered = MailService.of(level).getMailboxes().getAtPosition(pos) != null;
        int mailCount = mailbox.getAllMail().size();

        cleanUp(level, pos);
        if (result != MailDropOffResult.CONSUME || !registered || mailCount != 1) {
            return DataResult.error(() -> "intact mailbox: expected the letter to be delivered (CONSUME, still registered, 1 mail), got "
                  + (result == MailDropOffResult.CONSUME ? "CONSUME" : result) + ", registered=" + registered + ", mail=" + mailCount);
        }
        return DataResult.success(true);
    }

    /** Sends one letter to the address through the same handler couriers use when they arrive. */
    private static MailDropOffResult send(ServerLevel level, BlockAddress address) {
        Delivery delivery = new Delivery(
              Id.createUnsafe(0),
              Optional.empty(),
              new CustomAddress(Component.literal("envelope removal test")),
              address,
              new ItemStack(Envelope.Items.LETTER.get()),
              DeliveryRoute.EMPTY,
              DeliveryPhase.HANDLING_DELIVERY,
              0,
              false
        );
        return new BlockDropOffHandler().handle(new MailDropOffContext(MailService.of(level), address, delivery));
    }

    private static DataResult<Boolean> singleDrop(ServerPlayer player, BlockState state, Item expected,
                                                  BiConsumer<ServerLevel, BlockPos> fill) {
        ServerLevel level = player.level();
        BlockPos pos = SaveLoadTests.testPos(player);
        clearItems(level, pos);
        level.setBlock(pos, state, 3);
        fill.accept(level, pos);

        remove(player, level, pos, Removal.SURVIVAL_BREAK);

        int drops = countDropped(level, pos, expected);
        cleanUp(level, pos);
        // Exactly one: zero means the item was lost, two means the block entity and the loot table both dropped it.
        return drops == 1
              ? DataResult.success(true)
              : DataResult.error(() -> state + ": dropped " + drops + " x " + expected + ", expected exactly 1");
    }

    private static void remove(ServerPlayer player, ServerLevel level, BlockPos pos, Removal removal) {
        MinecraftServer server = level.getServer();
        String xyz = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        switch (removal) {
            case SURVIVAL_BREAK -> breakAs(player, pos, GameType.SURVIVAL);
            case CREATIVE_BREAK -> breakAs(player, pos, GameType.CREATIVE);
            case EXPLOSION -> level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1.5f, Level.ExplosionInteraction.TNT);
            case SETBLOCK_AIR -> server.getCommands().performPrefixedCommand(
                  server.createCommandSourceStack().withSuppressedOutput(), "setblock " + xyz + " air");
            case FILL_AIR -> server.getCommands().performPrefixedCommand(
                  server.createCommandSourceStack().withSuppressedOutput(), "fill " + xyz + " " + xyz + " air");
            case CLONE_OVER -> {
                // Copy an air block (default "replace" mode copies air too) over the mailbox
                BlockPos source = pos.above(2);
                level.setBlock(source, Blocks.AIR.defaultBlockState(), 3);
                String src = source.getX() + " " + source.getY() + " " + source.getZ();
                server.getCommands().performPrefixedCommand(
                      server.createCommandSourceStack().withSuppressedOutput(), "clone " + src + " " + src + " " + xyz);
            }
            case REPLACED_BY_BLOCK -> level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
        }
    }

    private static void breakAs(ServerPlayer player, BlockPos pos, GameType gameType) {
        GameType previous = player.gameMode.getGameModeForPlayer();
        player.setGameMode(gameType);
        try {
            player.gameMode.destroyBlock(pos);
        } finally {
            player.setGameMode(previous);
        }
    }

    private static int countDropped(ServerLevel level, BlockPos pos, Item item) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).stream()
              .map(ItemEntity::getItem)
              .filter(stack -> stack.is(item))
              .mapToInt(ItemStack::getCount)
              .sum();
    }

    private static void clearItems(ServerLevel level, BlockPos pos) {
        level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).forEach(ItemEntity::discard);
    }

    private static void cleanUp(ServerLevel level, BlockPos pos) {
        level.removeBlockEntity(pos);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        MailService.of(level).getMailboxes().remove(pos);
        // Keep cases independent: a leftover stored inbox from one case must not show up in the next one
        InboxStorage.get(level).removeByAddress(new BlockAddress("envelope_test_removal"));
        clearItems(level, pos);
    }
}
