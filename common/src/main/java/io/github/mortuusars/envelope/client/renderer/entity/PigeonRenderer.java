package io.github.mortuusars.envelope.client.renderer.entity;

import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.client.model.PigeonModel;
import io.github.mortuusars.envelope.client.renderer.entity.layer.PigeonBackpackLayer;
import io.github.mortuusars.envelope.client.renderer.entity.layer.PigeonGhostGlowLayer;
import io.github.mortuusars.envelope.client.renderer.entity.layer.PigeonHatLayer;
import io.github.mortuusars.envelope.client.renderer.entity.state.PigeonRenderState;
import io.github.mortuusars.envelope.world.entity.Pigeon;
import io.github.mortuusars.envelope.world.entity.PigeonVariant;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.21.11: renderers extract a {@link PigeonRenderState} from the entity, and the model and layers only see that state.
 * Babies use a separate baked model (AgeableMobRenderer swaps them), like vanilla's chicken.
 */
public class PigeonRenderer extends AgeableMobRenderer<Pigeon, PigeonRenderState, PigeonModel> {
    public static final ModelLayerLocation MODEL_LAYER = new ModelLayerLocation(Envelope.resource("pigeon"), "main");
    public static final ModelLayerLocation BABY_MODEL_LAYER = new ModelLayerLocation(Envelope.resource("pigeon_baby"), "main");

    /** Ghost body color: upstream drew the ghost at this alpha first, then an emissive pass (see {@link PigeonGhostGlowLayer}). */
    private static final int GHOST_BODY_COLOR = 0x22FFFFFF;

    public PigeonRenderer(EntityRendererProvider.Context context) {
        super(context, new PigeonModel(context.bakeLayer(MODEL_LAYER)), new PigeonModel(context.bakeLayer(BABY_MODEL_LAYER)), 0.35f);
        addLayer(new PigeonGhostGlowLayer(this));
        addLayer(new PigeonBackpackLayer(this, context.getModelSet()));
        addLayer(new PigeonHatLayer(this, context.getModelSet()));
    }

    @Override
    public @NotNull PigeonRenderState createRenderState() {
        return new PigeonRenderState();
    }

    @Override
    public void extractRenderState(Pigeon pigeon, PigeonRenderState state, float partialTick) {
        super.extractRenderState(pigeon, state, partialTick);
        state.texture = pigeon.getVariant().value().texture();
        state.ghost = pigeon.getVariant().is(PigeonVariant.GHOST);
        state.pose = PigeonModel.getState(pigeon);
        // Upstream getBob(): passed to setupAnim as "bob" and used as the wing flap angle
        float flap = Mth.lerp(partialTick, pigeon.oFlap, pigeon.flap);
        float flapSpeed = Mth.lerp(partialTick, pigeon.oFlapSpeed, pigeon.flapSpeed);
        state.flapAngle = (Mth.sin(flap) + 1.0F) * flapSpeed;
        state.eatingProgress = PigeonModel.getEatingProgress(pigeon, partialTick);
        state.chunky = PigeonModel.isChunky(pigeon);
        state.hasMail = pigeon.hasMail();
        state.hasMailmanHat = pigeon.hasMailmanHat();

        state.layerLight = state.lightCoords;
        if (state.ghost) {
            // Upstream drew the ghost body full bright
            state.lightCoords = LightTexture.FULL_BRIGHT;
        }
    }

    @Override
    public @NotNull Identifier getTextureLocation(PigeonRenderState state) {
        return state.texture;
    }

    @Override
    protected @Nullable RenderType getRenderType(PigeonRenderState state, boolean bodyVisible, boolean translucent, boolean glowing) {
        if (state.ghost) {
            // 1.21.11: RenderType.entityTranslucentCull was removed; itemEntityTranslucentCull is what vanilla now uses
            // for translucent (invisible-to-player) living entities
            return RenderTypes.itemEntityTranslucentCull(getTextureLocation(state));
        }

        return super.getRenderType(state, bodyVisible, translucent, glowing);
    }

    @Override
    protected int getModelTint(PigeonRenderState state) {
        return state.ghost ? GHOST_BODY_COLOR : super.getModelTint(state);
    }
}
