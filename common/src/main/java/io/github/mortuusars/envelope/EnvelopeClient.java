package io.github.mortuusars.envelope;

import io.github.mortuusars.envelope.client.gui.tooltip.*;
import io.github.mortuusars.envelope.client.renderer.SealRenderer;
import io.github.mortuusars.envelope.world.inventory.tooltip.SealDieTooltip;
import io.github.mortuusars.envelope.world.item.component.*;
import io.github.mortuusars.envelope.world.item.component.seal.Seal;
import io.github.mortuusars.envelope.world.item.tooltip.MailAddressTagTooltip;
import io.github.mortuusars.envelope.lib.mortaar.client.gui.tooltip.Tooltips;
import io.github.mortuusars.envelope.client.renderer.item.HasLetterContentProperty;
import io.github.mortuusars.envelope.client.renderer.item.PaybackDurationProperty;
import io.github.mortuusars.envelope.client.renderer.item.SealTintSource;
import net.minecraft.client.color.item.ItemTintSources;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperties;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperties;
import net.minecraft.resources.Identifier;

public class EnvelopeClient {
    private static final SealRenderer sealRenderer = new SealRenderer();

    public static void init() {
        ItemModelOverrides.register();

        Tooltips.register(MailAddressTagTooltip.class, mailAddress -> new MailAddressTagTooltipComponent(mailAddress.address()));
        Tooltips.register(PackageContents.class, PackageTooltipComponent::new);
        Tooltips.register(PaybackRequest.class, PaybackRequestTooltipComponent::new);
        Tooltips.register(Seal.class, SealTooltipComponent::new);
        Tooltips.register(SealDieTooltip.class, sealDie -> new SealDieTooltipComponent(sealDie.die()));
        // 1.21.11 (port decision D-a): Mortaar's Bugger debug screen and entity overhead display were removed
    }

    public static SealRenderer getSealRenderer() {
        return sealRenderer;
    }

    // --

    /**
     * 1.21.11: item model properties and tints are no longer registered per item (ItemProperties and item color
     * providers were removed in 1.21.4). They are registered once by id, and each item's
     * {@code assets/envelope/items/*.json} definition picks them up:
     * <ul>
     *     <li>{@code envelope:has_content} (condition) – letter has written content</li>
     *     <li>{@code envelope:payback_tag_duration} (range_dispatch) – payback tag duration</li>
     *     <li>{@code envelope:seal} (tint) – seal overlay color</li>
     * </ul>
     * Upstream's {@code envelope:open} property only checked the {@code envelope:open} component, so the item
     * definitions use vanilla's {@code minecraft:has_component} condition for it instead of a custom property.
     */
    public static class ItemModelOverrides {
        public static final Identifier HAS_CONTENT = Envelope.resource("has_content");
        public static final Identifier PAYBACK_TAG_DURATION = Envelope.resource("payback_tag_duration");
        public static final Identifier SEAL_TINT = Envelope.resource("seal");

        public static void register() {
            ConditionalItemModelProperties.ID_MAPPER.put(HAS_CONTENT, HasLetterContentProperty.MAP_CODEC);
            RangeSelectItemModelProperties.ID_MAPPER.put(PAYBACK_TAG_DURATION, PaybackDurationProperty.MAP_CODEC);
            ItemTintSources.ID_MAPPER.put(SEAL_TINT, SealTintSource.MAP_CODEC);
        }
    }
}
