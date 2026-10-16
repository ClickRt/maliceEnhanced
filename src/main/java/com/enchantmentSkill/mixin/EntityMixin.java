package com.enchantmentSkill.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.projectile.WitherSkull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import weapon.StarFantasyHadesBan;
import wither_title.WitherTitleEventHandler;
import wither_title.WitherTitleHelper;

/**
 * Entity.remove 拦截：无条件阻止未定义凋灵被 remove（含尾杀阶段二次攻击清除）
 * 尾杀结束的真正移除走 setRemoved()，不经过这里
 */
@Mixin(Entity.class)
public abstract class EntityMixin {

    @Inject(method = "isPickable", at = @At("HEAD"), cancellable = true)
    private void malice$hadesPickable(CallbackInfoReturnable<Boolean> cir) {
        if (StarFantasyHadesBan.isPageantHades((Entity) (Object) this)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void malice$hadesHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (StarFantasyHadesBan.shouldAcceptHit(source) && StarFantasyHadesBan.tryBanAndDiscard(self)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void witherTitle$remove(Entity.RemovalReason reason, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (reason == Entity.RemovalReason.DISCARDED && self instanceof WitherSkull skull) {
            WitherTitleEventHandler.onWitherSkullDiscarded(skull);
        }
        if (!(self instanceof WitherBoss wither)) return;
        if (!WitherTitleHelper.isUndefinedWither(wither)) return;
        ci.cancel();
    }
}
