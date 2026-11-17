package io.github.mortuusars.envelope.lib.mortaar.mixin.bugger;

import io.github.mortuusars.envelope.lib.mortaar.bugger.screen.BuggerScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugScreenOverlay.class)
public class ScreenOverlayMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRender(GuiGraphics guiGraphics, CallbackInfo ci) {
        if (BuggerScreen.render(guiGraphics)) {
            ci.cancel();
        }
    }
}
