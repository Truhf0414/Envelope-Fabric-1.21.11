package io.github.mortuusars.envelope.client.renderer.entity.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.client.model.CharredPigeonModel;
import io.github.mortuusars.envelope.client.renderer.entity.state.CharredPigeonRenderState;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * Glowing fire overlay. Upstream drew the parent model with entityTranslucentEmissive and full light;
 * its unused RenderType.eyes constant was dropped (RenderType factories moved to RenderTypes in 1.21.11).
 */
public class CharredPigeonFireLayer extends RenderLayer<CharredPigeonRenderState, CharredPigeonModel> {
    public static final Identifier TEXTURE = Envelope.resource("textures/entity/charred_pigeon/charred_pigeon_fire.png");

    public CharredPigeonFireLayer(RenderLayerParent<CharredPigeonRenderState, CharredPigeonModel> renderer) {
        super(renderer);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, CharredPigeonRenderState state,
                       float yRot, float xRot) {
        submitNodeCollector.order(1).submitModel(getParentModel(), state, poseStack, RenderTypes.entityTranslucentEmissive(TEXTURE),
              LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor, null);
    }
}
