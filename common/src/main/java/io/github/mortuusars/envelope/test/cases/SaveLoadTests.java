package io.github.mortuusars.envelope.test.cases;

import com.mojang.serialization.DataResult;
import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.lib.mortaar.bugger.test.BuggerTests;
import io.github.mortuusars.envelope.lib.mortaar.util.GameTimeId;
import io.github.mortuusars.envelope.world.KnownPlayers;
import io.github.mortuusars.envelope.world.block.mailbox.InboxStorage;
import io.github.mortuusars.envelope.world.block.mailbox.MailboxesSavedData;
import io.github.mortuusars.envelope.world.block.mailbox.UnloadedInbox;
import io.github.mortuusars.envelope.world.entity.CharredPigeon;
import io.github.mortuusars.envelope.world.entity.Pigeon;
import io.github.mortuusars.envelope.world.item.component.SealLock;
import io.github.mortuusars.envelope.world.level.saveddata.SealLocks;
import io.github.mortuusars.envelope.world.mail.CustomMailSavedData;
import io.github.mortuusars.envelope.world.mail.MailService;
import io.github.mortuusars.envelope.world.mail.address.type.BlockAddress;
import io.github.mortuusars.envelope.world.mail.dropoff.MailDropOffHandlerSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Port tests (1.21.11, phase 2 D7): saved data, block entities and entities must read back exactly what was written.
 * <p>
 * Saved data goes through a real {@link DimensionDataStorage} (write file, new storage, read file) in a scratch folder,
 * so the DataFixTypes == null path (handled by Fabric API) is exercised too. Every case first checks that the test data
 * actually encodes to something non-empty, otherwise a round trip of nothing would pass.
 * <p>
 * Not covered here (needs real gameplay data): PaybackDepartmentData, BackgroundDelivery and CustomMailSavedData typed data.
 * Those are checked by the real server restart step in phase 3b.
 */
public class SaveLoadTests extends BuggerTests {
    public SaveLoadTests() {
        add("SaveLoad_KnownPlayers", player -> {
            KnownPlayers data = new KnownPlayers();
            data.add(player);
            data.setDefaultAddress(player, new BlockAddress("envelope_test_default"));
            return roundTrip(player, KnownPlayers.type("envelope_players_port_test"), data);
        });

        add("SaveLoad_MailDropOffHandlerSavedData", player -> {
            CompoundTag tag = new CompoundTag();
            tag.putString("test", "value");
            MailDropOffHandlerSavedData data = new MailDropOffHandlerSavedData();
            data.set(Envelope.resource("test_handler"), tag);
            return roundTrip(player, MailDropOffHandlerSavedData.TYPE, data);
        });

        add("SaveLoad_CustomMailSavedData", player -> {
            CompoundTag tag = new CompoundTag();
            tag.putInt("test", 42);
            CustomMailSavedData data = new CustomMailSavedData();
            data.set(Envelope.resource("test_data"), tag);
            return roundTrip(player, CustomMailSavedData.TYPE, data);
        });

        add("SaveLoad_InboxStorage", player -> {
            UUID id = UUID.randomUUID();
            InboxStorage data = new InboxStorage(Map.of(id, new UnloadedInbox(id, new BlockAddress("envelope_test_inbox"), 512,
                  new java.util.ArrayList<>(List.of(new ItemStack(Items.PAPER, 3), new ItemStack(Envelope.Items.LETTER.get()))))));
            return roundTrip(player, InboxStorage.TYPE, data);
        });

        add("SaveLoad_MailboxesSavedData", player -> withLiveData(player,
              level -> MailService.of(level).getMailboxes().correctOrRegisterIfNeeded(new BlockAddress("envelope_test_mailbox"), testPos(player)),
              level -> MailService.of(level).getMailboxes().remove(testPos(player)),
              level -> roundTrip(player, MailboxesSavedData.TYPE, MailboxesSavedData.get(level))));

        add("SaveLoad_SealLocks", player -> {
            SealLock lock = new SealLock(player.getScoreboardName(), GameTimeId.createChecked(player.level().getGameTime(), false));
            return withLiveData(player,
                  level -> SealLocks.get(level).lock(lock),
                  level -> SealLocks.get(level).unlock(lock),
                  level -> roundTrip(player, SealLocks.TYPE, SealLocks.get(level)));
        });

        add("SaveLoad_MailboxBlockEntity", player -> blockEntityRoundTrip(player, Envelope.Blocks.MAILBOX.get().defaultBlockState(),
              be -> ((io.github.mortuusars.envelope.world.block.mailbox.MailboxBlockEntity) be).setItem(0, new ItemStack(Items.WHEAT_SEEDS, 5))));
        add("SaveLoad_PackageBlockEntity", player -> blockEntityRoundTrip(player, Envelope.Blocks.PACKAGE.get().defaultBlockState(),
              be -> ((io.github.mortuusars.envelope.world.block.PackageBlockEntity) be).setPackage(new ItemStack(Envelope.Items.PACKAGE.get()))));
        add("SaveLoad_LetterBlockEntity", player -> blockEntityRoundTrip(player, Envelope.Blocks.LETTER.get().defaultBlockState(),
              be -> ((io.github.mortuusars.envelope.world.block.LetterBlockEntity) be).setLetter(new ItemStack(Envelope.Items.LETTER.get()))));

        add("SaveLoad_Pigeon", player -> entityRoundTrip(player, Envelope.EntityTypes.PIGEON.get(), e -> ((Pigeon) e).setSitting(true)));
        add("SaveLoad_CourierBat", player -> entityRoundTrip(player, Envelope.EntityTypes.COURIER_BAT.get(), e -> {}));
        add("SaveLoad_CharredPigeon", player -> entityRoundTrip(player, Envelope.EntityTypes.CHARRED_PIGEON.get(),
              e -> ((CharredPigeon) e).setCarriedMail(new ItemStack(Envelope.Items.LETTER.get()))));
    }

