// ========== 文件名：ShatterMod.java ==========
package com.enchantmentSkill.positive.shatter;

import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShatterMod {
    private static final Logger LOGGER = LoggerFactory.getLogger("Shatter");
    
    public ShatterMod(FMLJavaModLoadingContext context) {
        LOGGER.info("=================================");
        LOGGER.info("Shatter mod is loading!");
        LOGGER.info("Initializing enchantment registry...");
        // 初始化模组注册器，传入模组事件总线
        CustomModRegistries.init(context.getModEventBus());
        LOGGER.info("Shatter mod loaded successfully!");
        LOGGER.info("=================================");
    }
}
