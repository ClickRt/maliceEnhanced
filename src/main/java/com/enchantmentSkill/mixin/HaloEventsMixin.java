package com.enchantmentSkill.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import weapon.SoulSwordItem;

/**
 * RevelationFix 的终末之环 (OdamaneHalo) 在 LivingDamageEvent(LOWEST) 里把每次受伤封顶 20。
 * 持有 soul_sword 攻击时跳过这个封顶，使虚空伤害能正常击杀环持有者。
 */
@Mixin(targets = "com/mega/revelationfix/common/odamane/common/HaloEvents", remap = false)
public abstract class HaloEventsMixin {

    @Inject(method = "onLivingDamage(Lnet/minecraftforge/event/entity/living/LivingDamageEvent;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void soulSword$bypassHaloCap(LivingDamageEvent event, CallbackInfo ci) {
        DamageSource source = event.getSource();
        if (source == null) return;
        if (!(source.getEntity() instanceof Player attacker)) return;
        ItemStack main = attacker.getMainHandItem();
        ItemStack off = attacker.getOffhandItem();
        if (main.getItem() instanceof SoulSwordItem || off.getItem() instanceof SoulSwordItem) {
            ci.cancel();
        }
    }
}
