package io.github.mortuusars.envelope.client.renderer.entity.state;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * 1.21.11: render state for {@code CharredPigeonModel} and its layers.
 */
public class CharredPigeonRenderState extends LivingEntityRenderState {
    public boolean flying;
    /** Wing flap angle (upstream passed it to setupAnim as the "bob" argument). */
    public float flapAngle;
    public boolean hasMail;
}
