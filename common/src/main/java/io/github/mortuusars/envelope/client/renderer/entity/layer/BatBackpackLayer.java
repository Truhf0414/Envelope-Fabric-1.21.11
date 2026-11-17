package io.github.mortuusars.envelope.client.renderer.entity.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.client.model.BatBackpackModel;
import io.github.mortuusars.envelope.client.model.CourierBatModel;
import io.github.mortuusars.envelope.client.renderer.entity.state.CourierBatRenderState;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Unit;

public class BatBackpackLayer extends RenderLayer<CourierBatRenderState, CourierBatModel> {
    public static final ModelLayerLocation MODEL_LAYER = new ModelLayerLocation(Envelope.resource("bat_backpack"), "main");

    protected final BatBackpackModel model;

    public BatBackpackLayer(RenderLayerParent<CourierBatRenderState, CourierBatModel> renderer, ModelPart root) {
        super(renderer);
        model = new BatBackpackModel(root);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, CourierBatRenderState state,
                       float yRot, float xRot) {
        if (!state.hasMail) {
            return;
        }

        poseStack.pushPose();

        // The parent model is already posed for this state (LivingEntityRenderer runs setupAnim before the layers)
        CourierBatModel batModel = getParentModel();
        ModelPart body = batModel.root().getChild("body");
        body.translateAndRotate(poseStack);

        submitNodeCollector.submitModel(model, Unit.INSTANCE, poseStack, RenderTypes.entityCutoutNoCull(BatBackpackModel.TEXTURE),
              packedLight, OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor, null);

        poseStack.popPose();
    }
}
