package io.github.mortuusars.envelope.client.renderer.item;

import com.mojang.serialization.MapCodec;
import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.world.item.component.PaybackDuration;
import io.github.mortuusars.envelope.world.item.component.PaybackRequest;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 1.21.11: replaces the {@code envelope:payback_tag_duration} item property (ItemProperties was removed in 1.21.4).
 * Used in {@code assets/envelope/items/payback_tag.json} as a {@code range_dispatch} property.
 * Same values as upstream: duration ordinal / 10 (0.0, 0.1, 0.2, ...), MEDIUM when the tag has no request.
 */
public record PaybackDurationProperty() implements RangeSelectItemModelProperty {
    public static final MapCodec<PaybackDurationProperty> MAP_CODEC = MapCodec.unit(new PaybackDurationProperty());

    @Override
    public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        PaybackDuration duration = stack.get(Envelope.DataComponents.PAYBACK_TAG_CONTENTS) instanceof PaybackRequest request
              ? request.duration()
              : PaybackDuration.MEDIUM;
        return duration.ordinal() / 10f;
    }

    @Override
    public MapCodec<PaybackDurationProperty> type() {
        return MAP_CODEC;
    }
}
