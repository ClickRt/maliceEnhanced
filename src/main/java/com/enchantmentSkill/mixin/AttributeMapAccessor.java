package com.enchantmentSkill.mixin;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * AttributeMap 在 1.20.1 没有公开的"全部属性"遍历方法（只有私有字段 attributes），
 * RevelationFix 也是用同名 Accessor 拿的。这里给无垠长魂之剑属性豁免逻辑提供一个 Accessor。
 */
@Mixin(AttributeMap.class)
public interface AttributeMapAccessor {
    @Accessor("attributes")
    Map<Attribute, AttributeInstance> soulSword$getAttributesMap();
}
