package wither_title;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class WitherTitleEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "malice_enhanced");

    public static final RegistryObject<EntityType<GravitySingularityEntity>> GRAVITY_SINGULARITY =
            ENTITIES.register("gravity_singularity", () -> EntityType.Builder
                    .<GravitySingularityEntity>of(GravitySingularityEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).fireImmune().noSummon()
                    .clientTrackingRange(16).updateInterval(4)
                    .build("gravity_singularity"));

    public static final RegistryObject<EntityType<BallLightningEntity>> BALL_LIGHTNING =
            ENTITIES.register("ball_lightning", () -> EntityType.Builder
                    .<BallLightningEntity>of(BallLightningEntity::new, MobCategory.MISC)
                    .sized(2.0F, 2.0F).fireImmune().noSummon()
                    .clientTrackingRange(16).updateInterval(4)
                    .build("ball_lightning"));

    public static final RegistryObject<EntityType<SummonUndefinedWither>> SUMMON_UNDEFINED_WITHER =
            ENTITIES.register("summon_undefined_wither", () -> EntityType.Builder
                    .<SummonUndefinedWither>of(SummonUndefinedWither::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).fireImmune()
                    .clientTrackingRange(10).updateInterval(Integer.MAX_VALUE)
                    .build("summon_undefined_wither"));

    public static final RegistryObject<EntityType<StarCoronaBeamEntity>> STAR_CORONA_BEAM =
            ENTITIES.register("star_corona_beam", () -> EntityType.Builder
                    .<StarCoronaBeamEntity>of(StarCoronaBeamEntity::new, MobCategory.MISC)
                    .sized(0.1F, 0.1F).fireImmune().noSummon()
                    .clientTrackingRange(64).updateInterval(1)
                    .build("star_corona_beam"));

    /** 未定义凋灵仆从（Goety 式认主凋灵骷髅）：体型对齐原版凋灵骷髅，怪物类别 */
    public static final RegistryObject<EntityType<UndefinedWitherServant>> UNDEFINED_WITHER_SERVANT =
            ENTITIES.register("undefined_wither_servant", () -> EntityType.Builder
                    .<UndefinedWitherServant>of(UndefinedWitherServant::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.98F).fireImmune()
                    .clientTrackingRange(48).updateInterval(3)
                    .build("undefined_wither_servant"));

    // 星冕宝晶头衔 6 召唤的悦灵仆从：自定义 Allay 子类，血 30、仅主人可交互（防接管）
    public static final RegistryObject<EntityType<weapon.starcrystal.StarCoronaServantAllay>> STAR_CORONA_SERVANT_ALLAY =
            ENTITIES.register("star_corona_servant_allay", () -> EntityType.Builder
                    .<weapon.starcrystal.StarCoronaServantAllay>of(weapon.starcrystal.StarCoronaServantAllay::new, MobCategory.CREATURE)
                    .sized(0.35F, 0.6F)
                    .clientTrackingRange(8)
                    .build("star_corona_servant_allay"));

    public static void init(IEventBus eventBus) {
        ENTITIES.register(eventBus);
    }
}
