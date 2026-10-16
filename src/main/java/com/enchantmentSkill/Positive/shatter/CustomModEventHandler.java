package com.enchantmentSkill.positive.shatter;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.enchantmentSkill.goetyrevelation_stub.util.ApollyonAbilityHelper;
import com.enchantmentSkill.positive.luckHit.LuckyHitEventHandler;
import com.enchantmentSkill.positive.bloodDebt.BloodDebtEventHandler;
import weapon.SoulSwordItem;
import weapon.SoulSwordBlackHoleAccessor;

/**
 * 破限附魔（SHATTERER）核心事件处理器。
 * 关键修复：通过 Mixin 拦截 hitCooldown 的设置，强制设为 0。
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CustomModEventHandler {

    private static Class<?> APOSTLE_CLASS = null;
    private static Field MODDED_INVUL_FIELD = null;
    private static Field MAX_DAMAGE_TAKEN_FIELD = null;

    private static final Map<LivingEntity, Float> PENDING_DAMAGE = new IdentityHashMap<>();
    private static final Map<LivingEntity, List<SegmentData>> COMPENSATION_SEGMENTS = new ConcurrentHashMap<>();
    private static final Map<LivingEntity, SonicDamageData> PENDING_SONIC_DAMAGE = new ConcurrentHashMap<>();

    // ========== 新增：标记是否正在处理补偿伤害 ==========
    /** 标记实体正在被破限附魔攻击，用于拦截无敌帧设置 */
    private static final Set<LivingEntity> SHATTERING_ENTITIES = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /** 正在处理破限的补偿/分段伤害：补偿伤害触发的事件不应再次进入破限逻辑，防止伤害链式自放大 */
    private static final ThreadLocal<Boolean> PROCESSING_COMPENSATION = ThreadLocal.withInitial(() -> false);

    private static class SegmentData {
        final DamageSource source;
        final float damage;
        SegmentData(DamageSource source, float damage) {
            this.source = source;
            this.damage = damage;
        }
    }

    private static class SonicDamageData {
        final DamageSource source;
        final float damage;
        final int delayTicks;
        SonicDamageData(DamageSource source, float damage, int delayTicks) {
            this.source = source;
            this.damage = damage;
            this.delayTicks = delayTicks;
        }
    }

    static {
        try {
            APOSTLE_CLASS = Class.forName("com.Polarice3.Goety.common.entities.boss.Apostle");
            MODDED_INVUL_FIELD = APOSTLE_CLASS.getDeclaredField("moddedInvul");
            MODDED_INVUL_FIELD.setAccessible(true);
            MAX_DAMAGE_TAKEN_FIELD = APOSTLE_CLASS.getDeclaredField("maxDamageTaken");
            MAX_DAMAGE_TAKEN_FIELD.setAccessible(true);
            System.out.println("[Shatter] Goety Apostle class found.");
        } catch (Exception e) {
            System.err.println("[Shatter] Reflection init failed: " + e.getMessage());
        }
    }

    private static boolean isApollyon(Entity entity) {
        if (APOSTLE_CLASS == null || !APOSTLE_CLASS.isInstance(entity)) return false;
        return entity instanceof ApollyonAbilityHelper helper && helper.allTitlesApostle_1_20_1$isApollyon();
    }

    private static boolean isRegularApostle(Entity entity) {
        return APOSTLE_CLASS != null && APOSTLE_CLASS.isInstance(entity) && !isApollyon(entity);
    }

    /** 强制清除所有免疫状态 */
    private static void forceDisableApollyonImmunity(LivingEntity target) {
        if (!(target instanceof ApollyonAbilityHelper helper)) return;

        helper.allTitlesApostle_1_20_1$setHitCooldown(0);
        helper.setApollyonTime(0);

        if (APOSTLE_CLASS != null && APOSTLE_CLASS.isInstance(target)) {
            try {
                if (MODDED_INVUL_FIELD != null)
                    MODDED_INVUL_FIELD.setInt(target, 0);
                if (MAX_DAMAGE_TAKEN_FIELD != null)
                    MAX_DAMAGE_TAKEN_FIELD.setFloat(target, Float.MAX_VALUE);
            } catch (Exception ignored) {}
        }

        target.invulnerableTime = 0;
        target.hurtTime = 0;
    }

    /**
     * 关键修复：在伤害处理前标记实体，用于拦截 GoetyRevelation 设置无敌帧
     */
    private static void markShattering(LivingEntity target) {
        SHATTERING_ENTITIES.add(target);
        // 延迟清除标记（防止内存泄漏）
        if (target.level().isClientSide()) return;
        target.level().getServer().execute(() -> {
            try { Thread.sleep(100); } catch (Exception ignored) {}
            SHATTERING_ENTITIES.remove(target);
        });
    }

    /**
     * 对外暴露的方法，供 Mixin 调用：检查是否应该阻止设置无敌帧
     */
    public static boolean shouldBlockHitCooldown(LivingEntity entity) {
        return SHATTERING_ENTITIES.contains(entity);
    }

    private static float calculateActualDamage(LivingEntity attacker, ItemStack weapon, Entity target) {
        float baseDamage = (float) attacker.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);

        int sharpnessLevel = weapon.getEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS);
        if (sharpnessLevel > 0) {
            baseDamage += sharpnessLevel * 0.5f + 0.5f;
        }

        if (target instanceof LivingEntity livingTarget) {
            int smiteLevel = weapon.getEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.SMITE);
            if (smiteLevel > 0 && isUndead(livingTarget)) {
                baseDamage += smiteLevel * 2.5f;
            }

            int baneLevel = weapon.getEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.BANE_OF_ARTHROPODS);
            if (baneLevel > 0 && isArthropod(livingTarget)) {
                baseDamage += baneLevel * 2.5f;
            }
        }
        return baseDamage;
    }

    private static boolean isUndead(LivingEntity entity) {
        String name = entity.getType().toString().toLowerCase();
        return name.contains("zombie") || name.contains("skeleton") || name.contains("wither") ||
                name.contains("phantom") || name.contains("drowned") || name.contains("husk") ||
                name.contains("stray") || name.contains("bogged") || name.contains("zoglin") ||
                name.contains("zombified_piglin") || name.contains("zombie_horse") ||
                name.contains("skeleton_horse") || name.contains("codger") || name.contains("crone");
    }

    private static boolean isArthropod(LivingEntity entity) {
        String name = entity.getType().toString().toLowerCase();
        return name.contains("spider") || name.contains("silverfish") || name.contains("endermite") || name.contains("bee");
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent event) {
        if (event.isCanceled()) return;
        // 补偿伤害内部触发 LivingAttackEvent，不能再次记录理论伤害，否则会无限叠加
        if (PROCESSING_COMPENSATION.get()) return;
        DamageSource source = event.getSource();
        Entity target = event.getEntity();
        if (!(source.getDirectEntity() instanceof LivingEntity attacker)) return;

        ItemStack weapon = attacker.getMainHandItem();
        if (weapon.getEnchantmentLevel(CustomModEnchantments.SHATTERER.get()) <= 0
                && !(weapon.getItem() instanceof SoulSwordItem)) return;

        float theoretical = calculateActualDamage(attacker, weapon, target);
        PENDING_DAMAGE.put((LivingEntity) target, theoretical);

        if (isApollyon(target)) {
            forceDisableApollyonImmunity((LivingEntity) target);
            markShattering((LivingEntity) target);  // 标记为正在破限攻击
            System.out.println("[Shatter] Attack - Marked Apollyon for shattering.");
        } else if (isRegularApostle(target)) {
            try {
                if (MAX_DAMAGE_TAKEN_FIELD != null)
                    MAX_DAMAGE_TAKEN_FIELD.setFloat(target, Float.MAX_VALUE);
                if (MODDED_INVUL_FIELD != null)
                    MODDED_INVUL_FIELD.setInt(target, 0);
            } catch (Exception ignored) {}
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt_HIGHEST(LivingHurtEvent event) {
        if (isApollyon(event.getEntity())) {
            forceDisableApollyonImmunity((LivingEntity) event.getEntity());
            markShattering((LivingEntity) event.getEntity());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        // 补偿伤害自己的 LivingDamageEvent 不能再次被破限放大/安排新的补偿
        if (PROCESSING_COMPENSATION.get()) return;
        DamageSource source = event.getSource();
        Entity target = event.getEntity();
        if (!(target instanceof LivingEntity livingTarget)) return;

        Entity direct = source.getDirectEntity();
        float theoretical;
        boolean applyMultipliers;
        if (direct instanceof LivingEntity attacker) {
            ItemStack weapon = attacker.getMainHandItem();
            if (weapon.getEnchantmentLevel(CustomModEnchantments.SHATTERER.get()) <= 0
                    && !(weapon.getItem() instanceof SoulSwordItem)) return;
            Float pending = PENDING_DAMAGE.remove(livingTarget);
            if (pending == null) return;
            theoretical = pending;
            applyMultipliers = true;
        } else if (direct instanceof SoulSwordBlackHoleAccessor hole && hole.soulSword$getColor() != 0) {
            // 蓝色黑洞（无垠长魂之剑施法）：与破限杀手同款通用破限
            // 排除我们附加的虚空伤害事件（soul_sword 类型），避免二次放大
            if ("soul_sword".equals(source.getMsgId())) return;
            theoretical = hole.soulSword$getDamage();
            applyMultipliers = false;
        } else {
            return;
        }

        float boostedTheoretical = theoretical;
        if (applyMultipliers) {
            float luckyHitMult = LuckyHitEventHandler.consumeMultiplier(livingTarget);
            float bloodDebtMult = BloodDebtEventHandler.consumeMultiplier(livingTarget);
            boostedTheoretical = theoretical * luckyHitMult * bloodDebtMult;
        }

        if (isApollyon(livingTarget)) {
            float actualHurt = event.getAmount();
            float remaining = boostedTheoretical - actualHurt;

            if (remaining > 0) {
                List<SegmentData> segments = new ArrayList<>();
                while (remaining > 0) {
                    float seg = Math.min(20.0f, remaining);
                    segments.add(new SegmentData(source, seg));
                    remaining -= seg;
                }
                COMPENSATION_SEGMENTS.put(livingTarget, segments);
                System.out.println("[Shatter] Apollyon - Scheduled " + segments.size() + " segments.");
            }

            forceDisableApollyonImmunity(livingTarget);
            markShattering(livingTarget);  // 标记为正在破限攻击

            if (MAX_DAMAGE_TAKEN_FIELD != null) {
                try { MAX_DAMAGE_TAKEN_FIELD.setFloat(target, 19.0F); } catch (Exception ignored) {}
            }
        } else if (isRegularApostle(livingTarget)) {
            event.setAmount(Math.max(event.getAmount(), boostedTheoretical));
            livingTarget.invulnerableTime = 0;
            livingTarget.hurtTime = 0;

            if (MAX_DAMAGE_TAKEN_FIELD != null) {
                try { MAX_DAMAGE_TAKEN_FIELD.setFloat(target, 19.0F); } catch (Exception ignored) {}
            }
        } else {
            float currentAmount = event.getAmount();
            event.setAmount(Math.max(currentAmount, boostedTheoretical));
            livingTarget.invulnerableTime = 0;
            livingTarget.hurtTime = 0;

            if (boostedTheoretical > 20.0f) {
                float reduced = boostedTheoretical - currentAmount;
                if (reduced > 0) {
                    int ticks = (int) (reduced / 20.0f);
                    float sonicDamage = ticks * 20.0f;
                    if (sonicDamage > 0) {
                        PENDING_SONIC_DAMAGE.put(livingTarget, new SonicDamageData(source, sonicDamage, 1));
                    }
                }
            }
        }
    }

    /**
     * 蓝色黑洞通用破限兜底（LOWEST）：非 Apostle/Apollyon 的限伤怪若在
     * LivingDamageEvent 里把伤害封顶（如终末之环的 20 上限、其他 mod 的事件级 cap），
     * 此处理器在 cap 之后把蓝色黑洞的主伤害拉回理论值，并清除无敌帧/hurtTime。
     * 对高抗怪同样有效（护甲/学派抗性压低后被拉回）。
     * 虚空伤害事件（soul_sword 类型）不参与，避免二次放大。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlueHoleDamageLowest(LivingDamageEvent event) {
        if (PROCESSING_COMPENSATION.get()) return;
        DamageSource source = event.getSource();
        if (!(source.getDirectEntity() instanceof SoulSwordBlackHoleAccessor hole && hole.soulSword$getColor() != 0)) return;
        if ("soul_sword".equals(source.getMsgId())) return;

        float theoretical = hole.soulSword$getDamage();
        if (theoretical > event.getAmount()) {
            event.setAmount(theoretical);
            LivingEntity target = event.getEntity();
            target.invulnerableTime = 0;
            target.hurtTime = 0;
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        // 处理 Apollyon 分段补偿
        COMPENSATION_SEGMENTS.entrySet().removeIf(entry -> {
            LivingEntity target = entry.getKey();
            List<SegmentData> segments = entry.getValue();

            if (!target.isAlive() || target.isRemoved()) return true;

            if (!segments.isEmpty()) {
                SegmentData seg = segments.remove(0);

                // ========== 关键修复：伤害前标记 + 清除免疫 ==========
                markShattering(target);           // 标记为正在破限攻击
                forceDisableApollyonImmunity(target);  // 清除所有免疫

                PROCESSING_COMPENSATION.set(true);
                try {
                    target.hurt(seg.source, seg.damage);
                } finally {
                    PROCESSING_COMPENSATION.set(false);
                }
                System.out.println("[Shatter] Compensation hit: " + seg.damage + ", remaining: " + segments.size());
            }

            return segments.isEmpty();
        });

        // 处理普通生物延迟伤害
        PENDING_SONIC_DAMAGE.entrySet().removeIf(entry -> {
            LivingEntity target = entry.getKey();
            SonicDamageData data = entry.getValue();

            if (!target.isAlive() || target.isRemoved()) return true;

            target.invulnerableTime = 0;
            target.hurtTime = 0;
            if (isApollyon(target)) {
                markShattering(target);
                forceDisableApollyonImmunity(target);
            }
            PROCESSING_COMPENSATION.set(true);
            try {
                target.hurt(data.source, data.damage);
            } finally {
                PROCESSING_COMPENSATION.set(false);
            }
            return true;
        });
    }
}
