package com.enchantmentSkill.positive.bloodDebt;

import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BloodDebtMod {
    private static final Logger LOGGER = LoggerFactory.getLogger("BloodDebt");

    public BloodDebtMod() {
        LOGGER.info("BloodDebt mod is loading!");
        BloodDebtRegistries.init(FMLJavaModLoadingContext.get().getModEventBus());
        LOGGER.info("BloodDebt mod loaded successfully!");
    }
}
