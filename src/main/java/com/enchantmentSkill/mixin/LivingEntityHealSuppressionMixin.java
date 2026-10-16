package com.enchantmentSkill.mixin;

import net.minecraft.world.entity.LivingEntity;
import weapon.SoulHealSuppression;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wither_title.WitherTitleHelper;
import weapon.BlueHoleHealSuppression;

/**
 * 治疗压制：被蓝色黑洞标记（受到其伤害）的实体，所有走 LivingEntity.heal()
 * 的治疗效果（脱战回血、吸血、再生等）降低 90%。
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityHealSuppressionMixin {

    @ModifyVariable(method = "heal", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float shatter$suppressHeal(float healAmount) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide) return healAmount;
        float factor = 1.0F;
        if (BlueHoleHealSuppression.isSuppressed(self)) {
            factor *= 0.1F;
        }
        factor *= SoulHealSuppression.getFactor(self);
        return healAmount * factor;
    }

    /**
     * 未定义凋灵半血治疗压制（heal() 层）：普通治疗（再生效果/吸血/自然回血等
     * 走 heal() 的恢复）削减为 50%。取消原调用后带 healPathActive 标记重新进入
     * heal()，随后的 setHealth→数据层写入凭该标记放行，不与数据层的 -80% 叠加；
     * 直接 setHealth()/SynchedEntityData 改血（如神灵金四件套每秒 +2）则走
     * WitherHealSuppressionHealthMixin 的 -80% 削减。
     */
    @Inject(method = "heal", at = @At("HEAD"), cancellable = true)
    private void wtitle$suppressNormalHeal(float amount, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide) return;
        if (WitherTitleHelper.healPathActive) return;
        if (!WitherTitleHelper.isHealSuppressed(self)) return;
        if (amount <= 0.0F) return;
        ci.cancel();
        WitherTitleHelper.healPathActive = true;
        try {
            self.heal(amount * WitherTitleHelper.HEAL_SUPPRESSION_FACTOR);
        } finally {
            WitherTitleHelper.healPathActive = false;
        }
    }
}
