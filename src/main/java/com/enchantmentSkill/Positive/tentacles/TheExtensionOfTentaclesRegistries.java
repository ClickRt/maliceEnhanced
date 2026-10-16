package com.enchantmentSkill.positive.tentacles;

import net.minecraftforge.eventbus.api.IEventBus;

public class TheExtensionOfTentaclesRegistries {
    public static void init(IEventBus eventBus) {
        TheExtensionOfTentaclesEnchantments.ENCHANTMENTS.register(eventBus);
    }
}
