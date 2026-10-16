package com.enchantmentSkill.positive.bloodDebt;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BloodDebtEventHandler {

    /** 供 Shatterer 及 Mixin 读取：记录本次攻击血债血偿的伤害倍率 */
    private static final Map<LivingEntity, Float> PENDING_MULTIPLIERS = new ConcurrentHashMap<>();

    /**
     * 双射防护：Mixin 成功注入倍率后置为 true，事件处理器看到后跳过，避免重复乘。
     * 若 Mixin 未工作（在某些环境下注入失败），事件处理器兜底生效。
     */
    public static final ThreadLocal<Boolean> MIXIN_APPLIED = ThreadLocal.withInitial(() -> false);

    public static float consumeMultiplier(LivingEntity entity) {
        Float mult = PENDING_MULTIPLIERS.remove(entity);
        return mult != null ? mult : 1.0F;
    }

    /** 供 Mixin 调用：存入倍率 */
    public static void storeMultiplier(LivingEntity entity, float multiplier) {
        PENDING_MULTIPLIERS.put(entity, multiplier);
    }

    /** 阻止同线程递归重入 —— 某些模组环境 LivingHurtEvent 会多次触发 */
    private static final ThreadLocal<Boolean> IN_HANDLER = ThreadLocal.withInitial(() -> false);

    /** 每 tick 同一目标只乘一次，防止环境多次触发 LivingHurtEvent 导致伤害自我乘方 */
    private static final Map<LivingEntity, Long> LAST_PROCESSED_TICK = new WeakHashMap<>();

    // ──────────── 兜底：LivingHurtEvent 乘倍率（Mixin 不工作时生效）────────────

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (IN_HANDLER.get()) return;
        if (MIXIN_APPLIED.get()) {
            MIXIN_APPLIED.set(false);
            return;
        }

        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        long tick = target.level().getGameTime();
        Long lastTick = LAST_PROCESSED_TICK.get(target);
        if (lastTick != null && lastTick == tick) return;
        LAST_PROCESSED_TICK.put(target, tick);

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

        int level = weapon.getEnchantmentLevel(BloodDebtEnchantments.BLOOD_DEBT.get());
        if (level <= 0) return;

        float maxHealth = attacker.getMaxHealth();
        float currentHealth = attacker.getHealth();

        float add = Math.min((int) (maxHealth / currentHealth), 9) * 0.2F;
        float multiplier = 1.0F + add;

        float oldAmount = event.getAmount();
        float newAmount = oldAmount * multiplier;
        System.out.printf("[BloodDebt] target=%s src=%s HP=%.1f/%.1f ratio=%d add=%.1f mult=%.2f | %.1f -> %.1f%n",
                event.getEntity().getName().getString(),
                event.getSource().getMsgId(),
                currentHealth, maxHealth, (int) (maxHealth / currentHealth), add, multiplier,
                oldAmount, newAmount);

        IN_HANDLER.set(true);
        try {
            event.setAmount(newAmount);
        } finally {
            IN_HANDLER.set(false);
        }
        PENDING_MULTIPLIERS.put(event.getEntity(), multiplier);
    }

    // ──────────── 扣血 + 吸收（事件/Mixin 均会走到这里）────────────

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (!(event.getSource().getDirectEntity() instanceof LivingEntity attacker)) return;
        if (attacker.level().isClientSide()) return;

        ItemStack weapon = attacker.getMainHandItem();
        int level = weapon.getEnchantmentLevel(BloodDebtEnchantments.BLOOD_DEBT.get());
        if (level <= 0) return;

        float maxHealth = attacker.getMaxHealth();
        float currentHealth = attacker.getHealth();

        if (currentHealth <= maxHealth * 0.1F) {
            float currentAbsorption = attacker.getAbsorptionAmount();
            float newAbsorption = Math.min(currentAbsorption + maxHealth * 0.05F, maxHealth * 0.9F);
            attacker.setAbsorptionAmount(newAbsorption);
            return;
        }

        float healthToLose = maxHealth * 0.1F;
        attacker.setHealth(Math.max(currentHealth - healthToLose, 1.0F));

        float currentAbsorption = attacker.getAbsorptionAmount();
        float newAbsorption = Math.min(currentAbsorption + healthToLose, maxHealth * 0.9F);
        attacker.setAbsorptionAmount(newAbsorption);
    }
}
