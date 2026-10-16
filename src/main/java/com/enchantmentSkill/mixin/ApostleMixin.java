package com.enchantmentSkill.mixin;

import com.enchantmentSkill.positive.shatter.CustomModEnchantments;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Mixin for Goety's Apostle entity
 * This mixin bypasses the damage cap when the attacker has the Shatterer enchantment
 *
 * 普通使徒形态：
 * - 使用反射访问 Apostle 的 maxDamageTaken 字段
 * - 有破限附魔时，临时将伤害上限设为 MAX_VALUE
 * - 清除 moddedInvul 和 invulnerableTime 无敌帧
 *
 * priority = -1000 确保在对方的 hurt() HEAD 注入之前执行（对方默认 priority = 0）
 *
 * 注意：由于 Goety 是可选依赖，这个 Mixin 只有在 Goety 存在时才会被加载
 */
@Mixin(targets = "com/Polarice3/Goety/common/entities/boss/Apostle", priority = -1000, remap = false)
public abstract class ApostleMixin {

    private static Field SHATTER$maxDamageTakenField = null;
    private static Field SHATTER$moddedInvulField = null;
    private static Method SHATTER$isApollyonMethod = null;
    private static Method SHATTER$setHitCooldownMethod = null;

    // 存储原始伤害值
    private static final ThreadLocal<Float> SHATTER$originalDamage = new ThreadLocal<>();
    private static boolean SHATTER$reflectionReady = false;

    private static void shatter$ensureReflection() {
        if (SHATTER$reflectionReady) return;
        SHATTER$reflectionReady = true;
        try {
            Class<?> apostleClass = Class.forName("com.Polarice3.Goety.common.entities.boss.Apostle");
            SHATTER$maxDamageTakenField = apostleClass.getDeclaredField("maxDamageTaken");
            SHATTER$maxDamageTakenField.setAccessible(true);
            System.out.println("[Shatter-Mixin] Successfully accessed Apostle.maxDamageTaken field");

            try {
                SHATTER$moddedInvulField = apostleClass.getDeclaredField("moddedInvul");
                SHATTER$moddedInvulField.setAccessible(true);
                System.out.println("[Shatter-Mixin] Successfully accessed Apostle.moddedInvul field");
            } catch (NoSuchFieldException e) {
                System.out.println("[Shatter-Mixin] Apostle.moddedInvul field not found");
            }

            // 尝试获取 isApollyon 方法（如果存在）
            try {
                SHATTER$isApollyonMethod = apostleClass.getDeclaredMethod("isApollyon");
                SHATTER$isApollyonMethod.setAccessible(true);
                System.out.println("[Shatter-Mixin] Successfully accessed Apostle.isApollyon method");
            } catch (NoSuchMethodException e) {
                System.out.println("[Shatter-Mixin] Apostle.isApollyon method not found - Apollyon form may not be implemented");
            }

            // 尝试获取 setHitCooldown 方法（Goety 原版可能没有，是 Goety Revelation 添加的）
            try {
                SHATTER$setHitCooldownMethod = apostleClass.getDeclaredMethod("allTitlesApostle_1_20_1$setHitCooldown", int.class);
                SHATTER$setHitCooldownMethod.setAccessible(true);
                System.out.println("[Shatter-Mixin] Successfully accessed Apostle.allTitlesApostle_1_20_1$setHitCooldown method");
            } catch (NoSuchMethodException e) {
                System.out.println("[Shatter-Mixin] Apostle.allTitlesApostle_1_20_1$setHitCooldown method not found");
            }
        } catch (Exception e) {
            System.out.println("[Shatter-Mixin] Could not access Apostle fields - Goety may not be installed");
        }
    }

