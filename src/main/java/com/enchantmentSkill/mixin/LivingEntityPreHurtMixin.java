package com.enchantmentSkill.mixin;

import com.enchantmentSkill.positive.shatter.CustomModEnchantments;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Mixin(value = net.minecraft.world.entity.LivingEntity.class)
public abstract class LivingEntityPreHurtMixin {

    @Inject(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("HEAD"), cancellable = false, require = 0)
    private void shatter$preHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        try {
            Entity direct = source.getDirectEntity();
            if (!(direct instanceof LivingEntity attacker)) return;
            ItemStack weapon = attacker.getMainHandItem();
            if (weapon.getEnchantmentLevel(CustomModEnchantments.SHATTERER.get()) <= 0) return;

            Object self = (Object) this;

            // 如果是 Apostle，尝试清除所有阻挡伤害的字段
            try {
                Class<?> apostleClass = Class.forName("com.Polarice3.Goety.common.entities.boss.Apostle");
                if (apostleClass.isInstance(self)) {
                    // maxDamageTaken -> MAX
                    try {
                        Field maxField = apostleClass.getDeclaredField("maxDamageTaken");
                        maxField.setAccessible(true);
                        maxField.setFloat(self, Float.MAX_VALUE);
                    } catch (NoSuchFieldException ignored) {}

                    // moddedInvul -> 0
                    try {
                        Field modField = apostleClass.getDeclaredField("moddedInvul");
                        modField.setAccessible(true);
                        modField.setInt(self, 0);
                    } catch (NoSuchFieldException ignored) {}

                    // hitCooldown via mixin method or field
                    try {
                        Method setHit = self.getClass().getMethod("allTitlesApostle_1_20_1$setHitCooldown", int.class);
                        setHit.invoke(self, 0);
                    } catch (NoSuchMethodException nsme) {
                        try {
                            Field hitField = apostleClass.getDeclaredField("hitCooldown");
                            hitField.setAccessible(true);
                            hitField.setInt(self, 0);
                        } catch (NoSuchFieldException ignored) {}
                    }

                    // 最后清除实体自身的无敌帧
                    if (self instanceof LivingEntity living) {
                        living.invulnerableTime = 0;
                        living.hurtTime = 0;
                    }
                }
            } catch (ClassNotFoundException ignored) {
                // Goety 未安装或类名不同，忽略
            }
        } catch (Throwable t) {
            // 不要影响主逻辑
            System.err.println("[Shatter] preHurt mixin error: " + t.getMessage());
        }
    }
}
