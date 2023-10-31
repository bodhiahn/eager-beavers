package bodhi.beaver;

import bodhi.beaver.entity.client.BeaverRenderer;
import bodhi.beaver.entity.client.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class BeaverModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {

		EntityRendererRegistry.register(ModEntities.BEAVER, BeaverRenderer::new);

	}
}