// ========== 文件名: CustomModEnchantments.java ==========
package com.enchantmentSkill.positive.shatter; // 修改了包路径

import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 附魔注册器
 */
public class CustomModEnchantments { // 修改了类名以区分
    public static final DeferredRegister<Enchantment> ENCHANTMENTS = DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, "malice_enhanced");

    public static final RegistryObject<Enchantment> SHATTERER = ENCHANTMENTS.register("shatterer", ShattererEnchantment::new);
}