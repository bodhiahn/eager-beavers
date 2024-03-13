package bodhi.beaver.materials;


import bodhi.beaver.BeaverMod;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;


public class BeaverArmorMaterial implements ArmorMaterial {
    private static final int[] BASE_DURABILITY = new int[] {13, 15, 16, 11};
    private static final int[] PROTECTION_VALUES = new int[] {2, 2, 2, 2};

    @Override
    public int getDurabilityForType(ArmorItem.@NotNull Type p_266807_) {
        return 13;
    }

    @Override
    public int getDefenseForType(ArmorItem.@NotNull Type p_267168_) {
        return 2;
    }

    @Override
    public int getEnchantmentValue() {
        return 2;
    }

    @Override
    public SoundEvent getEquipSound() {
        return SoundEvents.ITEM_ARMOR_EQUIP_LEATHER;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.of(BeaverMod.BEAVER_PELT);
    }

    @Override
    public String getName() {
        return BeaverMod.MOD_ID+":"+"beaver";
    }

    @Override
    public float getToughness() {
        return 0;
    }

    @Override
    public float getKnockbackResistance() {
        return 0;
    }
}
