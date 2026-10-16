package com.enchantmentSkill.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wither_title.WitherTitleHelper;

/**
 * 为未定义凋灵提供动态流动渐变存活名 "? 凋灵 ?"。
 * getDisplayName() 定义在 Entity 中，WitherBoss 未重写，
 * 因此必须在 @Mixin(Entity.class) 中注入，不能只在 @Mixin(WitherBoss.class) 中注入。
 */
@Mixin(Entity.class)
public abstract class EntityGetDisplayNameMixin {

    @Inject(method = "getDisplayName", at = @At("HEAD"), cancellable = true)
    private void extended$onGetDisplayName(CallbackInfoReturnable<Component> cir) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof WitherBoss wither)) return;
        if (!WitherTitleHelper.isUndefinedWither(wither)) return;

        cir.setReturnValue(WitherTitleHelper.getUndefinedDisplayName());
    }
}
