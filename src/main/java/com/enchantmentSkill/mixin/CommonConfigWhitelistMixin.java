package com.enchantmentSkill.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * RevelationFix 的"天启"（末日终结）阶段会禁用并没收非白名单物品，
 * 导致无垠长魂之剑在尾杀阶段无法右键格挡。
 * 白名单标签 revelationfix:no_stealing 已覆盖无垠长魂之剑与星魂之弓，
 * 这里再加一道硬保险，确保 CommonConfig.inWhitelist(soul_sword/soul_bow)
 * 恒为 true（无论标签是否加载成功）。
 */
@Mixin(targets = "com/mega/revelationfix/common/config/CommonConfig", remap = false)
public abstract class CommonConfigWhitelistMixin {

    @Inject(method = "inWhitelist(Lnet/minecraft/world/item/Item;)Z",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void soulSword$whitelist(Item item, CallbackInfoReturnable<Boolean> cir) {
        if (item == null) return;
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        if ("malice_enhanced:soul_sword".equals(id) || "malice_enhanced:soul_bow".equals(id)) {
            cir.setReturnValue(true);
        }
    }
}
