package com.enchantmentSkill.positive.recoil;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RecoilEventHandler {

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        LivingEntity attacker = null;
        ItemStack weapon = ItemStack.EMPTY;

        if (event.getSource().getDirectEntity() instanceof LivingEntity direct) {
            attacker = direct;
            weapon = attacker.getMainHandItem();
        } else if (event.getSource().getDirectEntity() instanceof Projectile projectile
                && projectile.getOwner() instanceof LivingEntity owner) {
            attacker = owner;
            weapon = attacker.getMainHandItem();
        }

        if (attacker == null || weapon.isEmpty()) return;

        int level = weapon.getEnchantmentLevel(RecoilEnchantments.RECOIL.get());
        if (level <= 0) return;

        LivingEntity target = event.getEntity();
        double dx = attacker.getX() - target.getX();
        double dz = attacker.getZ() - target.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 0.01) {
            dx = attacker.getRandom().nextDouble() - 0.5;
            dz = attacker.getRandom().nextDouble() - 0.5;
            dist = Math.sqrt(dx * dx + dz * dz);
        }
        dx /= dist;
        dz /= dist;

        double knockbackBlocks = Math.pow(2, level);
        attacker.knockback(knockbackBlocks * 0.6, dx, dz);
    }
}
