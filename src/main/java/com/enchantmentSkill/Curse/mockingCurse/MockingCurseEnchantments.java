package com.enchantmentSkill.Curse.mockingCurse;

import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class MockingCurseEnchantments {
    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, "malice_enhanced");

    public static final RegistryObject<Enchantment> MOCKING_CURSE =
            ENCHANTMENTS.register("mocking_curse", MockingCurseEnchantment::new);
}
