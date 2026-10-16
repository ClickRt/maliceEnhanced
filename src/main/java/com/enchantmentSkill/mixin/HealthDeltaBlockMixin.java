package com.enchantmentSkill.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.syncher.EntityDataAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import weapon.SoulSwordItem;
import weapon.SoulSwordEventHandler;
import weapon.starcrystal.StarCoronaCrystalEvents;
import weapon.starcrystal.StarCoronaCurios;
import wither_title.WitherTitleHelper;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 有效血量钳到 0，这就是无垠长魂之剑格挡仍被蒸发的根源（setHealth 只改原始血，被 delta 钳制住）。
 * 格挡无垠长魂之剑时跳过 delta 应用，让 SoulSwordEventHandler 的快照回滚能按原始血量生效。
 * 未定义凋灵：禁能把有效血钳到 <=0.5 的 delta；锁血只看隐藏真血。
 */
@Mixin(targets = "com/mega/uom/util/entity/EntityASMUtil", remap = false)
public abstract class HealthDeltaBlockMixin {
    private static final Set<String> HEALTH_PROBE_OK = ConcurrentHashMap.newKeySet();
    private static final Set<String> HEALTH_PROBE_BROKEN = ConcurrentHashMap.newKeySet();

    @Inject(method = "setHealthDelta(Lnet/minecraft/world/entity/LivingEntity;F)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void soulSword$blockHealthDelta(LivingEntity entity, float value, CallbackInfo ci) {
        if (entity.level().isClientSide()) return;
        if (entity instanceof WitherBoss wither && WitherTitleHelper.isUndefinedWither(wither)) {
            boolean inFinalSkill;
            try {
                inFinalSkill = wither.getEntityData().get(WitherTitleHelper.UNDEFINED_FINAL_SKILL);
            } catch (Throwable t) {
                inFinalSkill = wither.getPersistentData().getBoolean(WitherTitleHelper.UNDEFINED_FINAL_SKILL_NBT_KEY);
            }
            if (inFinalSkill || WitherTitleHelper.isHurtDefendActive(wither)
                    || wither.getMaxHealth() + value <= 0.5F) {
                ci.cancel();
            }
            return;
        }
        if (!(entity instanceof Player player)) return;

        // Fantasy-Ending's negative healthDelta is an effective-health write,
        // not a LivingHurtEvent.  A Soul Heart must still apply the same
        // 20-point raw cap and defensive multipliers, otherwise the delta can
        // accumulate below zero and kill the player while ordinary damage is
        // reduced to almost nothing.
        if (StarCoronaCurios.hasSoulHeart(player)) {
            if (StarCoronaCrystalEvents.isSoulHeartHurtDefendBlocking(player)) {
                ci.cancel();
                return;
            }
            if (Float.isNaN(value)) {
                // NaN is used by some external-mod kill paths as an
                // unconditional effective-health kill.  Do not let it bypass
                // Soul Heart.
                ci.cancel();
                return;
            }
            float currentDelta = getHealthDelta(player);
            if (!Float.isNaN(currentDelta) && value < currentDelta) {
                float rawDamage = currentDelta - value;
                float damage = StarCoronaCrystalEvents.reduceDirectHealthDamage(player, rawDamage);
                if (damage > 0.0F && !Float.isNaN(damage)) {
                    try {
                        float rawHealth = player.getEntityData().get(
                                LivingEntityAccessor.shatter$getDataHealthId());
                        SoulSwordEventHandler.directSetHealth(player, rawHealth - damage);
                    } catch (Throwable ignored) {
                    }
                }
                // Keep the delta unchanged; its negative component has been
                // converted into real, mitigated health damage above.
                ci.cancel();
                return;
            }
            return;
        }
        if (player.isUsingItem() && player.getUseItem().getItem() instanceof SoulSwordItem) {
            ci.cancel();
        }
    }

    private static float getHealthDelta(LivingEntity entity) {
        try {
            Class<?> util = Class.forName("com.mega.uom.util.entity.EntityASMUtil");
            Object value = util.getMethod("getHealthDelta", LivingEntity.class).invoke(null, entity);
            return value instanceof Number number ? number.floatValue() : 0.0F;
        } catch (Throwable ignored) {
            return 0.0F;
        }
    }

    @Inject(method = "special_getHealth(FLjava/lang/Object;)F",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void witherTitle$specialGetHealth(float health, Object entity, CallbackInfoReturnable<Float> cir) {
        if (!(entity instanceof WitherBoss wither) || !WitherTitleHelper.isUndefinedWither(wither)) return;
        boolean inFinalSkill;
        try {
            inFinalSkill = wither.getEntityData().get(WitherTitleHelper.UNDEFINED_FINAL_SKILL);
        } catch (Throwable t) {
            inFinalSkill = wither.getPersistentData().getBoolean(WitherTitleHelper.UNDEFINED_FINAL_SKILL_NBT_KEY);
        }
        if (inFinalSkill) {
            cir.setReturnValue(0.3999F);
            return;
        }
        cir.setReturnValue(wither.level().isClientSide ? health : WitherTitleHelper.getHiddenHp(wither));
    }

    /**
     * Fantasy Ending scans static HEALTH EntityDataAccessor fields for every
     * LivingEntity. A malformed optional entity can expose a null accessor;
     * let that entity keep vanilla health instead of entering the unsafe ASM
     * scan and crashing world load.
     */
    @Inject(method = "special_getHealth(FLjava/lang/Object;)F",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void malice$skipBrokenProbe(float health, Object entity,
                                                    CallbackInfoReturnable<Float> cir) {
        if (!(entity instanceof LivingEntity living)) return;
        if (living instanceof WitherBoss wither && WitherTitleHelper.isUndefinedWither(wither)) return;
        if (hasBrokenHealthAccessor(living.getClass())) {
            cir.setReturnValue(health);
        }
    }

    private static boolean hasBrokenHealthAccessor(Class<?> type) {
        String name = type.getName();
        if (HEALTH_PROBE_BROKEN.contains(name)) return true;
        if (HEALTH_PROBE_OK.contains(name)) return false;
        Class<?> current = type;
        try {
            while (current != null && LivingEntity.class.isAssignableFrom(current)) {
                for (Field field : current.getDeclaredFields()) {
                    String fieldName = field.getName().toUpperCase(java.util.Locale.ROOT);
                    if (!Modifier.isStatic(field.getModifiers())
                            || !fieldName.contains("HEALTH")
                            || !EntityDataAccessor.class.isAssignableFrom(field.getType())) {
                        continue;
                    }
                    field.setAccessible(true);
                    if (field.get(null) == null) {
                        HEALTH_PROBE_BROKEN.add(name);
                        return true;
                    }
                }
                current = current.getSuperclass();
            }
        } catch (Throwable ignored) {
            // If probing itself is unavailable, leave the original ASM path intact.
        }
        HEALTH_PROBE_OK.add(name);
        return false;
    }

    @Inject(method = "special_isDeadOrDying(ZLjava/lang/Object;)Z",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void witherTitle$specialIsDead(boolean src, Object entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof WitherBoss wither && WitherTitleHelper.isUndefinedWither(wither)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "special_isAlive(ZLjava/lang/Object;)Z",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void witherTitle$specialIsAlive(boolean src, Object entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof WitherBoss wither && WitherTitleHelper.isUndefinedWither(wither)) {
            cir.setReturnValue(true);
        }
    }
}
