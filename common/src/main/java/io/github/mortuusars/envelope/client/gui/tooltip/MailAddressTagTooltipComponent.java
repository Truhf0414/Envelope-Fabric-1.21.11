package io.github.mortuusars.envelope.client.gui.tooltip;

import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.util.Colors;
import io.github.mortuusars.envelope.world.mail.address.Address;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import net.minecraft.client.Minecraft;

public class MailAddressTagTooltipComponent implements ClientTooltipComponent {
    private static final ItemStack ADDRESS_TAG = new ItemStack(Envelope.Items.ADDRESS_TAG.get());

    private final @NotNull Component component;

    public MailAddressTagTooltipComponent(Address address) {
        component = address.format().withIcon()
              .withIconColor(Colors.ADDRESS_NEUTRAL)
              .withColor(Colors.ADDRESS_NEUTRAL).toComponent();
    }

    @Override
    public int getWidth(Font font) {
//        if (Minecraft.getInstance().hasShiftDown()) {
//            return Math.max(19, Math.max(font.width(fullSenderComponent), font.width(fullComponent)));
//        }
        return 19 + font.width(component);
    }

    @Override
    public int getHeight(Font font) { // 1.21.11: takes the font
//        if (Minecraft.getInstance().hasShiftDown()) {
//            return 16 + (!isEmpty(fullSenderComponent) ? 10 : 0) + (!isEmpty(fullComponent) ? 10 : 0);
//        }
        return 16;
    }

    @Override
    public void renderImage(Font font, int x, int y, int width, int height, GuiGraphics guiGraphics) { // 1.21.11: also gets the tooltip size
        guiGraphics.renderFakeItem(ADDRESS_TAG, x - 1, y - 1, 0);
    }

    @Override
    public void renderText(GuiGraphics guiGraphics, Font font, int x, int y) { // 1.21.11: drawn through GuiGraphics
//        if (Minecraft.getInstance().hasShiftDown()) {
//            y += 16;
//
//            if (!isEmpty(fullSenderComponent)) {
//                drawText(font, fullSenderComponent, x, y, guiGraphics);
//                y += 10;
//            }
//
//            if (!isEmpty(fullComponent)) {
//                drawText(font, fullComponent, x, y, guiGraphics);
//            }
//        } else {
//        }
            drawText(font, component, x + 18, y + 3, guiGraphics);
    }

    private void drawText(Font font, Component component, int x, int y, GuiGraphics guiGraphics) {
        // 1.21.11: upstream passed color 0, which Font used to treat as opaque black; alpha 0 is now skipped entirely
        guiGraphics.drawString(font, component, x, y, 0xFF000000, true);
    }

    private boolean isEmpty(Component component) {
        return component.equals(CommonComponents.EMPTY);
    }
}
