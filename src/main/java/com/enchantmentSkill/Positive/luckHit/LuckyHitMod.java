package com.enchantmentSkill.positive.luckHit;

import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LuckyHitMod {
    private static final Logger LOGGER = LoggerFactory.getLogger("LuckyHit");

    public LuckyHitMod() {
        LOGGER.info("LuckyHit mod is loading!");
        LuckyHitRegistries.init(FMLJavaModLoadingContext.get().getModEventBus());
        LOGGER.info("LuckyHit mod loaded successfully!");
    }
}
