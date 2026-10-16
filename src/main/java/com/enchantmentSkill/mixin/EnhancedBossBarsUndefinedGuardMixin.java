package com.enchantmentSkill.mixin;

import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wither_title.WitherTitleHelper;

/**
 * 守卫 Mixin：拦截 enhanced_boss_bars（com.nakuring.enhanced_boss_bars）
 * 对未定义凋灵血条的接管。
 *
 * 它的 CustomBarRenderer.onCustomGuiOverlay 是 EventPriority.HIGHEST 监听器：
 * - 等值分支按翻译名精确匹配（zh 下 "凋灵"），瞬时名也可能命中；
 * - contains 分支在装了 aether 时对任意条目放宽为子串匹配，
 *   "?凋灵?".contains("凋灵") 在中文语言下恒为 true，
 *   于是它 setCanceled(true) 抢走血条画自己的紫色凋灵框，我们的自定义血条失效。
 *
 * 这里在它方法 HEAD 注入：血条名是未定义凋灵（"?凋灵?"）时直接取消它的方法调用，
 * 其余 Boss（原版凋灵、灾厄、aether 等）完全不受影响。
 *
 * 用 targets 字符串软引用 + 配置 required=false：未安装 enhanced_boss_bars 时
 * 目标类不存在，本 mixin 自动跳过（与 BlackHoleMixin 钩 irons 同一机制）。
 * 对方是纯客户端渲染器，服务端永远不会加载目标类，注入体不会触碰客户端类。
 */
@Mixin(targets = "com.nakuring.enhanced_boss_bars.CustomBarRenderer")
public class EnhancedBossBarsUndefinedGuardMixin {

    /** 对方处理器是 static 方法，注入处理函数必须同为 static */
    @Inject(method = "onCustomGuiOverlay", at = @At("HEAD"), cancellable = true)
    private static void shatter$skipUndefinedWitherBar(CustomizeGuiOverlayEvent.BossEventProgress event,
                                                       CallbackInfo ci) {
        if (WitherTitleHelper.isUndefinedBarName(event.getBossEvent().getName())) {
            ci.cancel();
        }
    }
}
