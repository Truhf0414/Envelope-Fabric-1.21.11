package io.github.mortuusars.envelope.client.model;

import io.github.mortuusars.envelope.Envelope;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;

/**
 * 1.21.11: Model is generic over its render state; the backpack has no animation of its own, so it uses {@link Unit}.
 */
public class BatBackpackModel extends Model<Unit> {
    public static final Identifier TEXTURE = Envelope.resource("textures/entity/bat/misc/bat_backpack.png");

    public BatBackpackModel(ModelPart root) {
        super(root, RenderTypes::entityCutoutNoCull);
    }

    public static LayerDefinition createLayerDefinition() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild(
              "backpack",
              CubeListBuilder.create()
                    .texOffs(0, 0)
                    .addBox(-3f, -1f, 0.999f, 6, 7, 5),
              PartPose.ZERO
        );

//        root.addOrReplaceChild(
//              "strap",
//              CubeListBuilder.create()
//                    .texOffs(0, 0)
//                    .addBox(0.75f, -1f, -1f, 1, 6f, 2, new CubeDeformation(0, 0, 0.01f)),
//              PartPose.rotation(0, 0, Mth.DEG_TO_RAD * 32.5f)
//        );

        root.addOrReplaceChild(
              "strap",
              CubeListBuilder.create()
                    .texOffs(0, 12)
                    .addBox(-0.5f, -3, -1, 1, 6, 2, new CubeDeformation(0, 0, 0.01f)),
              PartPose.offsetAndRotation(0, 2.5f, 0, 0, 0, Mth.DEG_TO_RAD * 32.5f)
        );

//        root.addOrReplaceChild(
//              "backpack",
//              CubeListBuilder.create()
//                    .texOffs(0, 0)
//                    .addBox(-4, 1.5f, -2.5f, 8, 5, 5),
//              PartPose.ZERO
//        );

        return LayerDefinition.create(mesh, 32, 32);
    }

}
