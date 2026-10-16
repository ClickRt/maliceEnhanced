package com.enchantmentSkill.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wither_title.WitherTitleEventHandler;
import wither_title.WitherTitleHelper;

import java.util.List;

/**
 * WitherBoss 专属注入：defineSynchedData / 构造 / 骷髅弹强化
 * 锁血防护与尾杀（getHealth/setHealth/hurt/die/heal/remove/tick）在
 * LivingEntityMixin / EntityMixin 中实现——这些方法是 LivingEntity/Entity 声明的，
 * WitherBoss 未重写或运行时注入不可靠。
 */
@Mixin(WitherBoss.class)
public abstract class WitherBossMixin {

    @Unique
    private static final ThreadLocal<LivingEntity> witherTitle$TARGET = new ThreadLocal<>();

    /**
     * 注入 defineSynchedData（WitherBoss 重写了该方法），注册自定义数据同步器
     */
    @Inject(method = "defineSynchedData", at = @At("RETURN"))
    private void witherTitle$defineSynchedData(CallbackInfo ci) {
        WitherBoss wither = (WitherBoss) (Object) this;
        wither.getEntityData().define(WitherTitleHelper.UNDEFINED_DATA, false);
        wither.getEntityData().define(WitherTitleHelper.UNDEFINED_FINAL_SKILL, false);
        wither.getEntityData().define(WitherTitleHelper.UNDEFINED_DEATH_TIME, WitherTitleHelper.UNDEFINED_FINAL_TOTAL_TICKS);
        wither.getEntityData().define(WitherTitleHelper.DIVE_GHOST, false);
    }