    // -- Saved data

    private static <T extends SavedData> DataResult<Boolean> roundTrip(ServerPlayer player, SavedDataType<T> type, T data) {
        MinecraftServer server = player.level().getServer();
        RegistryOps<Tag> ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        Path dir = server.getServerDirectory().resolve("envelope_port_tests").resolve(type.id());
        try {
            Tag before = type.codec().encodeStart(ops, data).getOrThrow();
            if (before instanceof CompoundTag compound && compound.isEmpty()) {
                return DataResult.error(() -> type + ": test data encodes to an empty tag, the round trip would prove nothing");
            }

            Files.createDirectories(dir);
            try (DimensionDataStorage write = new DimensionDataStorage(dir, server.getFixerUpper(), server.registryAccess())) {
                write.set(type, data);
                write.saveAndJoin();
            }

            @Nullable T loaded;
            try (DimensionDataStorage read = new DimensionDataStorage(dir, server.getFixerUpper(), server.registryAccess())) {
                loaded = read.get(type);
            }
            if (loaded == null) {
                return DataResult.error(() -> type + ": nothing was read back from " + dir);
            }

            Tag after = type.codec().encodeStart(ops, loaded).getOrThrow();
            return before.equals(after)
                  ? DataResult.success(true)
                  : DataResult.error(() -> type + ": read back differs.\nWritten: " + before + "\nRead:    " + after);
        } catch (Exception e) {
            return DataResult.error(() -> type + ": " + e);
        } finally {
            deleteQuietly(dir.resolve(type.id() + ".dat"));
        }
    }

    private static DataResult<Boolean> withLiveData(ServerPlayer player, Consumer<ServerLevel> setUp, Consumer<ServerLevel> tearDown,
                                                    java.util.function.Function<ServerLevel, DataResult<Boolean>> test) {
        ServerLevel level = player.level().getServer().overworld();
        setUp.accept(level);
        try {
            return test.apply(level);
        } finally {
            tearDown.accept(level);
        }
    }

    // -- Block entities and entities

    private static DataResult<Boolean> blockEntityRoundTrip(ServerPlayer player, BlockState state, Consumer<BlockEntity> fill) {
        ServerLevel level = player.level();
        BlockPos pos = testPos(player);
        BlockState previous = level.getBlockState(pos);
        try {
            level.setBlock(pos, state, 3);
            @Nullable BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity == null) {
                return DataResult.error(() -> state + ": no block entity was created");
            }
            fill.accept(blockEntity);
            CompoundTag before = blockEntity.saveWithFullMetadata(level.registryAccess());
            @Nullable BlockEntity loaded = BlockEntity.loadStatic(pos, state, before, level.registryAccess());
            if (loaded == null) {
                return DataResult.error(() -> state + ": block entity could not be loaded from " + before);
            }
            CompoundTag after = loaded.saveWithFullMetadata(level.registryAccess());
            return before.equals(after)
                  ? DataResult.success(true)
                  : DataResult.error(() -> state + ": read back differs.\nWritten: " + before + "\nRead:    " + after);
        } finally {
            // Clear contents first so removing the test block does not drop anything
            level.removeBlockEntity(pos);
            level.setBlock(pos, previous.isAir() ? Blocks.AIR.defaultBlockState() : previous, 3);
        }
    }

    private static DataResult<Boolean> entityRoundTrip(ServerPlayer player, EntityType<?> type, Consumer<Entity> fill) {
        ServerLevel level = player.level();
        @Nullable Entity entity = type.create(level, EntitySpawnReason.COMMAND);
        if (entity == null) {
            return DataResult.error(() -> type + ": entity could not be created");
        }
        try {
            entity.snapTo(player.getX(), player.getY(), player.getZ(), 0, 0);
            fill.accept(entity);
            CompoundTag before = save(entity);
            @Nullable Entity loaded = EntityType.loadEntityRecursive(before, level, EntitySpawnReason.LOAD, EntityProcessor.NOP);
            if (loaded == null) {
                return DataResult.error(() -> type + ": entity could not be loaded from " + before);
            }
            CompoundTag after = save(loaded);
            loaded.discard();
            return before.equals(after)
                  ? DataResult.success(true)
                  : DataResult.error(() -> type + ": read back differs.\nWritten: " + before + "\nRead:    " + after);
        } finally {
            entity.discard();
        }
    }

    private static CompoundTag save(Entity entity) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(entity.problemPath(), Envelope.LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, entity.registryAccess());
            entity.save(output);
            return output.buildResult();
        }
    }

    // --

    static BlockPos testPos(ServerPlayer player) {
        return player.blockPosition().above(3).relative(player.getDirection(), 3);
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
        }
    }
}
