package bodhi.beaver.data;

import bodhi.beaver.EntityTesting;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricModelProvider;
import net.minecraft.data.client.BlockStateModelGenerator;
import net.minecraft.data.client.ItemModelGenerator;
import net.minecraft.data.client.ModelIds;

public class ModModelProvider extends FabricModelProvider {
    public ModModelProvider(FabricDataOutput dataGenerator) {
        super(dataGenerator);
    }

    @Override
    public void generateBlockStateModels(BlockStateModelGenerator blockStateModelGenerator) {
        blockStateModelGenerator.registerParentedItemModel(EntityTesting.BEAVER_SPAWN_EGG, ModelIds.getMinecraftNamespacedItem("template_spawn_egg"));
    }

    @Override
    public void generateItemModels(ItemModelGenerator itemModelGenerator) {

    }
}