    @Inject(method = "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;)V", at = @At("TAIL"))
    private void witherTitle$assignTitle(EntityType<? extends WitherBoss> type, Level level, CallbackInfo ci) {
        if (level.isClientSide) return;
        WitherBoss wither = (WitherBoss) (Object) this;
        if (WitherTitleHelper.isUndefinedWither(wither)) return;
        if (WitherTitleHelper.hasTitle(wither)) return;
        // 只随机头衔 id，不在这里设置 customName ——
        // 名字统一延迟到 EntityJoinLevelEvent 命名（此时未定义凋灵已被 setupUndefinedWither
        // 清名并打上 UNDEFINED_DATA，保证客户端头顶永远显示渐变名而非头衔名）
        int id = WitherTitleHelper.MIN_TITLE + wither.level().getRandom().nextInt(WitherTitleHelper.MAX_TITLE - WitherTitleHelper.MIN_TITLE + 1);
        WitherTitleHelper.setTitleId(wither, id);
    }

    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void witherTitle$allowUndeadTargets(CallbackInfo ci) {
        WitherBoss wither = (WitherBoss) (Object) this;
        wither.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(wither, LivingEntity.class, 0, false, false,
                living -> WitherTitleHelper.isUndefinedWither(wither)
                        && living != null
                        && living.attackable()
                        && !(living instanceof WitherBoss)
                        && !WitherTitleHelper.isWitherServant(living, wither.getUUID())));
    }

    @Redirect(method = "customServerAiStep()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getNearbyEntities(Ljava/lang/Class;Lnet/minecraft/world/entity/ai/targeting/TargetingConditions;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private <T extends LivingEntity> List<T> witherTitle$undeadSideTargets(Level level, Class<T> clazz,
                                                                           TargetingConditions conditions,
                                                                           LivingEntity entity, AABB box) {
        WitherBoss wither = (WitherBoss) (Object) this;
        if (WitherTitleHelper.isUndefinedWither(wither)) {
            conditions = TargetingConditions.forCombat().range(20.0D).selector(LivingEntity::attackable);
        }
        return level.getNearbyEntities(clazz, conditions, entity, box);
    }

    /**
     * 非尾杀阶段：每 tick 更新 BOSS 血条名称（动态渐变需要服务端每 tick setName 广播到客户端）。
     * 尾杀阶段 LivingEntity.tick 被拦截、customServerAiStep 不会跑，
     * 名字更新由 WitherTitleEventHandler.tickUndefinedFinalSkill 接管。
     */
    @Inject(method = "customServerAiStep()V", at = @At("TAIL"))
    private void witherTitle$refreshBossBarName(CallbackInfo ci) {
        WitherBoss wither = (WitherBoss) (Object) this;
        if (!WitherTitleHelper.isUndefinedWither(wither)) return;
        ((WitherBossBossBarAccessor) wither).shatter$getBossEvent()
                .setName(WitherTitleHelper.getUndefinedDisplayName());
    }

    /**
     * 俯冲必须在原版 AI 之后覆盖位移，否则 customServerAiStep 会把速度改回去。
     */
    @Inject(method = "aiStep()V", at = @At("TAIL"))
    private void witherTitle$tickUndefinedDive(CallbackInfo ci) {
        WitherBoss wither = (WitherBoss) (Object) this;
        if (wither.level().isClientSide) return;
        if (!WitherTitleHelper.isUndefinedWither(wither)) return;
        WitherTitleEventHandler.tickUndefinedPostAi(wither);
    }

    @Inject(method = "performRangedAttack(ILnet/minecraft/world/entity/LivingEntity;)V", at = @At("HEAD"), cancellable = true)
    private void witherTitle$suppressSkulls(int head, LivingEntity target, CallbackInfo ci) {
        WitherBoss wither = (WitherBoss) (Object) this;
        if (wither_title.WitherTitleEventHandler.isSkullFireSuppressed(wither)) {
            ci.cancel();
            return;
        }
        if (wither_title.WitherTitleEventHandler.isUndefinedOutOfCombat(wither)
                && wither.level().getRandom().nextFloat() < 0.99F) {
            ci.cancel();
        }
    }

    @Inject(method = "performRangedAttack(IDDDZ)V", at = @At("HEAD"), cancellable = true)
    private void witherTitle$suppressSkullSpawn(int head, double x, double y, double z, boolean dangerous, CallbackInfo ci) {
        WitherBoss wither = (WitherBoss) (Object) this;
        if (wither_title.WitherTitleEventHandler.isSkullFireSuppressed(wither)) {
            ci.cancel();
        }
    }

    @Inject(method = "performRangedAttack(ILnet/minecraft/world/entity/LivingEntity;)V", at = @At("HEAD"))
    private void witherTitle$captureTarget(int head, LivingEntity target, CallbackInfo ci) {
        witherTitle$TARGET.set(target);
    }

    @Inject(method = "performRangedAttack(ILnet/minecraft/world/entity/LivingEntity;)V", at = @At("TAIL"))
    private void witherTitle$releaseTarget(int head, LivingEntity target, CallbackInfo ci) {
        witherTitle$TARGET.remove();
    }

    @Redirect(method = "performRangedAttack(IDDDZ)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean witherTitle$onSkullSpawned(Level level, Entity entity) {
        if (entity instanceof WitherSkull skull) {
            WitherBoss wither = (WitherBoss) (Object) this;
            int title = WitherTitleHelper.getTitleId(wither);
            boolean undefined = WitherTitleHelper.isUndefinedWither(wither);

            boolean seeking = false;
            boolean vanillaSkull = WitherTitleHelper.isVanillaWitherSkull(skull);
            if (undefined && wither.level().getRandom().nextFloat() < WitherTitleHelper.SEEKING_SKULL_CHANCE) {
                seeking = true;
                skull.getPersistentData().putBoolean(WitherTitleHelper.SEEKING_SKULL_KEY, true);
                if (vanillaSkull) skull.getEntityData().set(WitherTitleHelper.SEEKING_SKULL_DATA, true);
                LivingEntity seekTarget = witherTitle$TARGET.get();
                if (seekTarget == null) seekTarget = wither.getTarget();
                if (seekTarget != null) {
                    skull.getPersistentData().putUUID(WitherTitleHelper.SKULL_TARGET_KEY, seekTarget.getUUID());
                    if (vanillaSkull) skull.getEntityData().set(WitherTitleHelper.SEEKING_TARGET_ID, seekTarget.getId());
                    Vec3 targetVec = seekTarget.position().add(0, seekTarget.getBbHeight() * 0.5D, 0)
                            .subtract(skull.position());
                    if (targetVec.lengthSqr() > 1.0E-4D) {
                        Vec3 dir = targetVec.normalize().scale(0.1D);
                        skull.xPower = dir.x;
                        skull.yPower = dir.y;
                        skull.zPower = dir.z;
                        if (skull.getDeltaMovement().lengthSqr() < 1.0E-4D) {
                            skull.setDeltaMovement(dir);
                        }
                    }
                }
            }
            if (!seeking && (title == 7 || undefined)) {
                skull.xPower *= 1.5D;
                skull.yPower *= 1.5D;
                skull.zPower *= 1.5D;
                LivingEntity target = witherTitle$TARGET.get();
                if (target == null) target = wither.getTarget();
                if (target != null) {
                    skull.getPersistentData().putUUID(WitherTitleHelper.SKULL_TARGET_KEY, target.getUUID());
                    if (vanillaSkull) skull.getEntityData().set(WitherTitleHelper.SEEKING_TARGET_ID, target.getId());
                }
            }
            if ((title == 8 || undefined) && !skull.isDangerous() && wither.level().getRandom().nextFloat() < 0.5F) {
                skull.getPersistentData().putBoolean(WitherTitleHelper.FLAME_SKULL_KEY, true);
            }
            if ((title == 9 || undefined) && skull.isDangerous()
                    && wither.level().getRandom().nextFloat() < 0.5F) {
                skull.getPersistentData().putBoolean(WitherTitleHelper.SINGULARITY_SKULL_KEY, true);
            }
        }
        return level.addFreshEntity(entity);
    }

}
