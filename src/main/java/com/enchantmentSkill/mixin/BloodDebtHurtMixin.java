package com.enchantmentSkill.mixin;

import com.enchantmentSkill.positive.bloodDebt.BloodDebtEnchantments;
import com.enchantmentSkill.positive.bloodDebt.BloodDebtEventHandler;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 血债血偿 Mixin — 在 hurt() 入口注入倍率，早于所有限伤逻辑。
 * priority = -2000 确保在任何其他 hurt() HEAD 注入之前执行。
 */
@Mixin(value = LivingEntity.class, priority = -2000)
public abstract class BloodDebtHurtMixin {

    @Unique
    private static final ThreadLocal<Boolean> APPLYING = ThreadLocal.withInitial(() -> false);

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true, require = 0)
    private void bloodDebt$preHurt(DamageSource source, float amount,
                                    CallbackInfoReturnable<Boolean> cir) {
        if (APPLYING.get()) return;

        try {
            LivingEntity attacker = null;
            Entity direct = source.getDirectEntity();
            if (direct instanceof LivingEntity living) {
                attacker = living;
            } else if (direct instanceof Projectile projectile
                    && projectile.getOwner() instanceof LivingEntity owner) {
                attacker = owner;
            }
            if (attacker == null) return;

            ItemStack weapon = attacker.getMainHandItem();
            int level = weapon.getEnchantmentLevel(BloodDebtEnchantments.BLOOD_DEBT.get());
            if (level <= 0) return;

            float maxHealth = attacker.getMaxHealth();
            float currentHealth = attacker.getHealth();

            float add = Math.min((int) (maxHealth / currentHealth), 9) * 0.2F;
            float multiplier = 1.0F + add;
            if (multiplier <= 1.0F) return;

            float newAmount = amount * multiplier;
            System.out.printf("[BloodDebt-Mixin] HP=%.1f/%.1f ratio=%d add=%.1f mult=%.2f | %.1f -> %.1f%n",
                    currentHealth, maxHealth, (int) (maxHealth / currentHealth), add, multiplier,
                    amount, newAmount);

            BloodDebtEventHandler.storeMultiplier((LivingEntity) (Object) this, multiplier);
            BloodDebtEventHandler.MIXIN_APPLIED.set(true);

            APPLYING.set(true);
            try {
                cir.setReturnValue(((LivingEntity) (Object) this).hurt(source, newAmount));
            } finally {
                APPLYING.set(false);
                BloodDebtEventHandler.MIXIN_APPLIED.set(false);
            }
        } catch (Throwable t) {
            System.err.println("[BloodDebt] Mixin error: " + t.getMessage());
        }
    }
}
