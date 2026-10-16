package com.enchantmentSkill.positive.pureMalice;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * 纯粹恶意（Pure Malice）：
 * 武器类附魔，最高 V 级。攻击命中实体时附加 level*6 点灵魂伤害（damage_soul），
 * I 级 = 6 点，V 级 = 30 点。名字固定显示为青色 #0CEEFA。
 */
public class PureMaliceEnchantment extends Enchantment {

    private static final TextColor NAME_COLOR = TextColor.fromRgb(0x0CEEFA);

    public PureMaliceEnchantment() {
        super(Rarity.RARE, EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    public int getMaxLevel() {
        return 5;
    }

    @Override
    public int getMinCost(int level) {
        return 5 + (level - 1) * 10;
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 50;
    }

    /** 完整名称（含等级罗马数字）同样使用 #0CEEFA 青色 */
    @Override
    public Component getFullname(int level) {
        MutableComponent name = Component.translatable(this.getDescriptionId())
                .withStyle(Style.EMPTY.withColor(NAME_COLOR));
        if (level != 1 || this.getMaxLevel() != 1) {
            name.append(" ").append(Component.translatable("enchantment.level." + level));
        }
        return name;
    }
}
