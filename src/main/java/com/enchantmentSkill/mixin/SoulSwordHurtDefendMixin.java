package com.enchantmentSkill.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import weapon.SoulSwordEventHandler;

/**
 * 无垠长魂之剑复活后的自定义受击无敌帧（soulHurtDefend）数据层防护。
 * 与终末之环的 SynchedEntityDataMixin 同思路：无敌窗口期间，
 * 拦截 HEALTH 数据的"扣血写入"（包括绕过伤害事件直写血量的路径），
 * 只允许回血。事件层由 SoulSwordEventHandler（LivingAttack/LivingHurt/LivingDamage 取消或归零）负责。
 */
@Mixin(SynchedEntityData.class)
public abstract class SoulSwordHurtDefendMixin {

    @Inject(
            method = "set(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;Z)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/syncher/SynchedEntityData$DataItem;setValue(Ljava/lang/Object;)V",
                    shift = At.Shift.BEFORE),
            cancellable = true,
            require = 0
    )
    private <T> void soulSword$hurtDefendBlockHealth(EntityDataAccessor<T> accessor, T value, boolean force, CallbackInfo ci) {
        if (!accessor.equals(LivingEntityAccessor.shatter$getDataHealthId()) || !(value instanceof Float fValue)) return;
        if (!(((SynchedEntityDataAccessor) this).shatter$getEntity() instanceof Player player)) return;
        if (!SoulSwordEventHandler.isSoulHurtDefendActive(player)) return;
        float lastHealth = player.getHealth();
        // 无敌窗口内禁止任何扣血写入（回血不受影响）
        if (lastHealth - fValue >= 0.0F) {
            ci.cancel();
        }
    }
}
