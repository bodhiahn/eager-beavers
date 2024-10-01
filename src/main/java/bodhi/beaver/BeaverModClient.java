package bodhi.beaver;

import bodhi.beaver.entity.client.BeaverRenderer;
import bodhi.beaver.entity.client.ModEntities;
import com.mojang.serialization.Codec;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.GlobalPos;

import java.util.List;
import java.util.Optional;

public class BeaverModClient implements ClientModInitializer {
	// Directly register the custom memory module type
	public static final MemoryModuleType<GlobalPos> BEAVER_TARGET = Registry.register(
			Registries.MEMORY_MODULE_TYPE,
			new Identifier("beavermod", "beaver_target"),
			new MemoryModuleType<>(Optional.of(GlobalPos.CODEC))
	);

	public static final MemoryModuleType<List<GlobalPos>> BEAVER_FALLEN_LOGS = Registry.register(
			Registries.MEMORY_MODULE_TYPE,
			new Identifier("beavermod", "beaver_fallen_logs"),
			new MemoryModuleType<>(Optional.of( Codec.list(GlobalPos.CODEC)))
	);


	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntities.BEAVER, BeaverRenderer::new);
	}
}