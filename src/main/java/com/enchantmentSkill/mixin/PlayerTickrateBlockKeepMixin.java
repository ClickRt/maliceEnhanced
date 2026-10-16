package com.enchantmentSkill.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import weapon.SoulBowItem;
import weapon.SoulSwordItem;

import java.lang.reflect.Method;

/**
 * 末日终结（天启）阶段 RevelationFix 的 PlayerTickrateExecutor.playerTick
 * 每 tick 无条件 stopUsingItem()，把长按使用全部掐断（与白名单/冷却无关），
 * 导致无垠长魂之剑右键格挡无法触发。这里把 stopUsingItem 调用重定向：
 * 正在使用的是白名单物品（含无垠长魂之剑）时跳过打断；非白名单仍照常禁用。
 * 注意：不能写 remap=false，否则 stopUsingItem 不会映射成 SRG 名（生产环境失效）。
 */
@Mixin(targets = "com/mega/revelationfix/common/apollyon/common/PlayerTickrateExecutor")
public abstract class PlayerTickrateBlockKeepMixin {

    @Unique
    private static Method shatter$inWhitelist;

    @Redirect(
            method = "playerTick(Lnet/minecraft/world/entity/player/Player;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;stopUsingItem()V"),
            require = 0
    )
    private static void soulSword$allowBlockInDoom(Player player) {
        ItemStack useItem = player.getUseItem();
        boolean whitelisted = !useItem.isEmpty()
                && (isWhitelisted(useItem.getItem())
                || useItem.getItem() instanceof SoulSwordItem
                || useItem.getItem() instanceof SoulBowItem);
        if (!whitelisted) {
            player.stopUsingItem();
        }
    }

    @Unique
    private static boolean isWhitelisted(Item item) {
        try {
            if (shatter$inWhitelist == null) {
                shatter$inWhitelist = Class.forName("com.mega.revelationfix.common.config.CommonConfig")
                        .getMethod("inWhitelist", Item.class);
            }
            return Boolean.TRUE.equals(shatter$inWhitelist.invoke(null, item));
        } catch (Throwable t) {
            return false;
        }
    }
}
