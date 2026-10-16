package weapon;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class WeaponSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, "malice_enhanced");

    public static final RegistryObject<SoundEvent> SWORD_PARRY = SOUNDS.register("sword_parry",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath("malice_enhanced", "sword_parry")));

    public static final RegistryObject<SoundEvent> SWING_SWORD = SOUNDS.register("swing_sword",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath("malice_enhanced", "swing_sword")));

    public static final RegistryObject<SoundEvent> UNDEFINED_WITHER_BOSS = SOUNDS.register("undefined_wither_boss",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath("malice_enhanced", "undefined_wither_boss")));
}
