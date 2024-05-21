package bodhi.beaver.entity.client;

import bodhi.beaver.entity.Beaver;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import java.util.Optional;

public class BeaverRenderer extends GeoEntityRenderer<Beaver> {
    private final HeldItemRenderer heldItemRenderer;
    Beaver beaver;
    public BeaverRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new BeaverModel());
        this.shadowRadius = 0.4f;
        this.heldItemRenderer = ctx.getHeldItemRenderer();
    }

    @Override
    public void renderRecursively(MatrixStack poseStack, Beaver beaver, GeoBone bone, RenderLayer renderType, VertexConsumerProvider bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight,
                                  int packedOverlay, float red, float green, float blue, float alpha) {
        ItemStack heldItem = beaver.getEquippedStack(EquipmentSlot.MAINHAND);

        if (bone.getName().equals("leg3") && !heldItem.isEmpty()) {
            poseStack.push();
            if (!beaver.isTouchingWater()) {
                poseStack.translate(0, 0.2, -0.45);
            } else {
                poseStack.translate(0, -.4, -0.5);
            }
            //stack.multiply(Vec3f.POSITIVE_X.getDegreesQuaternion(-90));
            poseStack.scale(1.7f, 1.7f, 1.7f);

            heldItemRenderer.renderItem(beaver, heldItem, ModelTransformationMode.GROUND, false, poseStack, bufferSource, packedLight);
            poseStack.pop();

            buffer = bufferSource.getBuffer(RenderLayer.getEntityCutout(getTexture(beaver)));
        }
        super.renderRecursively(poseStack, beaver,  bone,  renderType,  bufferSource,  buffer,  isReRender,  partialTick,  packedLight,
         packedOverlay, red,  green,  blue,  alpha);
    }

    @Override
    public void preRender(MatrixStack poseStack, Beaver beaver, BakedGeoModel model, VertexConsumerProvider bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, float red, float green, float blue,
                          float alpha) {
        Optional<GeoBone> head = model.getBone("head");
        if (beaver.isBaby()) {
            poseStack.scale(0.4f, 0.4f, 0.4f);
            head.ifPresent(geoBone -> {
                geoBone.setScaleX(1.4f);
                geoBone.setScaleY(1.4f);
                geoBone.setScaleZ(1.4f);
            });
        } else {
            poseStack.scale(0.8f, 0.8f, 0.8f);
            head.ifPresent(geoBone -> {
                geoBone.setScaleX(1f);
                geoBone.setScaleY(1f);
                geoBone.setScaleZ(1f);
            });
        }
        super.preRender(poseStack, beaver, model, bufferSource, buffer, isReRender, packedLight, packedOverlay, packedLight, red, green, blue, alpha);
    }
}