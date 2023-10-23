package bodhi.beaver.entity.client;

import bodhi.beaver.EntityTesting;
import bodhi.beaver.entity.Beaver;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

@Environment(EnvType.CLIENT)
public class BeaverModel extends GeoModel<Beaver> {
    @Override
    public Identifier getModelResource(Beaver animatable) {
        return new Identifier(EntityTesting.MOD_ID, "geo/beaver.geo.json");
    }

    @Override
    public Identifier getTextureResource(Beaver animatable) {
        return new Identifier(EntityTesting.MOD_ID, "textures/beavertexture.png");
    }

    @Override
    public Identifier getAnimationResource(Beaver animatable) {
        return new Identifier(EntityTesting.MOD_ID, "animations/beaver.animation.json");
    }

    @Override
    public void setCustomAnimations(Beaver animatable, long instanceId, AnimationState<Beaver> animationState) {
        CoreGeoBone head = getAnimationProcessor().getBone("head");

        if (head != null) {
            EntityModelData entityData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
            head.setRotX(entityData.headPitch() * MathHelper.RADIANS_PER_DEGREE);
            head.setRotY(entityData.netHeadYaw() * MathHelper.RADIANS_PER_DEGREE);
        }
    }
}
