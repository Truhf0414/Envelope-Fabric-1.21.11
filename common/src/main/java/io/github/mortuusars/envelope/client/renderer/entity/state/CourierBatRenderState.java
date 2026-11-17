package io.github.mortuusars.envelope.client.renderer.entity.state;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.AnimationState;

/**
 * 1.21.11: render state for {@code CourierBatModel} and {@code BatBackpackLayer}.
 */
public class CourierBatRenderState extends LivingEntityRenderState {
    public final AnimationState flyAnimationState = new AnimationState();
    public boolean hasMail;
}
