package com.enchantmentSkill.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wither_title.WitherTitleHelper;

/**
 * 让"灵魂之心"的显示名称拥有和无垠长魂之剑一样的流动渐变动画。
 * 每次渲染时动态计算，非静态文本。
 */
@Mixin(ItemStack.class)
public abstract class SoulStarItemStackMixin {

    @Inject(method = "getHoverName", at = @At("HEAD"), cancellable = true)
    private void extended$onGetHoverName(CallbackInfoReturnable<Component> cir) {
        ItemStack self = (ItemStack) (Object) this;
        if (!self.is(Items.NETHER_STAR)) return;
        if (self.hasTag() && self.getTag().contains(WitherTitleHelper.UNDEFINED_NBT_KEY)) {
            // 四色流动渐变动画：从 #07C0F2 -> #4CE2F2 -> #B9E8EB -> #707CE3 循环
            Style starStyle = Style.EMPTY.withBold(true).withItalic(false);
            cir.setReturnValue(WitherTitleHelper.gradientTextAnimated(
                    WitherTitleHelper.UNDEFINED_STAR_NAME, 0x07C0F2, 0x4CE2F2, 0xB9E8EB, 0x707CE3, starStyle));
        }
    }
}