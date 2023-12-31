package bodhi.beaver;

import bodhi.beaver.entity.Beaver;
import bodhi.beaver.entity.client.ModEntities;
import bodhi.beaver.world.gen.ModWorldGen;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.GeckoLib;

public class EntityTesting implements ModInitializer {
    public static final String MOD_ID = "beavermod";
    public static final Item BEAVER_SPAWN_EGG = new SpawnEggItem(ModEntities.BEAVER, 0x4a2d22, 0x632820, new Item.Settings());

    @Override
    public void onInitialize() {
        GeckoLib.initialize();
        ModWorldGen.generateWorldGen();
        FabricDefaultAttributeRegistry.register(ModEntities.BEAVER, Beaver.setAttributes());
        Registry.register(Registries.ITEM, new Identifier(MOD_ID, "beaver_spawn_egg"), BEAVER_SPAWN_EGG);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(content -> {
            content.add(BEAVER_SPAWN_EGG);
        });
    }
}
