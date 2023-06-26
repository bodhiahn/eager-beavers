package bodhi.beaver.entity.client;


import bodhi.beaver.entity.Beaver;
import bodhi.beaver.EntityTesting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3f;
import software.bernie.geckolib3.geo.render.built.GeoBone;
import software.bernie.geckolib3.renderers.geo.GeoEntityRenderer;

public class BeaverRenderer extends GeoEntityRenderer<Beaver> {
    private final HeldItemRenderer heldItemRenderer;
    Beaver beaver;
    public BeaverRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new BeaverModel());
        this.shadowRadius = 0.4f;
        this.heldItemRenderer = ctx.getHeldItemRenderer();
    }
    @Override
    public void renderEarly(Beaver animatable, MatrixStack stackIn, float ticks, VertexConsumerProvider renderTypeBuffer, VertexConsumer vertexBuilder, int packedLightIn, int packedOverlayIn, float red, float green, float blue, float partialTicks) {
        beaver = animatable;
        super.renderEarly(animatable, stackIn, ticks, renderTypeBuffer, vertexBuilder, packedLightIn, packedOverlayIn, red, green, blue, partialTicks);
    }
    @Override
    public Identifier getTextureResource(Beaver instance) {
        return new Identifier(EntityTesting.MOD_ID, "textures/beavertexture.png");
    }
    @Override
    public void renderRecursively(GeoBone bone, MatrixStack stack, VertexConsumer bufferIn, int packedLightIn, int packedOverlayIn, float red, float green, float blue, float alpha) {
        ItemStack heldItem = beaver.getEquippedStack(EquipmentSlot.MAINHAND);
        if (bone.getName().equals("leg3") && !heldItem.isEmpty()) {
            stack.push();
            if (!beaver.isTouchingWater()) {
                stack.translate(0, 0.2, -0.45);
            } else {
                stack.translate(0, -.4, -0.5);
            }
            //stack.multiply(Vec3f.POSITIVE_X.getDegreesQuaternion(-90));
            stack.scale(1.7f, 1.7f, 1.7f);

            heldItemRenderer.renderItem(beaver, heldItem, ModelTransformation.Mode.GROUND, false, stack, rtb, packedLightIn);
            stack.pop();

            bufferIn = rtb.getBuffer(RenderLayer.getEntityCutout(whTexture));
        }
        super.renderRecursively(bone, stack, bufferIn, packedLightIn, packedOverlayIn, red, green, blue, alpha);
    }

    @Override
    public RenderLayer getRenderType(Beaver animatable, float partialTicks, MatrixStack stack,
                                     VertexConsumerProvider renderTypeBuffer, VertexConsumer vertexBuilder,
                                     int packedLightIn, Identifier textureLocation) {
        if (animatable.isBaby()) {
            stack.scale(0.4f, 0.4f, 0.4f);
        } else {
            stack.scale(0.8f, 0.8f, 0.8f);
        }
        return super.getRenderType(animatable, partialTicks, stack, renderTypeBuffer, vertexBuilder, packedLightIn, textureLocation);
    }
}