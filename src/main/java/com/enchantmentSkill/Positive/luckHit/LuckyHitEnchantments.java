package com.enchantmentSkill.positive.luckHit;

import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class LuckyHitEnchantments {
    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, "malice_enhanced");

    public static final RegistryObject<Enchantment> LUCKY_HIT =
            ENCHANTMENTS.register("lucky_hit", LuckyHitEnchantment::new);
}
