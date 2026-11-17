package io.github.mortuusars.envelope;

import io.github.mortuusars.envelope.client.gui.tooltip.*;
import io.github.mortuusars.envelope.client.renderer.SealRenderer;
import io.github.mortuusars.envelope.world.inventory.tooltip.SealDieTooltip;
import io.github.mortuusars.envelope.world.item.component.*;
import io.github.mortuusars.envelope.world.item.component.seal.Seal;
import io.github.mortuusars.envelope.world.item.tooltip.MailAddressTagTooltip;
import io.github.mortuusars.envelope.lib.mortaar.client.gui.tooltip.Tooltips;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

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

    public static class ItemModelOverrides {
        public static final Identifier OPEN = Envelope.resource("open");
        public static final Identifier HAS_CONTENT = Envelope.resource("has_content");

        public static final Identifier PAYBACK_TAG_DURATION = Envelope.resource("payback_tag_duration");

        public static void register() {
            ItemProperties.register(Envelope.Items.LETTER_AND_QUILL.get(), HAS_CONTENT, ItemModelOverrides::hasLetterContent);

            ItemProperties.register(Envelope.Items.LETTER.get(), OPEN, ItemModelOverrides::isOpen);
            ItemProperties.register(Envelope.Items.LETTER.get(), HAS_CONTENT, ItemModelOverrides::hasLetterContent);
            ItemProperties.register(Envelope.Items.TATTERED_LETTER.get(), OPEN, ItemModelOverrides::isOpen);
            ItemProperties.register(Envelope.Items.TATTERED_LETTER.get(), HAS_CONTENT, ItemModelOverrides::hasLetterContent);
            ItemProperties.register(Envelope.Items.SERVICE_LETTER.get(), OPEN, ItemModelOverrides::isOpen);
            ItemProperties.register(Envelope.Items.SERVICE_LETTER.get(), HAS_CONTENT, ItemModelOverrides::hasLetterContent);

            ItemProperties.register(Envelope.Items.PAPER_BOX.get(), OPEN, ItemModelOverrides::isOpen);
            ItemProperties.register(Envelope.Items.PACKAGE.get(), OPEN, ItemModelOverrides::isOpen);
            ItemProperties.register(Envelope.Items.PAYBACK_BOX.get(), OPEN, ItemModelOverrides::isOpen);
            ItemProperties.register(Envelope.Items.PAYBACK_PACKAGE.get(), OPEN, ItemModelOverrides::isOpen);

            ItemProperties.register(Envelope.Items.PAYBACK_TAG.get(), PAYBACK_TAG_DURATION, ItemModelOverrides::getPaybackDuration);
        }

        public static float isOpen(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
            return stack.has(Envelope.DataComponents.OPEN) ? 1 : 0;
        }

        public static float hasLetterContent(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
            if (!stack.getOrDefault(Envelope.DataComponents.LETTER_AND_QUILL_CONTENT, LetterAndQuillContent.EMPTY).isEmpty()) {
                return 1;
            }
            if (!stack.getOrDefault(Envelope.DataComponents.LETTER_CONTENT, LetterContent.EMPTY).isEmpty()) {
                return 1;
            }
            return 0;
        }

        public static float getPaybackDuration(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
            PaybackDuration duration = stack.get(Envelope.DataComponents.PAYBACK_TAG_CONTENTS) instanceof PaybackRequest request
                  ? request.duration()
                  : PaybackDuration.MEDIUM;
            return duration.ordinal() / 10f; // 0.0, 0.1, 0.2, etc.
        }
    }
}
