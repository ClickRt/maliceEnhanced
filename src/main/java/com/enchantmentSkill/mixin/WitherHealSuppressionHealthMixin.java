package com.enchantmentSkill.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wither_title.WitherTitleHelper;

/**
 * 未定义凋灵「半血治疗压制」的数据层实现（与 WitherTitleHealthCapMixin 同思路，方向相反）。
 *
 * 针对"绕过 heal() 的直写改血"——直接 setHealth()（如神灵金四件套每秒 +2）或
 * SynchedEntityData 血量直写——在写入汇聚点 SynchedEntityData.set(DATA_HEALTH_ID)
 * 对"血量增加"削减 80%（只保留 20%）。扣血/等值写入一律放行。
 *
 * 走 heal() 的普通治疗（再生/吸血/自然回血）不经过这里：它们已在
 * LivingEntityHealSuppressionMixin 层被削减 50%，其内部 setHealth 写入凭
 * WitherTitleHelper.healPathActive 标记放行，避免两层叠加。
 *
 * 只在实体被 markHealSuppression 标记后（未定义凋灵半血攻击命中的目标）生效，
 * 未标记实体走一次极便宜的 map 判断即返回，热路径开销可忽略。
 */
@Mixin(SynchedEntityData.class)
public abstract class WitherHealSuppressionHealthMixin {

    /** 改写血量时的重入保护：内层 set 直接落到原版逻辑 */
    @Unique
    private boolean wtitle$rewritingHeal = false;

    @Inject(
            method = "set(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private <T> void wtitle$suppressHeal(EntityDataAccessor<T> accessor, T value, boolean force, CallbackInfo ci) {
        if (wtitle$rewritingHeal) return;
        if (!accessor.equals(LivingEntityAccessor.shatter$getDataHealthId())) return;
        if (!(value instanceof Float newValue)) return;
        if (WitherTitleHelper.healPathActive) return;
        if (!(((SynchedEntityDataAccessor) this).shatter$getEntity() instanceof LivingEntity living)) return;
        if (living.level().isClientSide) return;

        float factor = WitherTitleHelper.getHealSuppressionDataFactor(living);
        if (factor >= 1.0F) return;

        SynchedEntityData self = (SynchedEntityData) (Object) this;
        EntityDataAccessor<Float> healthId = LivingEntityAccessor.shatter$getDataHealthId();
        float current = self.get(healthId);

        // 只削减回血（血量增加）；扣血/等值放行，NaN 一并挡掉
        if (!(newValue > current)) return;

        float suppressed = current + (newValue - current) * factor;
        if (suppressed > living.getMaxHealth()) suppressed = living.getMaxHealth();

        wtitle$rewritingHeal = true;
        try {
            self.set(healthId, suppressed, force);
        } finally {
            wtitle$rewritingHeal = false;
        }
        ci.cancel();
    }
}
