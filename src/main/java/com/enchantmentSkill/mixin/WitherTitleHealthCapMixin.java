package com.enchantmentSkill.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wither_title.WitherTitleEventHandler;
import wither_title.WitherTitleHelper;
import weapon.starcrystal.StarCoronaCrystalEvents;
import weapon.starcrystal.StarCoronaCurios;

/**
 * 未定义凋灵与灵魂之心的 20 点限伤数据层实现（与 SoulSwordHurtDefendMixin 同思路）。
 *
 * LivingEntity.setHealth 与任何 mod 的直写最终都汇聚到 SynchedEntityData.set 的 HEALTH 写入，
 * 在这里夹紧意味着限伤无法被"绕过 hurt() 直接写血量"的路径突破
 * （外部 Mod 的 health-delta、置 1 血/秒杀类效果、本工程自己的直写锁血等）。
 *
 * 语义：未定义凋灵仍按原有数据层规则处理；灵魂之心玩家则先在伤害事件入口
 * 将本次原始伤害夹到 20 点，再进入头衔减伤。这里负责直写血量兜底、同 tick
 * 预算和受击无敌窗口，防止 setHealth/SynchedEntityData 路径绕过规则。
 * 成功扣血后开启 30 tick 自定义无敌窗口（soulHurtDefend 同思路），窗口内（激活当 tick
 * 除外，走预算分摊）后续扣血写入全部拦截。
 * 尾杀阶段不介入，锁血由 LivingEntityMixin 与 tickUndefinedFinalSkill 负责。
 */
@Mixin(SynchedEntityData.class)
public abstract class WitherTitleHealthCapMixin {

    /** 防止改写时的重入（内层调用直接落到原版逻辑） */
    @Unique
    private boolean witherTitle$rewritingHealth = false;

    @Inject(
            method = "set(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private <T> void witherTitle$capHealthDrop(EntityDataAccessor<T> accessor, T value, boolean force, CallbackInfo ci) {
        // 热路径：每实体每次同步数据写入都会经过这里，最便宜的判断放最前
        if (witherTitle$rewritingHealth) return;
        if (!accessor.equals(LivingEntityAccessor.shatter$getDataHealthId())) return;
        if (!(value instanceof Float newValue)) return;
        net.minecraft.world.entity.Entity entity = ((SynchedEntityDataAccessor) this).shatter$getEntity();
        if (entity == null) return;
        if (entity.level().isClientSide) return;

        if (entity instanceof Player player) {
            SynchedEntityData self = (SynchedEntityData) (Object) this;
            EntityDataAccessor<Float> healthId = LivingEntityAccessor.shatter$getDataHealthId();
            float current = self.get(healthId);
            if (!(newValue < current)) return;

            if (!StarCoronaCurios.hasSoulHeart(player)) {
                // Keep the existing title-only direct-write mitigation for players
                // without a Soul Heart. Normal hurt() writes already settled their
                // reductions in LivingHurtEvent and must not be reduced twice.
                if (StarCoronaCrystalEvents.isSoulHeartHurtInProgress(player)) return;
                float directDamage = StarCoronaCrystalEvents.reduceDirectHealthDamage(player, current - newValue);
                if (directDamage >= current - newValue) return;
                witherTitle$rewritingHealth = true;
                try {
                    self.set(healthId, current - directDamage, force);
                } finally {
                    witherTitle$rewritingHealth = false;
                }
                ci.cancel();
                return;
            }

            if (StarCoronaCrystalEvents.isSoulHeartHurtDefendBlocking(player)) {
                ci.cancel();
                return;
            }

            float damage = current - newValue;
            boolean insideHurt = StarCoronaCrystalEvents.isSoulHeartHurtInProgress(player);
            if (insideHurt) {
                // LivingHurtEvent already capped the raw hit before applying
                // title/ascension mitigation. Keep this as a final data-layer
                // fallback for later modifiers that may have raised the amount.
                damage = StarCoronaCrystalEvents.capSoulHeartDamageBeforeReduction(player, damage);
            } else {
                // Direct setHealth/SynchedEntityData writes have no hurt event;
                // apply the same raw cap first, then the direct-write reduction.
                damage = StarCoronaCrystalEvents.reduceDirectHealthDamage(player, damage);
            }
            if (damage <= 0.0F) {
                ci.cancel();
                return;
            }
            float allowed = Math.min(damage, StarCoronaCrystalEvents.getSoulHeartRemainingTickBudget(player));
            if (allowed >= damage) {
                StarCoronaCrystalEvents.consumeSoulHeartTickBudget(player, damage);
                StarCoronaCrystalEvents.activateSoulHeartHurtDefend(player);
                return;
            }
            if (allowed > 0.0F) {
                witherTitle$rewritingHealth = true;
                try {
                    self.set(healthId, current - allowed, force);
                } finally {
                    witherTitle$rewritingHealth = false;
                }
                StarCoronaCrystalEvents.consumeSoulHeartTickBudget(player, allowed);
                StarCoronaCrystalEvents.activateSoulHeartHurtDefend(player);
            }
            ci.cancel();
            return;
        }

        if (!(entity instanceof WitherBoss wither)) return;
        if (!WitherTitleHelper.isUndefinedWither(wither)) return;

        boolean inFinalSkill;
        try {
            inFinalSkill = wither.getEntityData().get(WitherTitleHelper.UNDEFINED_FINAL_SKILL);
        } catch (Throwable t) {
            inFinalSkill = wither.getPersistentData().getBoolean(WitherTitleHelper.UNDEFINED_FINAL_SKILL_NBT_KEY);
        }
        if (inFinalSkill) return;
        if (WitherTitleHelper.isDisplaySyncActive()) return;

        SynchedEntityData self = (SynchedEntityData) (Object) this;
        EntityDataAccessor<Float> healthId = LivingEntityAccessor.shatter$getDataHealthId();
        float current = self.get(healthId);

        if (!(newValue < current)) return;

        float damage = current - newValue;

        if (!WitherTitleHelper.isLegitWrite()) {
            // 非法直写：预算 + 无敌 + 适应都在 applyIllicitWriteAndRestore 里，不走满额扣真血
            float hidden = WitherTitleHelper.applyIllicitWriteAndRestore(wither, damage);
            if (hidden <= 0.5F) {
                WitherTitleEventHandler.triggerUndefinedLockHealth(wither);
            }
            ci.cancel();
            return;
        }

        if (WitherTitleHelper.isHurtDefendBlocking(wither)) {
            ci.cancel();
            return;
        }

        float allowed = Math.min(damage, WitherTitleHelper.getRemainingTickBudget(wither));
        if (allowed >= damage) {
            WitherTitleHelper.consumeTickBudget(wither, damage);
            WitherTitleHelper.applyHiddenDamage(wither, damage);
            WitherTitleHelper.activateHurtDefend(wither);
            return;
        }
        if (allowed > 0.0F) {
            witherTitle$rewritingHealth = true;
            try {
                self.set(healthId, current - allowed, force);
            } finally {
                witherTitle$rewritingHealth = false;
            }
            WitherTitleHelper.consumeTickBudget(wither, allowed);
            WitherTitleHelper.applyHiddenDamage(wither, allowed);
            WitherTitleHelper.activateHurtDefend(wither);
        }
        ci.cancel();
    }
}
