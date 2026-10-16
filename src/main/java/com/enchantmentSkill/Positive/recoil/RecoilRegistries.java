package com.enchantmentSkill.positive.recoil;

import net.minecraftforge.eventbus.api.IEventBus;

public class RecoilRegistries {
    public static void init(IEventBus eventBus) {
        RecoilEnchantments.ENCHANTMENTS.register(eventBus);
    }
}
