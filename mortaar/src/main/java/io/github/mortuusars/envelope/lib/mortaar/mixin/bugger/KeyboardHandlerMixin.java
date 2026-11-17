package io.github.mortuusars.envelope.lib.mortaar.mixin.bugger;

import io.github.mortuusars.envelope.lib.mortaar.bugger.screen.BuggerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.client.KeyboardHandler.class)
public class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At(value = "RETURN"), cancellable = true)
    private void keyPress(long windowPointer, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        if (BuggerScreen.onKeyAction(action, key, scanCode, modifiers)) {
            ci.cancel();
        }
    }
}
