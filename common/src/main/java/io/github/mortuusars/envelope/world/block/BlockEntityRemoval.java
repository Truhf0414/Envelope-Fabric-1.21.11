package io.github.mortuusars.envelope.world.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

/**
 * 1.21.11: Block#onRemove was split, and the block entity part moved to BlockEntity#preRemoveSideEffects.
 * Commands that replace blocks (/setblock, /fill, /clone in non-destroy modes) pass a flag that skips that hook,
 * while 1.21.1's onRemove ran for every removal. Block entities that must react to those removals too
 * check this from setRemoved.
 */
public final class BlockEntityRemoval {
    private BlockEntityRemoval() {
    }

    /**
     * True when the block entity is being removed because the block at its position is no longer its block.
     * False when only the block entity goes away while the block stays (chunk unload, or a relocation mod such as
     * CarryOn taking the block entity first). Reads the chunk only if it is already loaded, so it never loads a chunk
     * that is in the middle of unloading.
     */
    public static boolean wasBlockReplaced(BlockEntity blockEntity) {
        if (!(blockEntity.getLevel() instanceof ServerLevel serverLevel)) {
            return false;
        }
        BlockPos pos = blockEntity.getBlockPos();
        @Nullable LevelChunk chunk = serverLevel.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk != null && !chunk.getBlockState(pos).is(blockEntity.getBlockState().getBlock());
    }
}
