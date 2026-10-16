package com.enchantmentSkill.positive.pureMalice;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import weapon.SoulSwordEventHandler;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 纯粹恶意附魔核心事件处理器：
 * 持附魔武器命中实体后，额外施加 level*4 + 目标最大生命 0.5% 的灵魂伤害
 * （damage_soul，一次结算直写扣血）。
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PureMaliceEventHandler {

    /** 防止灵魂伤害自身触发的 LivingHurtEvent 再次进入本处理器，避免无限递归 */
    private static final ThreadLocal<Boolean> APPLYING_SOUL = ThreadLocal.withInitial(() -> false);

    /** 同一 tick 同一目标只处理一次，防止环境重复触发 LivingHurtEvent 导致灵魂伤害叠加 */
    private static final Map<LivingEntity, Long> LAST_PROCESSED_TICK = new WeakHashMap<>();

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (APPLYING_SOUL.get()) return;
        if (event.isCanceled()) return;

        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;

        long tick = target.level().getGameTime();
        Long lastTick = LAST_PROCESSED_TICK.get(target);
        if (lastTick != null && lastTick == tick) return;
        LAST_PROCESSED_TICK.put(target, tick);

        LivingEntity attacker = null;
        ItemStack weapon = ItemStack.EMPTY;

        // 近战：直接伤害来源必须是持有附魔武器的生物
        if (event.getSource().getDirectEntity() instanceof LivingEntity direct) {
            attacker = direct;
            weapon = attacker.getMainHandItem();
        } else if (event.getSource().getDirectEntity() instanceof Projectile projectile
                && projectile.getOwner() instanceof LivingEntity owner) {
            // 投射物：射出手里正拿着附魔武器（与幸运一击/反冲力同一判定口径）
            attacker = owner;
            weapon = attacker.getMainHandItem();
        }

        if (attacker == null || weapon.isEmpty()) return;
        if (attacker == target) return; // 自伤不触发

        int level = weapon.getEnchantmentLevel(PureMaliceEnchantments.PURE_MALICE.get());
        if (level <= 0) return;

        float soulAmount = level * 4.0F + target.getMaxHealth() * 0.005F;
        APPLYING_SOUL.set(true);
        try {
            if (target instanceof WitherBoss wither
                    && wither_title.WitherTitleHelper.isUndefinedWither(wither)) {
                SoulSwordEventHandler.applyPureMaliceDamage(target, attacker,
                        wither_title.WitherTitleHelper.capIncomingSoulDamage(wither, soulAmount));
                return;
            }
            SoulSwordEventHandler.applySoulDamage(target, attacker, soulAmount);
        } finally {
            APPLYING_SOUL.set(false);
        }
    }
}
