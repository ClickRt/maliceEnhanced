package com.enchantmentSkill.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import weapon.SoulSwordBlackHoleAccessor;

/**
 * 让无垠长魂之剑释放的蓝色黑洞豁免时停。
 *
 * EndingLibrary 的时停实现里，Level.guardEntityTick 会对
 * TimeStopUtils.canMove(entity) == false 的实体直接取消 tick。
 * 黑洞是 Projectile（非 LivingEntity），canMove 对非玩家/非生物恒返回 false，
 * 所以时停时黑洞会被整体冻结（引力、伤害全部停摆）。
 *
 * 这里在 canMove 入口拦截：蓝色黑洞（soulSword$getColor() != 0）直接放行，
 * 服务端黑洞继续 tick（牵引 + 每 10 tick 伤害），客户端也继续渲染/插值。
 */
@Mixin(targets = "com/mega/endinglib/util/time/TimeStopUtils")
public abstract class TimeStopUtilsMixin {

    @Inject(method = "canMove", at = @At("HEAD"), cancellable = true, remap = false)
    private static void soulSword$blueHoleIgnoresTimeStop(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof SoulSwordBlackHoleAccessor accessor && accessor.soulSword$getColor() != 0) {
            cir.setReturnValue(true);
        }
    }
}
