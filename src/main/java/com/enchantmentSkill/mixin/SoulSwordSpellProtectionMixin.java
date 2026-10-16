package com.enchantmentSkill.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import weapon.SoulBowArrow;
import weapon.SoulSwordBlackHoleAccessor;

import java.lang.reflect.Method;
import java.util.List;

/**
 * 1) 无垠长魂之剑蓝色黑洞：亚波伦 Doom 阶段不可被 discard。
 * 2) 浩瀚星魂之箭：飞行中拦截外部 DISCARDED/KILLED（含直接 setRemoved）；
 *    命中/入地/掉虚空后仍可自行回收。
 */
@Mixin(Entity.class)
public abstract class SoulSwordSpellProtectionMixin {

    private static final int SOUL_SWORD_BLUE = 0x0412FC;
    private static final double GUARD_RANGE = 160.0D;

    @Unique
    private static Class<?> shatter$apostleClass;
    @Unique
    private static Method shatter$isApollyonMethod;
    @Unique
    private static Method shatter$isInNetherMethod;
    @Unique
    private static Method shatter$getDoomMethod;
    @Unique
    private static boolean shatter$reflectionReady;

    @Inject(method = "remove(Lnet/minecraft/world/entity/Entity$RemovalReason;)V",
            at = @At("HEAD"), cancellable = true)
    private void soulSword$protectSpell(Entity.RemovalReason reason, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self instanceof SoulBowArrow arrow && arrow.blocksExternalClear(reason)) {
            ci.cancel();
            return;
        }
        if (reason != Entity.RemovalReason.DISCARDED) return;
        if (self.level().isClientSide) return;
        if (!(self instanceof SoulSwordBlackHoleAccessor accessor)) return;
        if (accessor.soulSword$getColor() != SOUL_SWORD_BLUE) return;
        if (!isDoomApollyonNearby(self)) return;
        ci.cancel();
    }

    @Inject(method = "setRemoved", at = @At("HEAD"), cancellable = true)
    private void soulBow$protectSetRemoved(Entity.RemovalReason reason, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self instanceof SoulBowArrow arrow && arrow.blocksExternalClear(reason)) {
            ci.cancel();
        }
    }

    @Unique
    private static boolean isDoomApollyonNearby(Entity self) {
        shatter$ensureReflection();
        if (shatter$apostleClass == null) return false;
        List<Entity> nearby = self.level().getEntities(self,
                self.getBoundingBox().inflate(GUARD_RANGE),
                e -> shatter$apostleClass.isInstance(e) && isDoomApollyon(e));
        return !nearby.isEmpty();
    }

    @Unique
    private static boolean isDoomApollyon(Entity e) {
        try {
            if (shatter$isApollyonMethod != null
                    && !Boolean.TRUE.equals(shatter$isApollyonMethod.invoke(e))) {
                return false;
            }
            if (shatter$isInNetherMethod != null
                    && !Boolean.TRUE.equals(shatter$isInNetherMethod.invoke(e))) {
                return false;
            }
            if (shatter$getDoomMethod != null) {
                return Boolean.TRUE.equals(shatter$getDoomMethod.invoke(e));
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    @Unique
    private static void shatter$ensureReflection() {
        if (shatter$reflectionReady) return;
        shatter$reflectionReady = true;
        try {
            shatter$apostleClass = Class.forName("com.Polarice3.Goety.common.entities.boss.Apostle");
            shatter$isApollyonMethod = shatter$apostleClass.getMethod("allTitlesApostle_1_20_1$isApollyon");
            shatter$isInNetherMethod = shatter$apostleClass.getMethod("isInNether");
            shatter$getDoomMethod = shatter$apostleClass.getMethod("getDoom");
        } catch (Throwable t) {
            shatter$apostleClass = null;
        }
    }
}
