package com.enchantmentSkill.positive.bloodDebt;

import net.minecraftforge.eventbus.api.IEventBus;

public class BloodDebtRegistries {
    public static void init(IEventBus eventBus) {
        BloodDebtEnchantments.ENCHANTMENTS.register(eventBus);
    }
}
