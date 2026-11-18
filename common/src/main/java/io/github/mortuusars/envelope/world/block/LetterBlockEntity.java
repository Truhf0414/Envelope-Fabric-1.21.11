package io.github.mortuusars.envelope.world.block;

import com.mojang.logging.LogUtils;
import io.github.mortuusars.envelope.Envelope;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SeededContainerLoot;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import net.minecraft.world.Containers;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class LetterBlockEntity extends BlockEntity implements Nameable {
    private static final Logger LOGGER = LogUtils.getLogger();

    protected @Nullable SeededContainerLoot loot = null;
    protected @NotNull ItemStack letter = ItemStack.EMPTY;
    private boolean removalHandled = false;

    public LetterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public LetterBlockEntity(BlockPos pos, BlockState state) {
        super(Envelope.BlockEntityTypes.LETTER.get(), pos, state);
    }

    public ItemStack getLetter(@Nullable Player player) {
        unpackLootTableIfPresent(player);
        return !letter.isEmpty() ? letter : new ItemStack(Envelope.Items.LETTER.get());
    }

    // 1.21.11: moved here from LetterBlock#onRemove (removed). Called on the server when the block changed.
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        removalHandled = true;
        if (level == null) return;
        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), getLetter(null));
        setLetter(ItemStack.EMPTY);
    }

    // 1.21.11: commands that replace blocks skip preRemoveSideEffects; 1.21.1's onRemove dropped the letter then too.
    @Override
    public void setRemoved() {
        super.setRemoved();
        if (!removalHandled && BlockEntityRemoval.wasBlockReplaced(this)) {
            preRemoveSideEffects(getBlockPos(), getBlockState());
        }
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        removalHandled = false;
    }

    public void setLetter(ItemStack letter) {
        this.letter = letter;
        this.loot = null;
        setChanged();
    }

    protected void unpackLootTableIfPresent(@Nullable Player player) {
        if (loot != null && level instanceof ServerLevel serverLevel) {
            if (!letter.isEmpty()) {
                Envelope.LOGGER.warn("Unpacking loot-table of the Letter block, that already has letter defined. " +
                      "Existing letter will be overriden.");
            }

            LootParams.Builder params = new LootParams.Builder(serverLevel)
                  .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(getBlockPos()));

            if (player != null) {
                params.withLuck(player.getLuck()).withParameter(LootContextParams.THIS_ENTITY, player);
            }

            if (player instanceof ServerPlayer serverPlayer) {
                CriteriaTriggers.GENERATE_LOOT.trigger(serverPlayer, loot.lootTable());
            }

            serverLevel.getServer().reloadableRegistries().getLootTable(loot.lootTable())
                  .getRandomItems(params.create(LootContextParamSets.CHEST), loot.seed())
                  .stream()
                  .findAny()
                  .ifPresent(this::setLetter);
        }
    }

    // --

    @Override
    public @NotNull Component getName() {
        return letter.getHoverName();
    }

    @Override
    public boolean hasCustomName() {
        return Nameable.super.hasCustomName() || letter.has(DataComponents.ITEM_NAME);
    }

    @Override
    public @Nullable Component getCustomName() {
        return letter.get(DataComponents.CUSTOM_NAME);
    }

    // -- Sync

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    // -- Save/Load

    // 1.21.11: block entity save/load goes through ValueOutput/ValueInput; ItemStack#save/parse became ItemStack.CODEC.
    @Override
    protected void saveAdditional(ValueOutput output) {
        output.storeNullable("Loot", SeededContainerLoot.CODEC, loot);
        if (!letter.isEmpty()) {
            output.store("Letter", ItemStack.CODEC, letter);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        input.read("Loot", SeededContainerLoot.CODEC).ifPresent(loot -> this.loot = loot);
        input.read("Letter", ItemStack.CODEC).ifPresent(letter -> this.letter = letter);
    }
}
