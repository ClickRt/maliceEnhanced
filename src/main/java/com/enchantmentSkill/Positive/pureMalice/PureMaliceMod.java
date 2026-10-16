package com.enchantmentSkill.positive.pureMalice;

import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PureMaliceMod {
    private static final Logger LOGGER = LoggerFactory.getLogger("PureMalice");

    public PureMaliceMod() {
        LOGGER.info("PureMalice mod is loading!");
        PureMaliceRegistries.init(FMLJavaModLoadingContext.get().getModEventBus());
        LOGGER.info("PureMalice mod loaded successfully!");
    }
}
