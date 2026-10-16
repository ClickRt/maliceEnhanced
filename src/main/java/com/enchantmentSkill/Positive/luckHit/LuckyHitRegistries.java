package com.enchantmentSkill.positive.luckHit;

import net.minecraftforge.eventbus.api.IEventBus;

public class LuckyHitRegistries {
    public static void init(IEventBus eventBus) {
        LuckyHitEnchantments.ENCHANTMENTS.register(eventBus);
    }
}
