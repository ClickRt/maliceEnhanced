package com.enchantmentSkill.mixin;

import net.minecraft.world.entity.LightningBolt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wither_title.WitherTitleHelper;

@Mixin(LightningBolt.class)
public abstract class LightningBoltColorMixin {

    /**
     * Entity 的构造流程在不同 Forge/整合环境中对 defineSynchedData 的调用时机
     * 可能不同。构造结束后补注册一次，避免后续 set 找不到 DataItem。
     */
    @Inject(method = "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;)V", at = @At("TAIL"))
    private void malice$defineLightningColor(net.minecraft.world.entity.EntityType<? extends LightningBolt> type,
                                             net.minecraft.world.level.Level level, CallbackInfo ci) {
        LightningBolt lightning = (LightningBolt) (Object) this;
        try {
            if (!lightning.getEntityData().hasItem(WitherTitleHelper.LIGHTNING_COLOR)) {
                lightning.getEntityData().define(WitherTitleHelper.LIGHTNING_COLOR, 0);
            }
        } catch (Throwable ignored) {
            // 颜色是可选视觉效果，失败时由事件和渲染器回退到原版闪电。
        }
    }
}
