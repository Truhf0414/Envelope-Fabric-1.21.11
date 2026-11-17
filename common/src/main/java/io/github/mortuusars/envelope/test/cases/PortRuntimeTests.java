package io.github.mortuusars.envelope.test.cases;

import com.mojang.serialization.DataResult;
import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.advancements.predicate.ItemOccludingBlockPredicate;
import io.github.mortuusars.envelope.lib.mortaar.bugger.test.BuggerTests;
import io.github.mortuusars.envelope.lib.mortaar.util.GameTimeId;
import io.github.mortuusars.envelope.network.packet.clientbound.*;
import io.github.mortuusars.envelope.network.packet.serverbound.*;
import io.github.mortuusars.envelope.world.inventory.MailboxMenu;
import io.github.mortuusars.envelope.world.item.component.SealLock;
import io.github.mortuusars.envelope.world.mail.MailService;
import io.github.mortuusars.envelope.world.mail.MailServiceHolder;
import io.github.mortuusars.envelope.world.mail.address.AllAddresses;
import io.github.mortuusars.envelope.world.mail.address.type.BlockAddress;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Port tests (1.21.11, phase 3b): things that compile fine but can only be checked in a running game.
 * <ul>
 *     <li>every packet's stream codec reads back what it wrote</li>
 *     <li>every registered object in {@link Envelope} resolves, and block items use the block name prefix</li>
 *     <li>loaded Envelope recipes survive their serializer's stream codec (used by recipe sync since phase 3)</li>
 *     <li>the item-occluding-block predicate (rewritten as a DataComponentPredicate)</li>
 *     <li>the mail service mixin is applied to the server level</li>
 * </ul>
 */
public class PortRuntimeTests extends BuggerTests {
    public PortRuntimeTests() {
        packets();

        add("Port_Registry_AllSuppliersResolve", PortRuntimeTests::allSuppliersResolve);
        add("Port_Registry_BlockItemsUseBlockNamePrefix", PortRuntimeTests::blockItemsUseBlockPrefix);
        add("Port_Recipes_StreamCodecRoundTrip", PortRuntimeTests::recipesRoundTrip);
        add("Port_Recipes_EquineAssuranceRecipeIsLoaded", PortRuntimeTests::equineAssuranceRecipe);
        add("Port_Predicate_ItemOccludingBlock", PortRuntimeTests::occludingPredicate);
        add("Port_Mixin_ServerLevelHoldsMailService", PortRuntimeTests::mailServiceHolder);
    }

    // -- Packets

    private void packets() {
        BlockAddress block = new BlockAddress("envelope_test_packet");
        AllAddresses addresses = AllAddresses.blocks(Set.of(block));
        BlockPos pos = new BlockPos(12, 64, -7);
        ItemStack letter = new ItemStack(Envelope.Items.LETTER.get());

        packet("MailboxHasNewMail", ClientboundMailboxHasNewMailPacket.STREAM_CODEC, p -> ClientboundMailboxHasNewMailPacket.INSTANCE);
        packet("MailboxMenuMailRemoved", ClientboundMailboxMenuMailRemovedPacket.STREAM_CODEC, p -> new ClientboundMailboxMenuMailRemovedPacket(3));
        packet("MailboxMenuSetMail", ClientboundMailboxMenuSetMailPacket.STREAM_CODEC,
              p -> new ClientboundMailboxMenuSetMailPacket(List.of(letter.copy(), new ItemStack(Items.PAPER, 5))));
        packet("OpenAddressTagScreen", ClientboundOpenAddressTagScreenPacket.STREAM_CODEC,
              p -> new ClientboundOpenAddressTagScreenPacket(InteractionHand.OFF_HAND, addresses));
        packet("OpenLetterBlockViewScreen", ClientboundOpenLetterBlockViewScreenPacket.STREAM_CODEC,
              p -> new ClientboundOpenLetterBlockViewScreenPacket(letter.copy(), pos));
        packet("OpenLetterEditScreen", ClientboundOpenLetterEditScreenPacket.STREAM_CODEC,
              p -> new ClientboundOpenLetterEditScreenPacket(InteractionHand.MAIN_HAND));
        packet("OpenLetterViewScreen", ClientboundOpenLetterViewScreenPacket.STREAM_CODEC,
              p -> new ClientboundOpenLetterViewScreenPacket(InteractionHand.OFF_HAND));
        packet("OpenMailboxAddressTagScreen", ClientboundOpenMailboxAddressTagScreenPacket.STREAM_CODEC,
              p -> new ClientboundOpenMailboxAddressTagScreenPacket(InteractionHand.MAIN_HAND, addresses, pos, block));
        packet("OpenMailboxPlacingScreen", ClientboundOpenMailboxPlacingScreenPacket.STREAM_CODEC,
              p -> new ClientboundOpenMailboxPlacingScreenPacket(InteractionHand.MAIN_HAND,
                    new BlockHitResult(new Vec3(12.5, 64, -6.5), Direction.UP, pos, false), addresses));
        packet("SyncDeathLockData", ClientboundSyncDeathLockDataPacket.STREAM_CODEC,
              p -> new ClientboundSyncDeathLockDataPacket(List.of(
                    new SealLock(p.getScoreboardName(), GameTimeId.createChecked(p.level().getGameTime(), false)))));

        packet("AddressTagApply", ServerboundAddressTagApplyPacket.STREAM_CODEC, p -> new ServerboundAddressTagApplyPacket(4, Optional.of(block)));
        packet("AddressTagApply_Empty", ServerboundAddressTagApplyPacket.STREAM_CODEC, p -> new ServerboundAddressTagApplyPacket(4, Optional.empty()));
        packet("ItemClosed", ServerboundItemClosedPacket.STREAM_CODEC, p -> new ServerboundItemClosedPacket(40));
        packet("LetterEdit", ServerboundLetterEditPacket.STREAM_CODEC, p -> new ServerboundLetterEditPacket(2, "Dear tester,\n§lhello", true));
        packet("MailboxAddressTagApply", ServerboundMailboxAddressTagApplyPacket.STREAM_CODEC,
              p -> new ServerboundMailboxAddressTagApplyPacket(InteractionHand.MAIN_HAND, "envelope_test_packet", pos));
        packet("MailboxMenuInboxAction", ServerboundMailboxMenuInboxActionPacket.STREAM_CODEC,
              p -> new ServerboundMailboxMenuInboxActionPacket(1, MailboxMenu.MailAction.MOVE_ALL_TO_INVENTORY));
        packet("MailboxPlace", ServerboundMailboxPlacePacket.STREAM_CODEC,
              p -> new ServerboundMailboxPlacePacket(InteractionHand.MAIN_HAND, "envelope_test_packet",
                    new BlockHitResult(new Vec3(12.5, 64, -6.5), Direction.NORTH, pos, true)));
        packet("PackingMenuPresetAddress", ServerboundPackingMenuPresetAddressPacket.STREAM_CODEC,
              p -> new ServerboundPackingMenuPresetAddressPacket(Optional.of(block)));
        packet("PaybackTagMenuSetSlot", ServerboundPaybackTagMenuSetSlotPacket.STREAM_CODEC,
              p -> new ServerboundPaybackTagMenuSetSlotPacket(0, new ItemStack(Items.EMERALD, 12)));
    }

