package bodhi.beaver;

import bodhi.beaver.entity.Beaver;
import bodhi.beaver.entity.client.ModEntities;
import bodhi.beaver.items.BeaverPelt;
import bodhi.beaver.materials.BeaverArmorMaterial;
import net.minecraft.item.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ObjectHolder;
import software.bernie.geckolib.GeckoLib;

@Mod(BeaverMod.MOD_ID)
public class BeaverMod {
    public static final String MOD_ID = "beavermod";

    public BeaverMod() {
        GeckoLib.initialize();
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        FMLJavaModLoadingContext.get().getModEventBus().addGenericListener(Item.class, this::onItemRegistry);
        FMLJavaModLoadingContext.get().getModEventBus().addGenericListener(SoundEvent.class, this::onSoundEventRegistry);
        // Register the setup method for modloading
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void setup(final FMLCommonSetupEvent event) {
        // Some common setup tasks
    }

    @SubscribeEvent
    public void onItemRegistry(final RegisterCommandsEvent.Register<Item> event) {
        // Register your items here
        event.getRegistry().registerAll(
                new SpawnEggItem(ModEntities.BEAVER, 0x4a2d22, 0x632820, new Item.Properties()).setRegistryName(new ResourceLocation(MOD_ID, "beaver_spawn_egg")),
                new BeaverPelt(new Item.Properties()).setRegistryName(new ResourceLocation(MOD_ID, "beaver_pelt")),
                new ArmorItem(BeaverArmorMaterial.BEAVER, EquipmentSlotType.HEAD, new Item.Properties()).setRegistryName(new ResourceLocation(MOD_ID, "beaver_helmet"))
        );
    }

    @SubscribeEvent
    public void onSoundEventRegistry(final RegistryEvent.Register<SoundEvent> event) {
        // Register your sound events here
        event.register(new SoundEvent(new ResourceLocation(MOD_ID, "beaver_ambient")).setRegistryName(new ResourceLocation(MOD_ID, "beaver_ambient")));
    }

    // Make sure to have your EntityTypes registered in a similar manner
    // Remember to register entities using the DeferredRegister or within the RegistryEvent.Register<EntityType<?>> event
}
