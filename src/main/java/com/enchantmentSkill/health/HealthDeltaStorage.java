package com.enchantmentSkill.health;

import net.minecraft.world.entity.LivingEntity;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Storage owner for the generic delta channel; kept outside the Mixin class. */
public final class HealthDeltaStorage {
    private static final Map<LivingEntity, Float> VALUES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private HealthDeltaStorage() {
    }

    public static float get(LivingEntity entity) {
        Float value = VALUES.get(entity);
        return value == null ? 0.0F : value;
    }

    public static boolean set(LivingEntity entity, float value) {
        if (!Float.isFinite(value)) return false;
        if (value == 0.0F) VALUES.remove(entity);
        else VALUES.put(entity, value);
        return true;
    }
}
