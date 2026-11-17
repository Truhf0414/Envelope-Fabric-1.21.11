package io.github.mortuusars.envelope.lib.mortaar.client.gui.tooltip.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;

import java.util.List;

public class CompositeTooltipComponent implements ClientTooltipComponent {
    private final List<ClientTooltipComponent> components;

    public CompositeTooltipComponent(List<ClientTooltipComponent> components) {
        this.components = components;
    }

    @Override
    public int getWidth(Font font) {
        int width = 0;
        for (ClientTooltipComponent component : components) {
            width = Math.max(component.getWidth(font), width);
        }
        return width;
    }

    @Override
    public int getHeight(Font font) { // 1.21.11: takes Font
        int height = 0;
        for (ClientTooltipComponent component : components) {
            height += component.getHeight(font);
        }
        return height;
    }

    @Override
    public void renderImage(Font font, int x, int y, int width, int height, GuiGraphics guiGraphics) { // 1.21.11: width/height added
        for (ClientTooltipComponent component : components) {
            int componentHeight = component.getHeight(font);
            component.renderImage(font, x, y, width, componentHeight, guiGraphics);
            y += componentHeight;
        }
    }

    @Override
    public void renderText(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) { // 1.21.11: draws through GuiGraphics
        for (ClientTooltipComponent component : components) {
            component.renderText(guiGraphics, font, mouseX, mouseY);
            mouseY += component.getHeight(font);
        }
    }
}