    private <T> void packet(String name, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, java.util.function.Function<ServerPlayer, T> factory) {
        add("Port_Packet_" + name, player -> {
            try {
                T value = factory.apply(player);
                byte[] written = encode(player, codec, value);
                if (written.length == 0 && !name.equals("MailboxHasNewMail")) {
                    return DataResult.error(() -> name + ": packet encodes to nothing, the round trip would prove nothing");
                }
                T decoded = decode(player, codec, written);
                byte[] rewritten = encode(player, codec, decoded);
                if (!Arrays.equals(written, rewritten)) {
                    return DataResult.error(() -> name + ": read back differs (" + written.length + " bytes written, "
                          + rewritten.length + " bytes after reading back). Sent: " + value + ", got: " + decoded);
                }
                // Equal bytes only show that what was written survives. A field the codec never writes would be lost
                // on both sides, so the decoded packet is also compared with the original field by field (records
                // print every field; item stacks print count and item).
                if (!describe(value).equals(describe(decoded))) {
                    return DataResult.error(() -> name + ": decoded packet is not the one that was sent. Sent: " + value + ", got: " + decoded);
                }
                return DataResult.success(true);
            } catch (Exception e) {
                return DataResult.error(() -> name + ": " + e);
            }
        });
    }

    /** Text form of a packet without object identity hashes (BlockHitResult has no toString of its own). */
    private static String describe(Object packet) {
        return String.valueOf(packet).replaceAll("@[0-9a-f]+", "");
    }

