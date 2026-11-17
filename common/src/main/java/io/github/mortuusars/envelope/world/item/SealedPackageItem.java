package io.github.mortuusars.envelope.world.item;

import io.github.mortuusars.envelope.Envelope;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class SealedPackageItem extends PackageItem implements SealedItem {
    public SealedPackageItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public @NotNull InteractionResult place(BlockPlaceContext context) {
        return InteractionResult.PASS;
    }

    @Override
    public ItemLike getUnsealedItem() {
        return Envelope.Items.PACKAGE.get();
    }

    @Override
    public @NotNull Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        return Optional.ofNullable(stack.get(Envelope.DataComponents.SEAL));
    }

    @Override
    public @NotNull ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.EAT;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        // 1.21.11: Item#getEatingSound was removed; use effects are emitted here instead.
        emitUnsealingEffects(entity, stack, remainingUseDuration);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return getUnsealingDuration(stack, entity);
    }

    @Override
    public @NotNull InteractionResult use(Level level, Player player, InteractionHand hand) {
        return useSealedItem(level, player, hand);
    }

    @Override
    public @NotNull ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        return unsealByUsing(stack, level, entity);
    }
}
