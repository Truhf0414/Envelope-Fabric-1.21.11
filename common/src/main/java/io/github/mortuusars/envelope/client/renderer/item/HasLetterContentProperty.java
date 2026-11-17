package io.github.mortuusars.envelope.client.renderer.item;

import com.mojang.serialization.MapCodec;
import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.world.item.component.LetterAndQuillContent;
import io.github.mortuusars.envelope.world.item.component.LetterContent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 1.21.11: replaces the {@code envelope:has_content} item property (ItemProperties was removed in 1.21.4).
 * Used in {@code assets/envelope/items/*.json} as {@code "property": "envelope:has_content"}.
 * True when the letter (or letter and quill) has any written content.
 */
public record HasLetterContentProperty() implements ConditionalItemModelProperty {
    public static final MapCodec<HasLetterContentProperty> MAP_CODEC = MapCodec.unit(new HasLetterContentProperty());

    @Override
    public boolean get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed,
                       ItemDisplayContext displayContext) {
        return !stack.getOrDefault(Envelope.DataComponents.LETTER_AND_QUILL_CONTENT, LetterAndQuillContent.EMPTY).isEmpty()
              || !stack.getOrDefault(Envelope.DataComponents.LETTER_CONTENT, LetterContent.EMPTY).isEmpty();
    }

    @Override
    public MapCodec<HasLetterContentProperty> type() {
        return MAP_CODEC;
    }
}
