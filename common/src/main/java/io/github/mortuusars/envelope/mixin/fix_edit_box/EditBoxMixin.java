package io.github.mortuusars.envelope.mixin.fix_edit_box;

import io.github.mortuusars.envelope.client.gui.screen.AddressTagScreen;
import io.github.mortuusars.envelope.lib.mortaar.client.Minecrft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(EditBox.class)
public abstract class EditBoxMixin extends AbstractWidget {
    public EditBoxMixin(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    // I hope no-one will try to do modify the same constant, because it will most likely crash.
    // But I don't see other easy ways to change the color.
    @ModifyConstant(method = "renderWidget", constant = @Constant(intValue = 0xFF808080))
    private int onRenderWidget(int constant) {
        if (Minecrft.get().screen instanceof AddressTagScreen addressTagScreen) {
            return addressTagScreen.getSuggestionTextColor();
        }
        return constant;
    }

    // Port: the scissor that limited the suggestion text to the box moved to AddressTagScreen (see there for why).
}