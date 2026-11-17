package io.github.mortuusars.envelope.client.renderer.entity.state;

import io.github.mortuusars.envelope.client.model.PigeonModel;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/**
 * 1.21.11: entity models are animated from a render state captured each frame instead of from the entity itself.
 * Holds what {@link PigeonModel} and the pigeon layers used to read from {@code Pigeon} directly.
 */
public class PigeonRenderState extends LivingEntityRenderState {
    public Identifier texture;
    public boolean ghost;
    public PigeonModel.State pose = PigeonModel.State.STANDING;
    /** Wing flap angle (upstream passed it to setupAnim as the "bob" argument). */
    public float flapAngle;
    /** Eating head-bob progress, 0 when not eating. Upstream computed it from eating ticks and partial tick. */
    public float eatingProgress;
    /** Easter egg: pigeons named "drumstick" or "matisslee" are chunkier. */
    public boolean chunky;
    public boolean hasMail;
    public boolean hasMailmanHat;
    /** Light for the backpack and hat layers. Ghosts draw their body full bright, but upstream lit the layers normally. */
    public int layerLight;
}
