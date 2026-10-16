// ========== 文件名: CustomModRegistries.java ==========
package com.enchantmentSkill.positive.shatter; // 修改了包路径

import net.minecraftforge.eventbus.api.IEventBus;

/**
 * 初始化所有内容
 * 注意：此方法现在需要接收 IEventBus 参数
 */
public class CustomModRegistries { // 修改了类名以区分
    /**
     * 初始化方法，应在模组主类构造函数中调用，并传入 FMLJavaModLoadingContext.get().getModEventBus()
     * @param eventBus 模组的事件总线
     */
    public static void init(IEventBus eventBus) {
        // 将 CustomModEnchantments 中的 DeferredRegister 注册到提供的事件总线
        CustomModEnchantments.ENCHANTMENTS.register(eventBus); // 修改了引用
    }
}