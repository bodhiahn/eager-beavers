package bodhi.beaver;

import bodhi.beaver.entity.Beaver;
import bodhi.beaver.entity.client.BeaverRenderer;
import bodhi.beaver.entity.client.ModEntities;
import bodhi.beaver.world.gen.ModWorldGen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class EntityTestingClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {

		EntityRendererRegistry.register(ModEntities.BEAVER, BeaverRenderer::new);

	}
}