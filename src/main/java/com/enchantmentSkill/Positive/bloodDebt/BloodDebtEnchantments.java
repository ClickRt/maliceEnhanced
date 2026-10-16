package com.enchantmentSkill.positive.bloodDebt;

import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class BloodDebtEnchantments {
    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, "malice_enhanced");

    public static final RegistryObject<Enchantment> BLOOD_DEBT =
            ENCHANTMENTS.register("blood_debt", BloodDebtEnchantment::new);
}
