package io.github.mortuusars.envelope.client.renderer.item;

import com.mojang.serialization.MapCodec;
import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.world.item.SealedItem;
import io.github.mortuusars.envelope.world.item.component.seal.Seal;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 1.21.11: replaces the item color provider for sealed items (Fabric's ColorProviderRegistry.ITEM was removed in 1.21.4).
 * Used in {@code assets/envelope/items/*.json} as {@code {"type": "envelope:seal"}} on the seal overlay layer
 * (upstream tinted layer 1 only, other layers got -1).
 * Color of the seal material, animated for locked and golden seals; default red when the item has no seal.
 */
public record SealTintSource() implements ItemTintSource {
    public static final MapCodec<SealTintSource> MAP_CODEC = MapCodec.unit(new SealTintSource());

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity) {
        @Nullable Seal seal = stack.get(Envelope.DataComponents.SEAL);
        return seal != null ? SealedItem.Client.getSealOverlayColor(seal) : SealedItem.Client.OVERLAY_DEFAULT_RED_COLOR;
    }

    @Override
    public MapCodec<SealTintSource> type() {
        return MAP_CODEC;
    }
}
