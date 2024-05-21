package bodhi.beaver.mixin;

import bodhi.beaver.BeaverMod;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerMixin extends LivingEntity {

    protected PlayerMixin(EntityType<? extends LivingEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo info) {
        if (!this.getWorld().isClient()) {
            ItemStack headItem = this.getEquippedStack(EquipmentSlot.HEAD);
            if (headItem.getItem() == BeaverMod.BEAVER_HELMET) { // Replace with your actual item
                this.addStatusEffect(new StatusEffectInstance(StatusEffects.DOLPHINS_GRACE, 1, 0, false, false, false));
            }
        }
    }
}