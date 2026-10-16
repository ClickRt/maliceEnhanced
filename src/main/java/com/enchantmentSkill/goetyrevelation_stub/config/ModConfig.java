package com.enchantmentSkill.goetyrevelation_stub.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Configuration class for Goety Revelation.
 * This is a stub for compilation purposes only.
 * Actual config values are loaded from GoetyRevelation at runtime.
 */
public class ModConfig {
    public static final ForgeConfigSpec.ConfigValue<Double> APOLLYON_HURT_LIMIT;
    public static final ForgeConfigSpec.ConfigValue<Double> APOLLYON_HEALTH;
    public static final ForgeConfigSpec.ConfigValue<Boolean> ENABLE_PLAYER_AMBIENT;

    static {
        // 默认值，实际值由 GoetyRevelation 在运行时提供
        ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
        APOLLYON_HURT_LIMIT = BUILDER.comment("Limit the amount of damage Apollyon takes each time. Default: 20.0").define("apollyon_hurt_limit", (double)20.0F);
        APOLLYON_HEALTH = BUILDER.comment("Health value for Apollyon form").define("apollyon_health", (double)600.0F);
        ENABLE_PLAYER_AMBIENT = BUILDER.comment("Enable player ambient sounds").define("enable_player_ambient", true);
    }
}
