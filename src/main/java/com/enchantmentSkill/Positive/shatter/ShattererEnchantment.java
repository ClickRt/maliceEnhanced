// ========== 文件名: ShattererEnchantment.java ==========
package com.enchantmentSkill.positive.shatter; // 修改了包路径

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;


/**
 * 破坏者附魔：允许攻击穿透使徒的伤害上限
 */
public class ShattererEnchantment extends Enchantment {
    public ShattererEnchantment() {
        // 附魔类别为武器、弓、弩、三叉戟等所有攻击性装备
        super(Rarity.VERY_RARE, EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    @Override
    public boolean isTreasureOnly() {
        return true;
    }

    @Override
    public boolean isDiscoverable() {
        return false;
    }

    /**
     * 获取最低附魔等级（用于铁砧和附魔台）
     */
    @Override
    public int getMinCost(int p_44620_) {
        return 15;
    }

    @Override
    public int getMaxCost(int p_44627_) {
        return super.getMinCost(p_44627_) + 50;
    }

    /**
     * 定义附魔兼容性
     * 此处修正：允许“破坏者”与所有其他附魔兼容，包括锋利、亡灵杀手、节肢杀手
     */
    @Override
    public boolean checkCompatibility(Enchantment p_44655_) {
        // 返回 true 表示与传入的附魔兼容，可以共存
        // 这样“破坏者”就可以和锋利、亡灵杀手、节肢杀手等一起附在一件装备上了
        return true;
    }

    // 注意：由于我们将使用虚空伤害源绕过上限，这个 modifyDamage 方法可能不再需要了
    // 但保留它以防将来需要其他逻辑
    /*
    public static float modifyDamage(float originalDamage, DamageSource source, Entity target) {
        // 检查目标是否为活着的实体
        if (target instanceof LivingEntity && target.isAlive()) {
            // 检查伤害来源是否是带有破坏者附魔的实体
            if (source.getDirectEntity() instanceof LivingEntity attacker) {
                ItemStack weapon = attacker.getMainHandItem();
                if (weapon.getEnchantmentLevel(CustomModEnchantments.SHATTERER.get()) > 0) {
                    // 如果是，返回原始伤害，绕过使徒的上限
                    System.out.println("Shatterer enchantment bypassed damage cap!");
                    return originalDamage;
                }
            }
            // 如果不是，则维持原有逻辑，伤害会被上限限制
        }
        return originalDamage;
    }
    */
}