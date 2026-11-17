package io.github.mortuusars.envelope.client.renderer.entity.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.mortuusars.envelope.client.model.PigeonModel;
import io.github.mortuusars.envelope.client.renderer.entity.state.PigeonRenderState;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/**
 * 1.21.11: second pass of the ghost pigeon. Upstream PigeonModel#renderToBuffer drew the ghost twice (a faint pass,
 * then this emissive green one into its own buffer); models can no longer do that, so the second pass is a layer.
 */
public class PigeonGhostGlowLayer extends RenderLayer<PigeonRenderState, PigeonModel> {
    private static final int GLOW_COLOR = 0x7F93FF93;

    public PigeonGhostGlowLayer(RenderLayerParent<PigeonRenderState, PigeonModel> renderer) {
        super(renderer);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, PigeonRenderState state,
                       float yRot, float xRot) {
        if (!state.ghost || state.isInvisible) {
            return;
        }

        submitNodeCollector.submitModel(getParentModel(), state, poseStack, RenderTypes.entityTranslucentEmissive(state.texture),
              LightTexture.FULL_BRIGHT, LivingEntityRenderer.getOverlayCoords(state, 0.0F), GLOW_COLOR, null, state.outlineColor, null);
    }
}
