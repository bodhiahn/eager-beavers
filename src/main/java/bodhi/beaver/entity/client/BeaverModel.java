package bodhi.beaver.entity.client;

import bodhi.beaver.EntityTesting;
import bodhi.beaver.entity.Beaver;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import net.minecraft.util.math.MathHelper;
import software.bernie.geckolib3.core.AnimationState;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.processor.IBone;
import software.bernie.geckolib3.model.AnimatedGeoModel;
import software.bernie.geckolib3.model.provider.data.EntityModelData;

import java.util.List;

@Environment(EnvType.CLIENT)
public class BeaverModel extends AnimatedGeoModel<Beaver> {
    @Override
    public Identifier getModelResource(Beaver object) {
        return new Identifier(EntityTesting.MOD_ID, "geo/beaver.geo.json");
    }

    @Override
    public Identifier getTextureResource(Beaver object) {
        return new Identifier(EntityTesting.MOD_ID, "textures/beavertexture.png");
    }

    @Override
    public Identifier getAnimationResource(Beaver animatable) {
        return new Identifier(EntityTesting.MOD_ID, "animations/beaver.animation.json");
    }

    @Override
    public void setLivingAnimations(Beaver beaver, Integer uniqueID, AnimationEvent customPredicate) {
        super.setCustomAnimations(beaver, uniqueID, customPredicate);
        if (customPredicate == null) return;

        List<EntityModelData> extraDataOfType = customPredicate.getExtraDataOfType(EntityModelData.class);
        IBone head = this.getAnimationProcessor().getBone("head");

        if (beaver.isBaby()) {
            head.setScaleX(1.4F);
            head.setScaleY(1.4F);
            head.setScaleZ(1.4F);
        }

        head.setRotationX(extraDataOfType.get(0).headPitch * MathHelper.RADIANS_PER_DEGREE);
        head.setRotationY(extraDataOfType.get(0).netHeadYaw * MathHelper.RADIANS_PER_DEGREE);
    }
}
