package bodhi.beaver.materials;

import bodhi.beaver.BeaverMod;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ArmorMaterials;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

public class BeaverArmorMaterial implements ArmorMaterial {
    // Durability base values for each armor type (helmet, chestplate, leggings, boots)
    private static final int[] BASE_DURABILITY = new int[] {13, 15, 16, 11};
    // Protection values for each armor type (helmet, chestplate, leggings, boots)
    private static final int[] PROTECTION_VALUES = new int[] {2, 2, 2, 2};

    @Override
    public int getDurability(ArmorItem.Type type) {
        // Map durability based on armor type
        return BASE_DURABILITY[type.ordinal()];
    }

    @Override
    public int getProtection(ArmorItem.Type type) {
        // Map protection based on armor type
        return PROTECTION_VALUES[type.ordinal()];
    }

    @Override
    public int getEnchantability() {
        return 40;
    }

    @Override
    public SoundEvent getEquipSound() {
        return SoundEvents.ITEM_ARMOR_EQUIP_LEATHER;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.ofItems(BeaverMod.BEAVER_PELT);
    }

    @Override
    public String getName() {
        return BeaverMod.MOD_ID + ":" + "beaver";
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
