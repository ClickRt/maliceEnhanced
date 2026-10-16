package com.enchantmentSkill.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wither_title.BallLightningEntity;
import wither_title.WitherTitleEventHandler;
import wither_title.WitherTitleHelper;

@Mixin(WitherSkull.class)
public abstract class WitherSkullMixin {

    @Inject(method = "defineSynchedData", at = @At("RETURN"))
    private void witherTitle$defineSeekingData(CallbackInfo ci) {
        WitherSkull skull = (WitherSkull) (Object) this;
        if (skull.getClass() != WitherSkull.class) return;
        skull.getEntityData().define(WitherTitleHelper.SEEKING_SKULL_DATA, false);
        skull.getEntityData().define(WitherTitleHelper.SEEKING_TARGET_ID, 0);
    }

    @Inject(method = "onHit(Lnet/minecraft/world/phys/HitResult;)V", at = @At("HEAD"))
    private void witherTitle$markNaturalSkullHit(HitResult result, CallbackInfo ci) {
        WitherSkull skull = (WitherSkull) (Object) this;
        if (skull.level().isClientSide) return;
        if (result.getType() == HitResult.Type.MISS) return;
        skull.getPersistentData().putBoolean(WitherTitleHelper.SKULL_NATURAL_HIT_KEY, true);
    }

    @Inject(method = "onHit(Lnet/minecraft/world/phys/HitResult;)V", at = @At("TAIL"))
    private void witherTitle$onSkullHit(HitResult result, CallbackInfo ci) {
        WitherSkull skull = (WitherSkull) (Object) this;
        if (skull.level().isClientSide) return;
        if (!(skull.getOwner() instanceof WitherBoss wither)) return;
        int title = WitherTitleHelper.getTitleId(wither);
        boolean undefined = WitherTitleHelper.isUndefinedWither(wither);
        if (undefined) {
            WitherTitleEventHandler.completeUndefinedSkullHits(wither);
        }
        if ((title == 12 || undefined) && skull.level().getRandom().nextFloat() < 0.6F) {
            skull.level().addFreshEntity(new BallLightningEntity(
                    skull.level(), skull.getX(), skull.getY(), skull.getZ(), wither.getUUID()));
        }
    }

    @ModifyArg(method = "onHit(Lnet/minecraft/world/phys/HitResult;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;explode(Lnet/minecraft/world/entity/Entity;DDDFZLnet/minecraft/world/level/Level$ExplosionInteraction;)Lnet/minecraft/world/level/Explosion;"),
            index = 4)
    private float witherTitle$boostExplosion(float radius) {
        WitherSkull skull = (WitherSkull) (Object) this;
        if (!(skull.getOwner() instanceof WitherBoss wither)) return radius;
        int title = WitherTitleHelper.getTitleId(wither);
        boolean undefined = WitherTitleHelper.isUndefinedWither(wither);
        if (title == 13 || undefined) {
            return radius + (skull.isDangerous() ? 1.0F : 2.0F);
        }
        return radius;
    }

    @Inject(method = "onHitEntity(Lnet/minecraft/world/phys/EntityHitResult;)V", at = @At("TAIL"))
    private void witherTitle$onSkullHitEntity(EntityHitResult result, CallbackInfo ci) {
        WitherSkull skull = (WitherSkull) (Object) this;
        if (skull.level().isClientSide) return;
        if (!(skull.getOwner() instanceof WitherBoss wither)) return;
        if (!(result.getEntity() instanceof LivingEntity living)) return;
        int title = WitherTitleHelper.getTitleId(wither);
        boolean undefined = WitherTitleHelper.isUndefinedWither(wither);
        if (title == 13 || undefined) {
            living.setSecondsOnFire(8);
        }
        // 任意凋灵之首命中：附加灵魂伤害（damage_soul，来源该凋灵）
        // 放在 onHitEntity TAIL 而非 LivingHurtEvent —— 直写扣血，
        // 目标免疫爆炸/免疫伤害类型/拔剑无敌/事件被取消均不影响（如二轮傀儡）
        if (!living.is(wither)) {
            float maxHp = living.getMaxHealth();
            float soul;
            if (undefined && maxHp >= 5000.0F) {
                soul = maxHp * 0.25F + 500.0F;
            } else if (undefined && maxHp >= 1000.0F) {
                soul = maxHp * 0.05F + 100.0F;
            } else {
                soul = maxHp * 0.01F + 4.0F;
            }
            if (undefined) {
                WitherTitleEventHandler.finishUndefinedSkullHit(wither, living, soul);
            } else {
                weapon.SoulSwordEventHandler.applySoulDamage(living, wither, soul);
            }
        }
        // 未定义凋灵半血后：被头颅直击的目标附加治疗压制（普通回血 -50%，setHealth 直写 -80%，2s）
        if (undefined && WitherTitleHelper.isAtOrBelowHalfHealth(wither)) {
            WitherTitleHelper.markHealSuppression(living);
        }
    }

    @Inject(method = "onHitEntity(Lnet/minecraft/world/phys/EntityHitResult;)V", at = @At("HEAD"))
    private void witherTitle$beforeSkullHitEntity(EntityHitResult result, CallbackInfo ci) {
        WitherSkull skull = (WitherSkull) (Object) this;
        if (skull.level().isClientSide) return;
        if (!(skull.getOwner() instanceof WitherBoss wither)
                || !WitherTitleHelper.isUndefinedWither(wither)) return;
        if (result.getEntity() instanceof LivingEntity living) {
            WitherTitleEventHandler.beginUndefinedSkullHit(wither, living);
        }
    }
}
