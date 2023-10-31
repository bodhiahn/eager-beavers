package bodhi.beaver;

import bodhi.beaver.entity.Beaver;
import bodhi.beaver.entity.client.BeaverRenderer;
import bodhi.beaver.entity.client.ModEntities;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.renderer.GeoObjectRenderer;

public class BeaverTailModel extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    private final BeaverRenderer beaverRenderer;

    public BeaverTailModel(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context, PlayerEntityModel<AbstractClientPlayerEntity> beaverModel, BeaverRenderer beaverRenderer) {
        super(context);
        this.beaverRenderer = beaverRenderer;
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, AbstractClientPlayerEntity entity, float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
        ItemStack itemStack = entity.getEquippedStack(EquipmentSlot.HEAD);
        if (!itemStack.isOf(BeaverMod.BEAVER_HELMET)) {
            return;
        }
        matrices.push();

        float interpolatedHeadYaw = MathHelper.lerp(tickDelta, entity.prevHeadYaw, entity.headYaw);
        float interpolatedHeadPitch = MathHelper.lerp(tickDelta, entity.prevPitch, entity.getPitch());

        // Translate to neck position
        matrices.translate(0.0F, 1.5F, 0.0F);

        // Apply head rotation
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-interpolatedHeadYaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(interpolatedHeadPitch));

        // Translate back to original position
        matrices.translate(0.0F, -1.5F, 0.0F);


        matrices.translate(0.0f, 1.8f, -0.1f);
        double d = MathHelper.lerp((double)tickDelta, entity.prevCapeX, entity.capeX) - MathHelper.lerp((double)tickDelta, entity.prevX, entity.getX());
        double e = MathHelper.lerp((double)tickDelta, entity.prevCapeY, entity.capeY) - MathHelper.lerp((double)tickDelta, entity.prevY, entity.getY());
        double m = MathHelper.lerp((double)tickDelta, entity.prevCapeZ, entity.capeZ) - MathHelper.lerp((double)tickDelta, entity.prevZ, entity.getZ());
        float n = MathHelper.lerpAngleDegrees(tickDelta, entity.prevHeadYaw, entity.headYaw);
        double o = MathHelper.sin(n * ((float)Math.PI / 180));
        double p = -MathHelper.cos(n * ((float)Math.PI / 180));
        float q = (float)e * 10.0f;
        q = MathHelper.clamp(q, -6.0f, 32.0f);
        float r = (float)(d * o + m * p) * 100.0f;
        r = MathHelper.clamp(r, 0.0f, 150.0f);
        float s = (float)(d * p - m * o) * 100.0f;
        s = MathHelper.clamp(s, -20.0f, 20.0f);
        if (r < 0.0f) {
            r = 0.0f;
        }

        q -= 30.0f;
        float t = MathHelper.lerp(tickDelta, entity.prevStrideDistance, entity.strideDistance);
        q += MathHelper.sin(MathHelper.lerp(tickDelta, entity.prevHorizontalSpeed, entity.horizontalSpeed) * 6.0f) * 32.0f * t;

        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(6.0f + r / 2.0f + q));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(s / 2.0f));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - s / 2.0f));
        matrices.translate(0.0f, -1.8f, 0.1f);
        if (entity.isInSneakingPose()) {
            matrices.translate(0.0f, 1.2f, -.18f);
        } else if (entity.isInSwimmingPose()){
            matrices.translate(0f, 0f, .1f);
        }else {
            matrices.translate(0.0f, 1.45f, -.18f);
        }
        Beaver beaver = new Beaver(ModEntities.BEAVER, entity.getWorld());
        beaver.setHat();

        beaverRenderer.getGeoModel().getBakedModel(beaverRenderer.getGeoModel().getModelResource(beaver)).getBone("tail").ifPresent(tailPart -> {
            RenderLayer renderType = RenderLayer.getEntityCutout(beaverRenderer.getTexture(beaver));
            VertexConsumer vertexConsumer = vertexConsumers.getBuffer(renderType);

            beaverRenderer.renderCubesOfBone(matrices, tailPart, vertexConsumer, light, OverlayTexture.DEFAULT_UV, 1.0F, 1.0F, 1.0F, 1.0F);
        });
        matrices.pop();
    }
}