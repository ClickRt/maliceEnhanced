package com.enchantmentSkill.Curse.mockingCurse;

import net.minecraftforge.eventbus.api.IEventBus;

public class MockingCurseRegistries {
    public static void init(IEventBus eventBus) {
        MockingCurseEnchantments.ENCHANTMENTS.register(eventBus);
    }
}
