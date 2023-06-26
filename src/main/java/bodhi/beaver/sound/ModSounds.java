package bodhi.beaver.sound;

import net.minecraft.sound.SoundEvent;
import net.minecraft.util.registry.Registry;


public class ModSounds {
    public static final SoundEvent BEAVER_AMBIENT = new SoundEvent(SoundIdentifier.BEAVER_AMBIENT);

    public static void register(Object optionalEvent) {
        Registry.register(Registry.SOUND_EVENT, SoundIdentifier.BEAVER_AMBIENT, BEAVER_AMBIENT);
    }
}
