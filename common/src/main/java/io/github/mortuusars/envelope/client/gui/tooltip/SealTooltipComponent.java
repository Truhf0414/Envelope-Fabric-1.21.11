package io.github.mortuusars.envelope.client.gui.tooltip;

import io.github.mortuusars.envelope.EnvelopeClient;
import io.github.mortuusars.envelope.world.item.component.seal.Seal;
import io.github.mortuusars.envelope.lib.mortaar.client.Minecrft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

public class SealTooltipComponent implements ClientTooltipComponent {
    protected final Seal seal;

    public SealTooltipComponent(Seal seal) {
        this.seal = seal;
    }

    @Override
    public int getWidth(Font font) {
        return 37 + font.width(seal.signature());
    }

    @Override
    public int getHeight(Font font) { // 1.21.11: takes the font
        return seal.lock()
              .map(lock -> lock.isLocked(Minecrft.level()) ? 35 : 34) // +1 pixel to make some room for lock tentacles
              .orElse(34);
    }

    @Override
    public void renderImage(Font font, int x, int y, int width, int height, GuiGraphics guiGraphics) { // 1.21.11: also gets the tooltip size
        EnvelopeClient.getSealRenderer().render(seal, guiGraphics, x, y);
    }

    @Override
    public void renderText(GuiGraphics guiGraphics, Font font, int x, int y) { // 1.21.11: drawn through GuiGraphics
        // Signature:
        x += 36;
        y += 12;
        int mainColor = ARGB.srgbLerp(0.25f, seal.material().value().impressionPalette().highlight().tint(), 0xFFFFFFFF);
        int outlineColor = ARGB.srgbLerp(0.25f, seal.material().value().impressionPalette().side().tint(), 0xFF7F7F7F);
        int shadowColor = ARGB.srgbLerp(0.25f, seal.material().value().impressionPalette().shadow().tint(), 0xFF333333);
        text(seal.signature(), font, x - 1, y, outlineColor, guiGraphics);
        text(seal.signature(), font, x - 1, y - 1, outlineColor, guiGraphics);
        text(seal.signature(), font, x, y - 1, outlineColor, guiGraphics);
        text(seal.signature(), font, x + 1, y - 1, outlineColor, guiGraphics);
        text(seal.signature(), font, x + 1, y, outlineColor, guiGraphics);
        text(seal.signature(), font, x, y + 1, outlineColor, guiGraphics);
        text(seal.signature(), font, x - 1, y + 1, outlineColor, guiGraphics);

        text(seal.signature(), font, x + 1, y + 1, shadowColor, guiGraphics);

        text(seal.signature(), font, x, y, mainColor, guiGraphics);
    }

    private void text(Component text, Font font, int x, int y, int color, GuiGraphics guiGraphics) {
        guiGraphics.drawString(font, text, x, y, color, false);
    }
}
