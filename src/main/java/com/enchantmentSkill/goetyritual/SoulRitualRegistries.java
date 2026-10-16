package com.enchantmentSkill.goetyritual;

import com.Polarice3.Goety.api.ritual.RitualType;

/**
 * 仅在 goety 已加载时由 EnchantmentSkillMod 调用。
 * 必须在 JEI 初始化（客户端 registerCategories）之前完成注册，
 * Goety 的 JEI 插件会遍历 RitualType.getAllRitualType() 自动为
 * soul_ritual 生成类别、以 getJeiIcon()（灵魂沙）作为类别图标与配方催化剂。
 */
public class SoulRitualRegistries {

    public static void init() {
        RitualType.addRitualType(SoulRitualType.NAME, new SoulRitualType());
    }
}