    private static <T> byte[] encode(ServerPlayer player, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, T value) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
        try {
            codec.encode(buffer, value);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return bytes;
        } finally {
            buffer.release();
        }
    }

    private static <T> T decode(ServerPlayer player, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, byte[] bytes) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes), player.registryAccess());
        try {
            T value = codec.decode(buffer);
            if (buffer.isReadable()) {
                throw new IllegalStateException(buffer.readableBytes() + " bytes were left unread");
            }
            return value;
        } finally {
            buffer.release();
        }
    }

    // -- Registries

    /**
     * Every public static Supplier in Envelope's registry holder classes must resolve to a non-null object.
     * A failed or skipped registration shows up here instead of as a crash much later.
     */
    private static DataResult<Boolean> allSuppliersResolve(ServerPlayer player) {
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (Class<?> holder : Envelope.class.getDeclaredClasses()) {
            for (Field field : holder.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || !Supplier.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = ((Supplier<?>) field.get(null)).get();
                    checked++;
                    if (value == null) {
                        problems.add(holder.getSimpleName() + "." + field.getName() + " is null");
                    }
                } catch (Throwable e) {
                    problems.add(holder.getSimpleName() + "." + field.getName() + ": " + e);
                }
            }
        }
        int total = checked;
        if (total < 50) {
            return DataResult.error(() -> "only " + total + " registry suppliers were found, the check is not looking at the right classes");
        }
        return problems.isEmpty() ? DataResult.success(true)
              : DataResult.error(() -> problems.size() + " of " + total + " failed: " + String.join("; ", problems));
    }

    private static DataResult<Boolean> blockItemsUseBlockPrefix(ServerPlayer player) {
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (!id.getNamespace().equals(Envelope.ID) || !(item instanceof BlockItem blockItem)) {
                continue;
            }
            checked++;
            // 1.21.2+: items get "item.<ns>.<path>" unless registered with useBlockDescriptionPrefix; the lang files
            // name blocks as "block.envelope.*", so a wrong prefix shows as a raw key in game.
            String expected = blockItem.getBlock().getDescriptionId();
            if (!item.getDescriptionId().equals(expected)) {
                problems.add(id + " has name key '" + item.getDescriptionId() + "', its block uses '" + expected + "'");
            }
        }
        int total = checked;
        if (total == 0) {
            return DataResult.error(() -> "no envelope block items were found, the check found nothing to look at");
        }
        return problems.isEmpty() ? DataResult.success(true) : DataResult.error(() -> String.join("; ", problems));
    }

    // -- Recipes

    /**
     * Every loaded Envelope recipe goes through its serializer's stream codec and back. Recipes were not sent to
     * clients between 1.21.2 and this port's recipe sync, so these codecs had no other chance to be exercised.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static DataResult<Boolean> recipesRoundTrip(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            if (!holder.id().identifier().getNamespace().equals(Envelope.ID)) {
                continue;
            }
            checked++;
            Recipe<?> recipe = holder.value();
            StreamCodec codec = ((RecipeSerializer) recipe.getSerializer()).streamCodec();
            try {
                byte[] written = encode(player, codec, recipe);
                Object decoded = decode(player, codec, written);
                byte[] rewritten = encode(player, codec, decoded);
                if (!Arrays.equals(written, rewritten)) {
                    problems.add(holder.id().identifier() + ": read back differs");
                }
            } catch (Exception e) {
                problems.add(holder.id().identifier() + ": " + e);
            }
        }
        int total = checked;
        if (total == 0) {
            return DataResult.error(() -> "no envelope recipes are loaded, there was nothing to check");
        }
        return problems.isEmpty() ? DataResult.success(true)
              : DataResult.error(() -> problems.size() + " of " + total + " failed: " + String.join("; ", problems));
    }

    /**
     * EquineAssuranceBureau finds its recipe by id (address id with a "mailing/" prefix and "/golden_horse_armor").
     * In 1.21.11 recipe ids are ResourceKeys and that lookup silently found nothing until it was fixed in phase 2;
     * this checks the recipe under that exact id is loaded and is a mailing recipe.
     */
    private static DataResult<Boolean> equineAssuranceRecipe(ServerPlayer player) {
        Identifier id = Envelope.resource("mailing/equine_assurance_bureau/golden_horse_armor");
        return player.level().getServer().getRecipeManager()
              .byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, id))
              .map(holder -> holder.value().getType() == Envelope.RecipeTypes.MAILING.get()
                    ? DataResult.success(true)
                    : DataResult.<Boolean>error(() -> id + " is loaded but is not a mailing recipe: " + holder.value().getType()))
              .orElseGet(() -> DataResult.error(() -> "recipe " + id + " is not loaded"));
    }

    // -- Predicate

    private static DataResult<Boolean> occludingPredicate(ServerPlayer player) {
        ItemOccludingBlockPredicate predicate = ItemOccludingBlockPredicate.INSTANCE;
        List<String> problems = new ArrayList<>();
        if (!predicate.matches(new ItemStack(Items.STONE))) problems.add("stone (a full block) did not match");
        if (predicate.matches(new ItemStack(Items.GLASS))) problems.add("glass (does not occlude) matched");
        if (predicate.matches(new ItemStack(Items.STICK))) problems.add("stick (not a block) matched");
        if (predicate.matches(DataComponentMap.EMPTY)) problems.add("a plain component map (not an item stack) matched");
        return problems.isEmpty() ? DataResult.success(true) : DataResult.error(() -> String.join("; ", problems));
    }

    // -- Mixin

    private static DataResult<Boolean> mailServiceHolder(ServerPlayer player) {
        ServerLevel overworld = player.level().getServer().overworld();
        if (!(overworld instanceof MailServiceHolder)) {
            return DataResult.error(() -> "ServerLevel does not implement MailServiceHolder: the injected ServerLevelMixin is not applied");
        }
        try {
            MailService service = MailService.of(overworld);
            return service != null && service.getLevel() == overworld
                  ? DataResult.success(true)
                  : DataResult.error(() -> "overworld mail service is missing or bound to another level");
        } catch (Exception e) {
            return DataResult.error(() -> "MailService.of(overworld) failed: " + e);
        }
    }
}
