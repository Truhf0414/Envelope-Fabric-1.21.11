package io.github.mortuusars.envelope.world.block;

import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.world.item.PackageItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class PackageBlockEntity extends BlockEntity {
    protected ItemStack item = ItemStack.EMPTY;
    protected boolean unpackWhenBroken = true;
    private boolean removalHandled = false;

    protected PackageBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    public PackageBlockEntity(BlockPos pos, BlockState blockState) {
        super(Envelope.BlockEntityTypes.PACKAGE.get(), pos, blockState);
    }

    // --

    public ItemStack getPackage() {
        return !item.isEmpty() ? item : new ItemStack(getBlockState().getBlock().asItem());
    }

    public void setPackage(ItemStack item) {
        this.item = item;
        setChanged();
    }

    public boolean unpackWhenBroken() {
        return unpackWhenBroken;
    }

    public void setUnpackWhenBroken(boolean unpackWhenBroken) {
        this.unpackWhenBroken = unpackWhenBroken;
        setChanged();
    }

    // 1.21.11: moved here from PackageBlock#onRemove (removed). Called on the server when the block changed.
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        removalHandled = true;
        if (level == null) return;
        if (unpackWhenBroken()) {
            level.playSound(null, pos, Envelope.SoundEvents.PAPER_TEAR.get(), SoundSource.BLOCKS, 0.8f, 1);
        }
        dropContents(level, pos);
    }

    // 1.21.11: commands that replace blocks skip preRemoveSideEffects; 1.21.1's onRemove dropped the package then too.
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

    public void dropContents(Level level, BlockPos pos) {
        ItemStack stack = getPackage();
        if (unpackWhenBroken() && stack.getItem() instanceof PackageItem packageItem) {
            packageItem.destroy(stack, level, Vec3.atCenterOf(pos), null);
        } else {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
        }
        setChanged();
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

    // --

    // 1.21.11: block entity save/load goes through ValueOutput/ValueInput; ItemStack#save/parse became ItemStack.CODEC.
    @Override
    protected void saveAdditional(ValueOutput output) {
        if (!item.isEmpty()) {
            output.store("Package", ItemStack.CODEC, item);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        input.read("Package", ItemStack.CODEC).ifPresent(item -> this.item = item);
    }
}
