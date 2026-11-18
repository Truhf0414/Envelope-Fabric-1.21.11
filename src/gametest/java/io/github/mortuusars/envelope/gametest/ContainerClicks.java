package io.github.mortuusars.envelope.gametest;

import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.client.gui.screen.MailboxScreen;
import io.github.mortuusars.envelope.client.gui.screen.PackageScreen;
import io.github.mortuusars.envelope.client.gui.screen.PackingScreen;
import io.github.mortuusars.envelope.world.block.mailbox.MailboxBlockEntity;
import io.github.mortuusars.envelope.world.entity.Pigeon;
import io.github.mortuusars.envelope.world.item.component.PackageContents;
import io.github.mortuusars.envelope.world.mail.address.type.BlockAddress;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.state.properties.ComparatorMode;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Port tests (1.21.11): clicking slots in Envelope's container screens through the client's own click path
 * (MultiPlayerGameMode#handleInventoryMouseClick, which since 1.21.5 hashes the components of the stacks it sends),
 * and checking on the server where the items ended up. Also a few server side behaviours found by the 1.21.1 -> 1.21.11
 * drift audit (_notes/reviews/drift-audit-20261118.md): comparators next to a removed mailbox, dispensers and pigeons.
 */
final class ContainerClicks {
    private ContainerClicks() {
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext singleplayer, Path results, List<String> problems) {
        current = context;
        List<String> lines = new ArrayList<>();
        check(lines, problems, "packing: put an item from the inventory into the box, close, get it back",
              () -> packing(context, singleplayer));
        check(lines, problems, "packing, creative: middle click (clone) the opened box in hand, then put the copy down",
              () -> creativeCloneOpenedItem(context, singleplayer));
        check(lines, problems, "package: take an item out of an opened package",
              () -> packageTakeOut(context, singleplayer));
        check(lines, problems, "mailbox: put pigeon food into its slot",
              () -> mailboxFood(context, singleplayer));
        check(lines, problems, "mailbox removed: a comparator reading it through a block drops to 0",
              () -> comparatorAfterRemoval(context, singleplayer));
        check(lines, problems, "dispenser cannot put armor on a pigeon",
              () -> dispenserPigeon(singleplayer));
        EnvelopeClientGameTest.write(results.resolve("container_clicks.txt"), lines);
    }

    private interface Case {
        String run() throws Exception;
    }

    private static ClientGameTestContext current;

    private static void check(List<String> lines, List<String> problems, String name, Case test) {
        try {
            String failure = test.run();
            if (failure != null) failure += clientState();
            if (failure == null) {
                lines.add("OK    " + name);
            } else {
                lines.add("FAIL  " + name + ": " + failure);
                problems.add("container clicks: " + name + ": " + failure);
            }
        } catch (Throwable e) {
            String text = String.valueOf(e);
            Throwable cause = e;
            while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
            if (cause != e) text += " / cause: " + cause;
            text += clientState();
            lines.add("FAIL  " + name + ": " + text);
            problems.add("container clicks: " + name + ": " + text);
        }
    }

    // -- cases

    private static String packing(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        try {
            setUp(singleplayer, GameType.SURVIVAL, inventory -> {
                inventory.setItem(0, new ItemStack(Envelope.Items.PAPER_BOX.get()));
                inventory.setItem(1, new ItemStack(Items.STONE, 4));
            });
            open(context, PackingScreen.class);
            click(context, inventorySlot(context, 1), 0, ClickType.PICKUP);
            click(context, containerSlot(context, 0), 0, ClickType.PICKUP);
            String inBox = onServer(singleplayer, player -> describe(menuContainerItem(player, 0)));
            if (!inBox.equals("4 minecraft:stone")) {
                return "after the clicks the box slot holds " + inBox;
            }
            close(context, singleplayer);
            String back = onServer(singleplayer, player -> describe(player.getInventory().getItem(1))
                  + (player.getInventory().getItem(0).has(Envelope.DataComponents.OPEN) ? ", box still marked open" : ""));
            if (!back.equals("4 minecraft:stone")) {
                return "after closing the screen without packing, inventory slot 1 holds " + back;
            }
            return null;
        } finally {
            close(context, singleplayer);
        }
    }

    private static String creativeCloneOpenedItem(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        try {
            setUp(singleplayer, GameType.CREATIVE, inventory -> inventory.setItem(0, new ItemStack(Envelope.Items.PAPER_BOX.get())));
            open(context, PackingScreen.class);
            click(context, inventorySlot(context, 0), 2, ClickType.CLONE);
            click(context, inventorySlot(context, 9), 0, ClickType.PICKUP);
            String copy = onServer(singleplayer, player -> describe(player.getInventory().getItem(9)));
            // creative clone gives a full stack
            if (!copy.endsWith(" envelope:paper_box")) {
                return "inventory slot 9 holds " + copy;
            }
            return null;
        } finally {
            close(context, singleplayer);
            singleplayer.getServer().runOnServer(server -> player(server).setGameMode(GameType.SURVIVAL));
        }
    }

    private static String packageTakeOut(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        try {
            setUp(singleplayer, GameType.SURVIVAL, inventory -> {
                ItemStack pack = new ItemStack(Envelope.Items.PACKAGE.get());
                pack.set(Envelope.DataComponents.PACKAGE_CONTENTS, new PackageContents(List.of(new ItemStack(Items.STONE, 3))));
                inventory.setItem(0, pack);
            });
            open(context, PackageScreen.class);
            click(context, containerSlot(context, 0), 0, ClickType.PICKUP);
            click(context, inventorySlot(context, 9), 0, ClickType.PICKUP);
            String taken = onServer(singleplayer, player -> describe(player.getInventory().getItem(9)));
            if (!taken.equals("3 minecraft:stone")) {
                return "inventory slot 9 holds " + taken;
            }
            return null;
        } finally {
            close(context, singleplayer);
        }
    }

    private static String mailboxFood(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        try {
            setUp(singleplayer, GameType.SURVIVAL, inventory -> inventory.setItem(1, new ItemStack(Items.WHEAT_SEEDS, 5)));
            BlockPos pos = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer player = player(server);
                ServerLevel level = player.level();
                BlockPos at = player.blockPosition().offset(0, 1, 2);
                level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(at.below(), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(at, Envelope.Blocks.MAILBOX.get().defaultBlockState(), 3);
                ((MailboxBlockEntity) level.getBlockEntity(at)).setAddress(new BlockAddress("Clicks test mailbox"));
                return at;
            });
            context.waitTicks(5);
            context.getInput().pressKey(options -> options.keyUse);
            context.waitForScreen(MailboxScreen.class);
            context.waitTicks(5);
            click(context, inventorySlot(context, 1), 0, ClickType.PICKUP);
            click(context, containerSlot(context, MailboxBlockEntity.SLOT_PIGEON_FOOD), 0, ClickType.PICKUP);
            String food = onServer(singleplayer, player -> describe(
                  ((MailboxBlockEntity) player.level().getBlockEntity(pos)).getItem(MailboxBlockEntity.SLOT_PIGEON_FOOD)));
            if (!food.equals("5 minecraft:wheat_seeds")) {
                return "the pigeon food slot holds " + food;
            }
            return null;
        } finally {
            close(context, singleplayer);
        }
    }

    /** A comparator two blocks away (behind a solid block) is only told by the removed block itself. */
    private static String comparatorAfterRemoval(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        BlockPos[] at = new BlockPos[3];
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = player(server);
            ServerLevel level = player.level();
            BlockPos mailbox = player.blockPosition().offset(6, 0, 6);
            BlockPos block = mailbox.west();
            BlockPos comparator = block.west();
            for (BlockPos pos : BlockPos.betweenClosed(comparator.offset(-1, -1, -1), mailbox.offset(1, 2, 1))) {
                level.setBlock(pos, pos.getY() < mailbox.getY() ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
            }
            level.setBlock(block, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(mailbox, Envelope.Blocks.MAILBOX.get().defaultBlockState(), 3);
            MailboxBlockEntity entity = (MailboxBlockEntity) level.getBlockEntity(mailbox);
            entity.setAddress(new BlockAddress("Comparator test mailbox"));
            entity.addMail(new ItemStack(Envelope.Items.LETTER.get()));
            level.setBlock(comparator, Blocks.COMPARATOR.defaultBlockState()
                  .setValue(ComparatorBlock.FACING, Direction.EAST)
                  .setValue(ComparatorBlock.MODE, ComparatorMode.COMPARE), 3);
            at[0] = mailbox;
            at[1] = comparator;
        });
        context.waitTicks(10);
        int before = comparatorOutput(singleplayer, at[1]);
        if (before != 15) {
            return "set-up: the comparator reads " + before + " from the mailbox with mail, expected 15";
        }
        singleplayer.getServer().runOnServer(server -> player(server).level().destroyBlock(at[0], false));
        context.waitTicks(10);
        int after = comparatorOutput(singleplayer, at[1]);
        return after == 0 ? null : "after the mailbox was removed the comparator still reads " + after;
    }

    private static String dispenserPigeon(TestSingleplayerContext singleplayer) {
        return singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = player(server);
            Pigeon pigeon = Envelope.EntityTypes.PIGEON.get().create(player.level(), EntitySpawnReason.COMMAND);
            pigeon.setPos(player.getX() + 3, player.getY(), player.getZ());
            List<String> equippable = new ArrayList<>();
            for (ItemStack stack : List.of(new ItemStack(Items.IRON_HELMET), new ItemStack(Items.CARVED_PUMPKIN),
                  new ItemStack(Items.LEATHER_CHESTPLATE))) {
                if (pigeon.canEquipWithDispenser(stack)) {
                    equippable.add(stack.getItem().toString());
                }
            }
            pigeon.discard();
            return equippable.isEmpty() ? null : "a dispenser would put on " + equippable;
        });
    }

    // -- helpers

    private static int failureShots = 0;

    private static String clientState() {
        try {
            current.takeScreenshot("container_clicks_failure_" + (failureShots++));
            return current.computeOnClient(client -> " [client: screen " + (client.screen == null ? "none" : client.screen.getClass().getSimpleName())
                  + ", menu " + client.player.containerMenu.getClass().getSimpleName()
                  + ", carried " + describe(client.player.containerMenu.getCarried())
                  + ", hit " + client.hitResult.getType() + "]");
        } catch (Throwable e) {
            return " [client state unavailable: " + e + "]";
        }
    }

    private interface InventorySetUp {
        void apply(Inventory inventory);
    }

    private static void setUp(TestSingleplayerContext singleplayer, GameType mode, InventorySetUp setUp) {
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = player(server);
            for (BlockPos pos : BlockPos.betweenClosed(player.blockPosition().offset(-1, 0, 1), player.blockPosition().offset(1, 2, 4))) {
                player.level().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
            player.setGameMode(mode);
            player.getInventory().clearContent();
            player.getInventory().setSelectedSlot(0);
            setUp.apply(player.getInventory());
            player.inventoryMenu.broadcastChanges();
        });
    }

    private static void open(ClientGameTestContext context, Class<? extends Screen> screen) {
        context.waitTicks(5);
        context.getInput().pressKey(options -> options.keyUse);
        context.waitForScreen(screen);
        context.waitTicks(5);
    }

    private static void click(ClientGameTestContext context, int slot, int button, ClickType type) {
        context.runOnClient(client -> client.gameMode.handleInventoryMouseClick(
              client.player.containerMenu.containerId, slot, button, type, client.player));
        context.waitTicks(4);
    }

    private static int inventorySlot(ClientGameTestContext context, int index) {
        return findSlot(context, slot -> slot.container instanceof Inventory && slot.getContainerSlot() == index,
              "player inventory slot " + index);
    }

    private static int containerSlot(ClientGameTestContext context, int index) {
        return findSlot(context, slot -> !(slot.container instanceof Inventory) && slot.getContainerSlot() == index,
              "container slot " + index);
    }

    private static int findSlot(ClientGameTestContext context, Predicate<Slot> which, String what) {
        int id = context.computeOnClient(client -> {
            AbstractContainerMenu menu = client.player.containerMenu;
            for (Slot slot : menu.slots) {
                if (which.test(slot)) return slot.index;
            }
            return -1;
        });
        if (id < 0) throw new IllegalStateException("the open menu has no " + what);
        return id;
    }

    private static ItemStack menuContainerItem(ServerPlayer player, int index) {
        for (Slot slot : player.containerMenu.slots) {
            if (!(slot.container instanceof Inventory) && slot.getContainerSlot() == index) return slot.getItem();
        }
        return ItemStack.EMPTY;
    }

    private static int comparatorOutput(TestSingleplayerContext singleplayer, BlockPos pos) {
        return singleplayer.getServer().computeOnServer(server ->
              player(server).level().getBlockEntity(pos) instanceof ComparatorBlockEntity comparator ? comparator.getOutputSignal() : -1);
    }

    private interface ServerRead {
        String read(ServerPlayer player);
    }

    private static String onServer(TestSingleplayerContext singleplayer, ServerRead read) {
        return singleplayer.getServer().computeOnServer(server -> read.read(player(server)));
    }

    private static ServerPlayer player(net.minecraft.server.MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static String describe(ItemStack stack) {
        return stack.isEmpty() ? "nothing" : stack.getCount() + " " + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    private static void close(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        context.runOnClient(client -> {
            if (client.screen != null) client.screen.onClose();
        });
        context.waitTicks(3);
        context.setScreen(() -> null);
        singleplayer.getServer().runOnServer(server -> player(server).closeContainer());
        context.waitTicks(3);
    }
}
