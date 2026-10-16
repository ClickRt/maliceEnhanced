package com.enchantmentSkill.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

/**
 * 防止下界亚波伦在"炼狱结界"死亡演出未结束时被提前清除：
 *  - Goety 的 Apostle.hurt 对"无来源虚空伤害"直接 discard()（结界烧伤类型里含虚空伤害）；
 *  - Goety 的 Apostle.aiStep 在击杀玩家后 10 秒计时归零会 remove(DISCARDED)。
 * 这些都会让 Boss 在结界动画期间"清除自身"。
 * 这里在 remove(KILLED/DISCARDED) 入口拦截：只要 DeathPerformance.leftTime > 0
 * （结界演出进行中）就取消移除；结界正常结束（leftTime<=0）后的飞升死亡不受影响。
 * 注意：不能写 remap=false，否则 remove 不会映射成 SRG 名（生产环境失效）。
 */
@Mixin(targets = "com/Polarice3/Goety/common/entities/boss/Apostle")
public abstract class ApostleBarrierRemoveGuardMixin {

    @Unique
    private static Method shatter$getLeftTime;

    @Inject(method = "remove(Lnet/minecraft/world/entity/Entity$RemovalReason;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void soulSword$blockPrematureRemove(Entity.RemovalReason reason, CallbackInfo ci) {
        if (reason != Entity.RemovalReason.KILLED && reason != Entity.RemovalReason.DISCARDED) {
            return;
        }
        if (isNetherBarrierRunning((LivingEntity) (Object) this)) {
            ci.cancel();
        }
    }

    /** 反射判断：是否下界亚波伦且死亡演出（结界）仍在进行 */
    @Unique
    private static boolean isNetherBarrierRunning(LivingEntity self) {
        try {
            Class<?> apostleClass = Class.forName("com.Polarice3.Goety.common.entities.boss.Apostle");
            if (!apostleClass.isInstance(self)) return false;
            if (shatter$getLeftTime == null) {
                shatter$getLeftTime = Class.forName("com.mega.revelationfix.common.apollyon.common.DeathPerformance")
                        .getMethod("getLeftTime", apostleClass);
            }
            Object left = shatter$getLeftTime.invoke(null, self);
            return left instanceof Integer i && i > 0;
        } catch (Throwable t) {
            return false;
        }
    }
}
