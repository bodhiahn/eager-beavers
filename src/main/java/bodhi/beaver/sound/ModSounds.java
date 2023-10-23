package bodhi.beaver.sound;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;


public class ModSounds {
    public static final Identifier BEAVER_AMBIENT_ID = new Identifier("beaver_ambient");
    public static SoundEvent BEAVER_AMBIENT = SoundEvent.of(BEAVER_AMBIENT_ID);

    public static void register(Object optionalEvent) {
        Registry.register(Registries.SOUND_EVENT, ModSounds.BEAVER_AMBIENT_ID, BEAVER_AMBIENT);
    }
}
