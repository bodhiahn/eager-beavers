package bodhi.beaver;

import bodhi.beaver.entity.Beaver;
import bodhi.beaver.entity.client.ModEntities;
import bodhi.beaver.items.BeaverPelt;
import bodhi.beaver.materials.BeaverArmorMaterial;
import bodhi.beaver.world.gen.BeaverGen;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.GeckoLib;

public class BeaverMod implements ModInitializer {
    public static final String MOD_ID = "beavermod";
    public static final ArmorMaterial beaverMaterial = new BeaverArmorMaterial();

    public static final Identifier BEAVER_AMBIENT_ID = new Identifier("beavermod:beaver_ambient");
    public static SoundEvent BEAVER_AMBIENT = SoundEvent.of(BEAVER_AMBIENT_ID);
    public static final Item BEAVER_SPAWN_EGG = new SpawnEggItem(ModEntities.BEAVER, 0x4a2d22, 0x632820, new Item.Settings());
    public static final Item BEAVER_PELT = new BeaverPelt(new FabricItemSettings());

    public static final Item BEAVER_HELMET = new ArmorItem(beaverMaterial, ArmorItem.Type.HELMET, new Item.Settings());
    @Override
    public void onInitialize() {
        GeckoLib.initialize();
        BeaverGen.generateWorldGen();
        FabricDefaultAttributeRegistry.register(ModEntities.BEAVER, Beaver.setAttributes());
        Registry.register(Registries.ITEM, new Identifier(MOD_ID, "beaver_spawn_egg"), BEAVER_SPAWN_EGG);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(content -> {
            content.add(BEAVER_SPAWN_EGG);
        });
        Registry.register(Registries.ITEM, new Identifier("beavermod", "beaver_helmet"), BEAVER_HELMET);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(content -> {
            content.add(BEAVER_HELMET);
        });

        Registry.register(Registries.ITEM, new Identifier("beavermod", "beaver_pelt"), BEAVER_PELT);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(content -> {
            content.add(BEAVER_PELT);
        });

        Registry.register(Registries.SOUND_EVENT, BEAVER_AMBIENT_ID, BEAVER_AMBIENT);
    }
}
