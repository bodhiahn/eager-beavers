package bodhi.beaver.entity.client;

import bodhi.beaver.EntityTesting;
import bodhi.beaver.entity.Beaver;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

public class ModEntities {
    public static final EntityType<Beaver> BEAVER = Registry.register(
            Registry.ENTITY_TYPE,
            new Identifier(EntityTesting.MOD_ID, "beaver"),
            FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, Beaver::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 0.6f)).build()
    );
}
