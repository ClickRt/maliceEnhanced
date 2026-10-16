package com.enchantmentSkill.health;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import wither_title.WitherTitleHelper;

import java.lang.reflect.Method;

/** Bridges the local shadow channel with the external delta channel. */
public final class HealthDeltaBridge {
    private static final String ASM_UTIL_CLASS = "com.mega.uom.util.entity.EntityASMUtil";
    private static volatile boolean bridgeChecked;
    private static volatile Method getDeltaMethod;
    private static volatile Method setDeltaMethod;

    private HealthDeltaBridge() {
    }

    /**
     * The lightweight delta store is used when the entity inherits the vanilla
     * health implementation. Entities with a custom health model are handled
     * by the caller's generic hidden-health probe instead of a class-specific
     * mixin or adapter.
     */
    public static boolean isEligibleTarget(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide || entity instanceof Player) return false;
        if (entity instanceof WitherBoss wither && WitherTitleHelper.isUndefinedWither(wither)) return false;
        if (isSpecialEntityData(entity)) return false;
        // Do not classify entities by their concrete getHealth implementation.
        // A custom override may itself fold the active delta channel; the
        // before/after check below decides whether the write was effective and
        // cleanly falls through when that entity ignores this channel.
        return true;
    }

    /** True when this entity should read the local channel instead of the external one. */
    public static boolean useOwnStorage(LivingEntity entity) {
        if (entity == null || entity instanceof Player) return false;
        if (entity instanceof WitherBoss wither && WitherTitleHelper.isUndefinedWither(wither)) return false;
        return !ensureExternalBridge();
    }

    /** Applies negative delta damage and verifies that effective health moved. */
    public static boolean applyDamage(LivingEntity entity, float amount) {
        if (!isEligibleTarget(entity) || !(amount > 0.0F) || !Float.isFinite(amount)) return false;
        float before = entity.getHealth();
        float oldDelta = getDelta(entity);
        float maxHealth = entity.getMaxHealth();
        if (!Float.isFinite(before) || !Float.isFinite(oldDelta) || !Float.isFinite(maxHealth)) return false;
        // Choose the smallest delta that lowers the effective health by the
        // requested amount. This also handles a pre-existing positive delta:
        // subtracting from that value could leave the effective health unchanged.
        float next = Math.min(oldDelta, before - amount - maxHealth);
        if (!setDelta(entity, next)) return false;
        float after = entity.getHealth();
        if (after < before - 0.001F) return true;
        // The min(baseHealth, maxHealth + delta) fold is inactive for this target;
        // do not leave an invisible charge that could apply to a later hit.
        setDelta(entity, oldDelta);
        return false;
    }

    private static float getDelta(LivingEntity entity) {
        if (ensureExternalBridge()) {
            try {
                Object value = getDeltaMethod.invoke(null, entity);
                return value instanceof Number number ? number.floatValue() : 0.0F;
            } catch (Throwable ignored) {
                return 0.0F;
            }
        }
        return HealthDeltaStorage.get(entity);
    }

    private static boolean setDelta(LivingEntity entity, float value) {
        if (!Float.isFinite(value)) return false;
        if (ensureExternalBridge()) {
            try {
                // The external channel accepts non-positive values only. A positive existing delta
                // cannot contribute to damage until it has been consumed.
                if (value > 0.0F) return false;
                setDeltaMethod.invoke(null, entity, value);
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }
        return HealthDeltaStorage.set(entity, value);
    }

    private static boolean isSpecialEntityData(LivingEntity entity) {
        try {
            return "com.mega.uom.common.entity.boss.uom.UomWither$UomEntityData"
                    .equals(entity.getEntityData().getClass().getName());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean ensureExternalBridge() {
        if (bridgeChecked) return getDeltaMethod != null && setDeltaMethod != null;
        synchronized (HealthDeltaBridge.class) {
            if (bridgeChecked) return getDeltaMethod != null && setDeltaMethod != null;
            try {
                Class<?> util = Class.forName(ASM_UTIL_CLASS);
                getDeltaMethod = util.getMethod("getHealthDelta", LivingEntity.class);
                setDeltaMethod = util.getMethod("setHealthDelta", LivingEntity.class, float.class);
            } catch (Throwable ignored) {
                getDeltaMethod = null;
                setDeltaMethod = null;
            } finally {
                bridgeChecked = true;
            }
            return getDeltaMethod != null && setDeltaMethod != null;
        }
    }
}
