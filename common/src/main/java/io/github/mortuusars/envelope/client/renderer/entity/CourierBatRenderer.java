package io.github.mortuusars.envelope.client.renderer.entity;

import io.github.mortuusars.envelope.client.model.CourierBatModel;
import io.github.mortuusars.envelope.client.renderer.entity.layer.BatBackpackLayer;
import io.github.mortuusars.envelope.client.renderer.entity.state.CourierBatRenderState;
import io.github.mortuusars.envelope.world.entity.CourierBat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

/**
 * 1.21.11: renders from a {@link CourierBatRenderState} (same pattern as vanilla's BatRenderer).
 */
@Environment(EnvType.CLIENT)
public class CourierBatRenderer extends MobRenderer<CourierBat, CourierBatRenderState, CourierBatModel> {
    private static final Identifier BAT_LOCATION = Identifier.withDefaultNamespace("textures/entity/bat.png");

    public CourierBatRenderer(EntityRendererProvider.Context context) {
        super(context, new CourierBatModel(context.bakeLayer(ModelLayers.BAT)), 0.25F);
        addLayer(new BatBackpackLayer(this, context.bakeLayer(BatBackpackLayer.MODEL_LAYER)));
    }

    @Override
    public @NotNull CourierBatRenderState createRenderState() {
        return new CourierBatRenderState();
    }

    @Override
    public void extractRenderState(CourierBat bat, CourierBatRenderState state, float partialTick) {
        super.extractRenderState(bat, state, partialTick);
        state.flyAnimationState.copyFrom(bat.flyAnimationState);
        state.hasMail = bat.hasMail();
    }

    @Override
    public @NotNull Identifier getTextureLocation(CourierBatRenderState state) {
        return BAT_LOCATION;
    }
}
