package io.github.mortuusars.envelope.client.renderer.entity;

import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.client.model.CharredPigeonModel;
import io.github.mortuusars.envelope.client.renderer.entity.layer.CharredPigeonBackpackLayer;
import io.github.mortuusars.envelope.client.renderer.entity.layer.CharredPigeonFireLayer;
import io.github.mortuusars.envelope.client.renderer.entity.state.CharredPigeonRenderState;
import io.github.mortuusars.envelope.world.entity.CharredPigeon;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

/**
 * 1.21.11: renders from a {@link CharredPigeonRenderState}; babies use a separate baked model.
 */
public class CharredPigeonRenderer extends AgeableMobRenderer<CharredPigeon, CharredPigeonRenderState, CharredPigeonModel> {
    public static final ModelLayerLocation MODEL_LAYER = new ModelLayerLocation(Envelope.resource("charred_pigeon"), "main");
    public static final ModelLayerLocation BABY_MODEL_LAYER = new ModelLayerLocation(Envelope.resource("charred_pigeon_baby"), "main");

    public static final Identifier TEXTURE = Envelope.resource("textures/entity/charred_pigeon/charred_pigeon.png");

    public CharredPigeonRenderer(EntityRendererProvider.Context context) {
        super(context, new CharredPigeonModel(context.bakeLayer(MODEL_LAYER)),
              new CharredPigeonModel(context.bakeLayer(BABY_MODEL_LAYER)), 0.35f);
        addLayer(new CharredPigeonFireLayer(this));
        addLayer(new CharredPigeonBackpackLayer(this, context.getModelSet()));
    }

    @Override
    public @NotNull CharredPigeonRenderState createRenderState() {
        return new CharredPigeonRenderState();
    }

    @Override
    public void extractRenderState(CharredPigeon pigeon, CharredPigeonRenderState state, float partialTick) {
        super.extractRenderState(pigeon, state, partialTick);
        state.flying = pigeon.isFlying();
        // Upstream getBob(): passed to setupAnim as "bob" and used as the wing flap angle
        float flap = Mth.lerp(partialTick, pigeon.oFlap, pigeon.flap);
        float flapSpeed = Mth.lerp(partialTick, pigeon.oFlapSpeed, pigeon.flapSpeed);
        state.flapAngle = (Mth.sin(flap) + 1.0F) * flapSpeed;
        state.hasMail = pigeon.hasMail();
    }

    @Override
    public @NotNull Identifier getTextureLocation(CharredPigeonRenderState state) {
        return TEXTURE;
    }
}
