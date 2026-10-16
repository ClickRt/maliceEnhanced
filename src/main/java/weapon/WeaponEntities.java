package weapon;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class WeaponEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "malice_enhanced");

    public static final RegistryObject<EntityType<SoulBowArrow>> SOUL_BOW_ARROW =
            ENTITIES.register("soul_bow_arrow", () -> EntityType.Builder
                    .<SoulBowArrow>of(SoulBowArrow::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .build("soul_bow_arrow"));

    public static void init(IEventBus eventBus) {
        ENTITIES.register(eventBus);
    }
}
