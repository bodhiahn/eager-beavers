package bodhi.beaver.mixin;

import bodhi.beaver.BeaverTailModel;
import bodhi.beaver.entity.Beaver;
import bodhi.beaver.entity.client.BeaverRenderer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class BeaverTailMixin extends LivingEntityRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {

    @Unique
    private BeaverTailModel beaverTailModel;

    public BeaverTailMixin(EntityRendererFactory.Context ctx, PlayerEntityModel<AbstractClientPlayerEntity> model, float shadowRadius) {
        super(ctx, model, shadowRadius);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(EntityRendererFactory.Context ctx, boolean slim, CallbackInfo ci) {
        // Initialize your BeaverTailModel here and pass required parameters
        BeaverRenderer beaverRenderer = new BeaverRenderer(ctx);
        this.beaverTailModel = new BeaverTailModel(this, this.getModel(), beaverRenderer);
    }

    @Inject(method = "render*", at = @At("TAIL"))
    public void render(AbstractClientPlayerEntity player, float f, float g, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int i, CallbackInfo info) {
        // Call the render method of BeaverTailModel here
        this.beaverTailModel.render(matrixStack, vertexConsumerProvider, i, player, 0, 0, 0, 0, 0, 0);
    }
}