package io.github.mortuusars.envelope.client.renderer.entity.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.client.model.PigeonModel;
import io.github.mortuusars.envelope.client.renderer.entity.state.PigeonRenderState;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.Identifier;

public class PigeonBackpackLayer extends RenderLayer<PigeonRenderState, PigeonModel> {
    public static final ModelLayerLocation MODEL_LAYER = new ModelLayerLocation(Envelope.resource("pigeon_backpack"), "main");

    public static final Identifier TEXTURE = Envelope.resource("textures/entity/pigeon/misc/pigeon_backpack.png");

    protected final PigeonModel model;

    public PigeonBackpackLayer(RenderLayerParent<PigeonRenderState, PigeonModel> renderer, EntityModelSet modelSet) {
        super(renderer);
        model = new PigeonModel(modelSet.bakeLayer(MODEL_LAYER));
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, PigeonRenderState state,
                       float yRot, float xRot) {
        if (!state.isBaby && state.hasMail) {
            // 1.21.11: the copy model is animated from the same render state when it is drawn (no more copyPropertiesTo)
            coloredCutoutModelCopyLayerRender(model, TEXTURE, poseStack, submitNodeCollector, state.layerLight, state, -1, 1);
        }
    }
}
