package com.enchantmentSkill.Curse.mockingCurse;

import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MockingCurseMod {
    private static final Logger LOGGER = LoggerFactory.getLogger("MockingCurse");

    public MockingCurseMod() {
        LOGGER.info("MockingCurse mod is loading!");
        MockingCurseRegistries.init(FMLJavaModLoadingContext.get().getModEventBus());
        LOGGER.info("MockingCurse mod loaded successfully!");
    }
}
