package com.enchantmentSkill.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import weapon.starcrystal.StarCoronaCrystalEvents;
import wither_title.WitherTitleEventHandler;
import wither_title.WitherTitleHelper;

/**
 * LivingEntity 全局拦截：未定义凋灵锁血防护
 * WitherBoss 没有重写 getHealth/setHealth/hurt/die，Mixin 无法在 WitherBossMixin
 * 中注入这些继承方法，所以必须在 LivingEntity 声明处注入（参考 外部凋灵 的做法）。
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    /** 防止锁血递归 */
    @Unique
    private boolean witherTitle$isLockingHealth = false;

    /** 锁血后返回给调用者的伪血量 */
    @Unique
    private float witherTitle$fakeHealth = -1.0F;

    /** Snapshot used to measure an undefined Wither's ordinary hurt() damage. */
    @Unique
    private float witherTitle$undefinedAttackHealth = -1.0F;
    @Unique
    private WitherBoss witherTitle$undefinedAttackOwner;

    private LivingEntityMixin() {
    }

    @Inject(method = "hurt", at = @At("HEAD"))
    private void starCorona$beginHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player player) {
            StarCoronaCrystalEvents.beginSoulHeartHurt(player);
        }
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.level().isClientSide && !WitherTitleEventHandler.isUndefinedAttackGuarded()) {
            WitherBoss owner = WitherTitleEventHandler.getUndefinedAttacker(source, false);
            if (owner != null && owner != self) {
                witherTitle$undefinedAttackOwner = owner;
                witherTitle$undefinedAttackHealth = self.getHealth();
            }
        }
    }

    @Inject(method = "hurt", at = @At("RETURN"))
    private void starCorona$endHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (witherTitle$undefinedAttackOwner != null) {
            WitherTitleEventHandler.completeUndefinedHurt(witherTitle$undefinedAttackOwner, self,
                    witherTitle$undefinedAttackHealth, cir.getReturnValue());
            witherTitle$undefinedAttackOwner = null;
            witherTitle$undefinedAttackHealth = -1.0F;
        }
        if ((Object) this instanceof Player player) {
            StarCoronaCrystalEvents.endSoulHeartHurt(player);
        }
    }

    private WitherBoss witherTitle$asUndefinedWither() {
        if (!((Object) this instanceof WitherBoss wither)) return null;
        return WitherTitleHelper.isUndefinedWither(wither) ? wither : null;
    }

    /**
     * getHealth 拦截（TAIL）：真实血量 <= 0.5 时触发锁血并返回 0.3999
     * TAIL 处返回值已由原方法算好，直接读取，无需反射，无递归风险
     */
    @Inject(method = "getHealth", at = @At("TAIL"), cancellable = true)
    private void witherTitle$getHealth(CallbackInfoReturnable<Float> cir) {
        WitherBoss wither = witherTitle$asUndefinedWither();
        if (wither == null) return;

        // 已进入尾杀阶段，返回伪血量
        if (witherTitle$fakeHealth > 0) {
            cir.setReturnValue(witherTitle$fakeHealth);
            return;
        }
        try {
            if (wither.getEntityData().get(WitherTitleHelper.UNDEFINED_FINAL_SKILL)) {
                cir.setReturnValue(0.3999F);
                return;
            }
        } catch (Throwable ignored) {
        }

        float hidden = WitherTitleHelper.getHiddenHp(wither);
        if (!wither.level().isClientSide) {
            cir.setReturnValue(hidden);
            if (hidden <= 0.5F) {
                witherTitle$fakeHealth = 0.3999F;
                witherTitle$doTriggerLock(wither);
                cir.setReturnValue(0.3999F);
            }
            return;
        }
        if (cir.getReturnValue() <= 0.5F) {
            cir.setReturnValue(0.3999F);
        }
    }

    /**
     * setHealth 拦截（HEAD）：吞掉会把血量写到 <= 0.5 的调用。
     * 锁血用隐藏真血判定：正规 hurt 已把真血扣进 HiddenHp；
     * 改血/直写先吃每 tick 20 点预算和 30 tick 无敌，再按适应系数扣真血。
     */
    @Inject(method = "setHealth", at = @At("HEAD"), cancellable = true)
    private void witherTitle$setHealth(float health, CallbackInfo ci) {
        WitherBoss wither = witherTitle$asUndefinedWither();
        if (wither == null) return;

        if (witherTitle$isLockingHealth) return;
        if (WitherTitleHelper.isDisplaySyncActive()) return;

        try {
            if (wither.getEntityData().get(WitherTitleHelper.UNDEFINED_FINAL_SKILL)) {
                ci.cancel();
                return;
            }
        } catch (Throwable ignored) {
        }

        if (wither.level().isClientSide) return;

        float hidden = WitherTitleHelper.getHiddenHp(wither);
        if (WitherTitleHelper.isLegitWrite()) {
            if (health > hidden) {
                WitherTitleHelper.applyHiddenHeal(wither, health - hidden);
                hidden = WitherTitleHelper.getHiddenHp(wither);
            }
            if (hidden <= 0.5F) {
                ((LivingEntityAccessor) (Object) this).shatter$setDead(false);
                witherTitle$fakeHealth = 0.3999F;
                witherTitle$doTriggerLock(wither);
                ci.cancel();
            }
            return;
        }

        float current = wither.getEntityData().get(LivingEntityAccessor.shatter$getDataHealthId());
        if (health < current) {
            hidden = WitherTitleHelper.applyIllicitWriteAndRestore(wither, current - health);
            if (hidden <= 0.5F) {
                ((LivingEntityAccessor) (Object) this).shatter$setDead(false);
                witherTitle$fakeHealth = 0.3999F;
                witherTitle$doTriggerLock(wither);
            }
            ci.cancel();
            return;
        }

        if (hidden <= 0.5F) {
            ((LivingEntityAccessor) (Object) this).shatter$setDead(false);
            witherTitle$fakeHealth = 0.3999F;
            witherTitle$doTriggerLock(wither);
            ci.cancel();
        }
    }

    /**
     * hurt 尾杀免疫：尾杀阶段把伤害参数归零
     * （ModifyVariable 修改方法参数本体，才能影响后续实际伤害计算）
     *
     * 20 点限伤已下沉到数据层 WitherTitleHealthCapMixin —— 只有落在
     * SynchedEntityData 的 HEALTH 写入处，才能覆盖绕过 hurt() 的直写路径。
     */
    @ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float witherTitle$blockHurtInFinalSkill(float amount) {
        if ((Object) this instanceof Player player && !player.level().isClientSide) {
            // Clamp before vanilla armor/resistance and Forge damage events;
            // later event and data-layer guards remain as defensive fallbacks.
            return StarCoronaCrystalEvents.capSoulHeartDamageBeforeReduction(player, amount);
        }

        WitherBoss wither = witherTitle$asUndefinedWither();
        if (wither == null) return amount;

        try {
            if (wither.getEntityData().get(WitherTitleHelper.UNDEFINED_FINAL_SKILL)) {
                return 0.0F;
            }
        } catch (Throwable ignored) {
        }
        return amount;
    }

    @Inject(method = "hurt", at = @At("HEAD"))
    private void witherTitle$beginLegitHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (witherTitle$asUndefinedWither() == null) return;
        WitherTitleHelper.beginLegitWrite();
    }

    @Inject(method = "hurt", at = @At("RETURN"))
    private void witherTitle$endLegitHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (witherTitle$asUndefinedWither() == null) return;
        WitherTitleHelper.endLegitWrite();
    }

    @Inject(method = "canAttack(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("RETURN"), cancellable = true)
    private void witherTitle$undefinedCanAttack(LivingEntity living, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;
        WitherBoss wither = witherTitle$asUndefinedWither();
        if (wither == null) return;
        if (living == null || living == wither || !living.isAlive() || living.isSpectator()) return;
        if (living instanceof WitherBoss) return;
        if (living instanceof Player player && (player.isCreative() || player.isSpectator())) return;
        if (WitherTitleHelper.isWitherServant(living, wither.getUUID())) return;
        cir.setReturnValue(true);
    }

    /**
     * heal 拦截（HEAD）：尾杀阶段免疫一切治疗（防原版 aiStep/其他 mod 回血）
     */
    @Inject(method = "heal", at = @At("HEAD"), cancellable = true)
    private void witherTitle$heal(float amount, CallbackInfo ci) {
        WitherBoss wither = witherTitle$asUndefinedWither();
        if (wither == null) return;
        try {
            if (wither.getEntityData().get(WitherTitleHelper.UNDEFINED_FINAL_SKILL)) {
                ci.cancel();
                return;
            }
        } catch (Throwable ignored) {
        }
        WitherTitleHelper.beginLegitWrite();
    }

    @Inject(method = "heal", at = @At("RETURN"))
    private void witherTitle$endLegitHeal(float amount, CallbackInfo ci) {
        if (witherTitle$asUndefinedWither() == null) return;
        WitherTitleHelper.endLegitWrite();
    }

    /**
     * tick 拦截（HEAD）：尾杀阶段完全接管 tick —— AI/移动/攻击全部停止（绝对无敌），
     * 尾杀动画与死亡流程委托给 WitherTitleEventHandler.tickUndefinedFinalSkill
     * （WitherBossMixin 的 tick 注入运行时不可靠，这里用已验证生效的 LivingEntity 声明方法）
     */
    @Inject(method = "tick()V", at = @At("HEAD"), cancellable = true)
    private void witherTitle$tick(CallbackInfo ci) {
        if (!((Object) this instanceof WitherBoss wither)) return;
        if (!WitherTitleHelper.isUndefinedWither(wither)) return;

        boolean inFinalSkill;
        try {
            inFinalSkill = wither.getEntityData().get(WitherTitleHelper.UNDEFINED_FINAL_SKILL);
        } catch (Throwable t) {
            inFinalSkill = wither.getPersistentData().getBoolean(WitherTitleHelper.UNDEFINED_FINAL_SKILL_NBT_KEY);
        }
        if (!inFinalSkill) return;

        ci.cancel();
        if (!wither.level().isClientSide) {
            try {
                wither_title.WitherTitleEventHandler.tickUndefinedFinalSkill(wither);
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * die 拦截（HEAD）：无条件吞掉死亡（含尾杀阶段——真正死亡由尾杀逻辑 setRemoved 完成，
     * 防止无垠长魂之剑二次攻击走 die→dropLoot→remove 流程直接清除）
     */
    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void witherTitle$die(DamageSource source, CallbackInfo ci) {
        WitherBoss wither = witherTitle$asUndefinedWither();
        if (wither == null) return;

        try {
            if (!wither.getEntityData().get(WitherTitleHelper.UNDEFINED_FINAL_SKILL)) {
                ((LivingEntityAccessor) (Object) this).shatter$setDead(false);
                ((LivingEntityAccessor) (Object) this).shatter$setDeathTime(0);
                witherTitle$fakeHealth = 0.3999F;
                witherTitle$doTriggerLock(wither);
            }
        } catch (Throwable ignored) {
        }
        ci.cancel();
    }

    /**
     * isDeadOrDying 拦截：未定义凋灵（含尾杀阶段）强制返回 false，
     * 防止 WitherBoss.tickDeath 走原版死亡移除流程
     */
    @Inject(method = "isDeadOrDying", at = @At("HEAD"), cancellable = true)
    private void witherTitle$isDeadOrDying(CallbackInfoReturnable<Boolean> cir) {
        if (witherTitle$asUndefinedWither() != null) {
            cir.setReturnValue(false);
        }
    }

    /**
     * 统一锁血入口（实例级防递归包装）
     */
    @Unique
    private void witherTitle$doTriggerLock(WitherBoss wither) {
        if (witherTitle$isLockingHealth) return;
        witherTitle$isLockingHealth = true;
        try {
            wither_title.WitherTitleEventHandler.triggerUndefinedLockHealth(wither);
        } finally {
            witherTitle$isLockingHealth = false;
        }
    }
}
