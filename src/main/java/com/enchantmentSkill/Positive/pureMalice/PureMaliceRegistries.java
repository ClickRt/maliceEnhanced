package com.enchantmentSkill.positive.pureMalice;

import net.minecraftforge.eventbus.api.IEventBus;

public class PureMaliceRegistries {
    public static void init(IEventBus eventBus) {
        PureMaliceEnchantments.ENCHANTMENTS.register(eventBus);
    }
}
