package com.enchantmentSkill.positive.recoil;

import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RecoilMod {
    private static final Logger LOGGER = LoggerFactory.getLogger("Recoil");

    public RecoilMod() {
        LOGGER.info("Recoil mod is loading!");
        RecoilRegistries.init(FMLJavaModLoadingContext.get().getModEventBus());
        LOGGER.info("Recoil mod loaded successfully!");
    }
}
