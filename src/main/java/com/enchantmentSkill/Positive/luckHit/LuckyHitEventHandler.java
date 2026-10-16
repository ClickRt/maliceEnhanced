package com.enchantmentSkill.positive.luckHit;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class LuckyHitEventHandler {
    private static final Random RANDOM = new Random();

    /** 供 Shatterer 读取：记录本次攻击 LuckyHit 的伤害倍率 */
    private static final Map<LivingEntity, Float> PENDING_MULTIPLIERS = new ConcurrentHashMap<>();

    public static float consumeMultiplier(LivingEntity entity) {
        Float mult = PENDING_MULTIPLIERS.remove(entity);
        return mult != null ? mult : 1.0F;
    }

    /** 同一 tick 同一目标只处理一次，防环境多次触发 LivingHurtEvent 导致伤害乘方 */
    private static final Map<LivingEntity, Long> LAST_PROCESSED_TICK = new WeakHashMap<>();

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        long tick = target.level().getGameTime();
        Long lastTick = LAST_PROCESSED_TICK.get(target);
        if (lastTick != null && lastTick == tick) return;
        LAST_PROCESSED_TICK.put(target, tick);

        LivingEntity attacker = null;
        ItemStack weapon = ItemStack.EMPTY;
        boolean isProjectile = false;

        if (event.getSource().getDirectEntity() instanceof LivingEntity direct) {
            attacker = direct;
            weapon = attacker.getMainHandItem();
        } else if (event.getSource().getDirectEntity() instanceof Projectile projectile
                && projectile.getOwner() instanceof LivingEntity owner) {
            attacker = owner;
            weapon = attacker.getMainHandItem();
            isProjectile = true;
        }

        if (attacker == null || weapon.isEmpty()) return;

        int level = weapon.getEnchantmentLevel(LuckyHitEnchantments.LUCKY_HIT.get());
        if (level <= 0) return;

        // For melee attacks, require fully charged attack
        if (!isProjectile && attacker instanceof Player player) {
            float cooldown = player.getAttackStrengthScale(0.5F);
            if (cooldown < 0.95F) return;
        }

        float chance = level * 0.10F;
        if (RANDOM.nextFloat() < chance) {
            event.setAmount(event.getAmount() * 2.0F);
            PENDING_MULTIPLIERS.put(event.getEntity(), 2.0F);
        }
    }
}