    /**
     * 在 hurt 方法开始时捕获原始伤害值并临时禁用伤害上限
     * priority = -1000 确保在对方的 canHurt 检查之前执行
     */
    @Inject(
        method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
        at = @At("HEAD"),
        require = 0
    )
    private void shatter$beforeHurt(net.minecraft.world.damagesource.DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        shatter$ensureReflection();
        // 存储原始伤害值
        SHATTER$originalDamage.set(amount);

        // 检查攻击者是否有破限附魔
        if (source.getDirectEntity() instanceof LivingEntity attacker) {
            ItemStack weapon = attacker.getMainHandItem();
            if (weapon.getEnchantmentLevel(CustomModEnchantments.SHATTERER.get()) > 0) {
                // Apollyon 的阶段保护由泛化有效生命通道处理；这里仅处理普通 Apostle。
                if (isApollyonForm(this)) {
                    System.out.println("[Shatter-Mixin] Apollyon form detected - skipping ordinary Apostle path");
                    return;
                }

                // 普通形态：临时设置伤害上限为最大值
                if (SHATTER$maxDamageTakenField != null) {
                    try {
                        SHATTER$maxDamageTakenField.setFloat(this, Float.MAX_VALUE);
                        System.out.println("[Shatter-Mixin] Normal Apostle - Temporarily set maxDamageTaken to MAX_VALUE");
                    } catch (IllegalAccessException e) {
                        System.err.println("[Shatter-Mixin] Failed to set maxDamageTaken: " + e.getMessage());
                    }
                }

                // 清除 moddedInvul（如果有）
                if (SHATTER$moddedInvulField != null) {
                    try {
                        SHATTER$moddedInvulField.setInt(this, 0);
                        System.out.println("[Shatter-Mixin] Normal Apostle - Cleared moddedInvul");
                    } catch (IllegalAccessException e) {
                        System.err.println("[Shatter-Mixin] Failed to clear moddedInvul: " + e.getMessage());
                    }
                }

                // 清除 hitCooldown（如果有 Goety Revelation 添加的方法）
                if (SHATTER$setHitCooldownMethod != null) {
                    try {
                        SHATTER$setHitCooldownMethod.invoke(this, 0);
                        System.out.println("[Shatter-Mixin] Normal Apostle - Cleared hitCooldown via method");
                    } catch (Exception e) {
                        System.err.println("[Shatter-Mixin] Failed to clear hitCooldown: " + e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * 在 hurt 方法返回后恢复默认值
     */
    @Inject(
        method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
        at = @At("RETURN"),
        require = 0
    )
    private void shatter$afterReturn(net.minecraft.world.damagesource.DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        shatter$ensureReflection();
        // 检查攻击者是否有破限附魔
        if (source.getDirectEntity() instanceof LivingEntity attacker) {
            ItemStack weapon = attacker.getMainHandItem();
            if (weapon.getEnchantmentLevel(CustomModEnchantments.SHATTERER.get()) > 0) {
                // 检查是否为 Apollyon 形态 - 如果是，不干预
                if (isApollyonForm(this)) {
                    SHATTER$originalDamage.remove();
                    return;
                }

                // 恢复 maxDamageTaken 为默认值
                if (SHATTER$maxDamageTakenField != null) {
                    try {
                        SHATTER$maxDamageTakenField.setFloat(this, 19.0F);
                    } catch (IllegalAccessException e) {
                        // 忽略
                    }
                }
            }
        }

        // 清除无敌帧
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.invulnerableTime > 1) {
            self.invulnerableTime = 1;
        }

        SHATTER$originalDamage.remove();
    }

    /**
     * Check if the Apostle is in Apollyon form
     * @param instance The Apostle instance
     * @return true if Apollyon form, false otherwise
     */
    private boolean isApollyonForm(Object instance) {
        if (SHATTER$isApollyonMethod != null) {
            try {
                return (Boolean) SHATTER$isApollyonMethod.invoke(instance);
            } catch (Exception e) {
                System.out.println("[Shatter-Mixin] Failed to invoke isApollyon method: " + e.getMessage());
            }
        }
        return false;
    }
}
