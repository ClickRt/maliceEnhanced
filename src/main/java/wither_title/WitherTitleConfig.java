package wither_title;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 未定义凋灵 COMMON 配置，生成 config/malice_enhanced-common.toml。
 */
public final class WitherTitleConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue HEALTH;
    public static final ForgeConfigSpec.DoubleValue HURT_LIMIT;
    public static final ForgeConfigSpec.DoubleValue SOUL_HURT_LIMIT;
    public static final ForgeConfigSpec.IntValue HURT_DEFEND_TICKS;
    public static final ForgeConfigSpec.IntValue SERVANT_LIFETIME_TICKS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        HEALTH = builder
                .comment("Sets the undefined wither's max health, Default: 1453")
                .defineInRange("undefined_wither_health", (double) WitherTitleHelper.UNDEFINED_MAX_HEALTH, 1.0D, 1_000_000.0D);
        HURT_LIMIT = builder
                .comment("Limit the amount of damage the undefined wither takes each tick. Default: 20.0")
                .defineInRange("undefined_wither_hurt_limit", (double) WitherTitleHelper.UNDEFINED_DAMAGE_CAP, 0.0D, 1_000_000.0D);
        SOUL_HURT_LIMIT = builder
                .comment("Limit soul damage the undefined wither takes per hit. Default: 200.0")
                .defineInRange("undefined_wither_soul_hurt_limit", 200.0D, 0.0D, 1_000_000.0D);
        HURT_DEFEND_TICKS = builder
                .comment("Hurt-defend i-frames after a hit, in ticks. Default: 30")
                .defineInRange("undefined_wither_hurt_defend_ticks", WitherTitleHelper.HURT_DEFEND_TICKS, 0, 20_000);
        SERVANT_LIFETIME_TICKS = builder
                .comment("Lifetime in ticks of undefined wither's summoned wither-skeleton servants before they vanish (Goety-style limited life). Default: 1200 (60s). 0 = permanent.")
                .defineInRange("undefined_wither_servant_lifetime_ticks", WitherTitleHelper.SERVANT_LIFETIME_TICKS, 0, 1_000_000);
        SPEC = builder.build();
    }

    public static float maxHealth() {
        try {
            return HEALTH.get().floatValue();
        } catch (Throwable t) {
            return WitherTitleHelper.UNDEFINED_MAX_HEALTH;
        }
    }

    public static float hurtLimit() {
        try {
            return HURT_LIMIT.get().floatValue();
        } catch (Throwable t) {
            return WitherTitleHelper.UNDEFINED_DAMAGE_CAP;
        }
    }

    public static float soulHurtLimit() {
        try {
            return SOUL_HURT_LIMIT.get().floatValue();
        } catch (Throwable t) {
            return 200.0F;
        }
    }

    public static int hurtDefendTicks() {
        try {
            return HURT_DEFEND_TICKS.get();
        } catch (Throwable t) {
            return WitherTitleHelper.HURT_DEFEND_TICKS;
        }
    }

    public static int servantLifetimeTicks() {
        try {
            return SERVANT_LIFETIME_TICKS.get();
        } catch (Throwable t) {
            return WitherTitleHelper.SERVANT_LIFETIME_TICKS;
        }
    }

    private WitherTitleConfig() {
    }
}
