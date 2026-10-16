package com.enchantmentSkill.positive.tentacles;

import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class TheExtensionOfTentaclesEnchantments {
    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, "malice_enhanced");

    public static final RegistryObject<Enchantment> EXTENSION_OF_TENTACLES =
            ENCHANTMENTS.register("extension_of_tentacles", TheExtensionOfTentaclesEnchantment::new);
}
