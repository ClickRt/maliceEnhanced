package wither_title;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.WitherSkull;
import weapon.starcrystal.StarCoronaCurios;

public class WitherTitleHelper {
    public static final String TITLE_KEY = "WitherTitleId";
    public static final String STAR_NBT_KEY = "malice_enhanced:wither_title";
    public static final String LANG_PREFIX = "wither_title.malice_enhanced.";
    public static final int MIN_TITLE = 1;
    public static final int MAX_TITLE = 13;
    public static final int UNDEFINED_ID = 14;
    public static final String UNDEFINED_NBT_KEY = "malice_enhanced:undefined_wither";
    public static final String UNDEFINED_FINAL_SKILL_NBT_KEY = "malice_enhanced:undefined_final_skill";
    public static final String UNDEFINED_STAR_NAME = "灵魂之心";
    /** 未定义凋灵专属掉落物：灵魂之星 */
    public static final String SOUL_STAR_NAME = "灵魂之星";
    /** 尾杀总时长：溶解 200 tick + 黑洞 900 tick（45 秒）。 */
    public static final int UNDEFINED_FINAL_TOTAL_TICKS = 1100;
    /** 溶解动画时长 */
    public static final int UNDEFINED_DISSOLVE_TICKS = 200;
    /** 无铁魔法时：第二波音波（5跳×1000，damage_soul）在溶解动画结束后再等 2 秒（40 tick） */
    public static final int SOUL_BOOM_SECOND_WAVE_TICK = 300;
    /** 无铁魔法时尾杀总时长：200 动画 + 第一波 + 2s 间隔 + 第二波 + 收尾 = 340 tick（17秒） */
    public static final int UNDEFINED_FINAL_TOTAL_TICKS_NO_IRONS = 340;
    /** 未定义凋灵每 tick 外来伤害预算（同 tick 多段伤害共享，先到先得），由数据层 WitherTitleHealthCapMixin 强制执行 */
    public static final float UNDEFINED_DAMAGE_CAP = 20.0F;
    /** 未定义凋灵固定生命上限；改 MAX_HEALTH 属性不能削真上限 */
    public static final float UNDEFINED_MAX_HEALTH = 1453.0F;
    /** 受伤后的自定义受击无敌窗口（soulHurtDefend 同思路，时长 30 tick） */
    public static final int HURT_DEFEND_TICKS = 30;

    // 数据同步器（类似 Goety 的 APOLLYON 标记）
    public static final EntityDataAccessor<Boolean> UNDEFINED_DATA =
            SynchedEntityData.defineId(WitherBoss.class, EntityDataSerializers.BOOLEAN);

    // 尾杀阶段标记（外部 Mod 风格锁血机制）
    public static final EntityDataAccessor<Boolean> UNDEFINED_FINAL_SKILL =
            SynchedEntityData.defineId(WitherBoss.class, EntityDataSerializers.BOOLEAN);

    // 尾杀倒计时
    public static final EntityDataAccessor<Integer> UNDEFINED_DEATH_TIME =
            SynchedEntityData.defineId(WitherBoss.class, EntityDataSerializers.INT);

    /** 冲撞前虚化：客户端按此标记把本体画成 90% 透明 */
    public static final EntityDataAccessor<Boolean> DIVE_GHOST =
            SynchedEntityData.defineId(WitherBoss.class, EntityDataSerializers.BOOLEAN);

    /** 未定义凋灵攻击闪电颜色 #C7C9D5。0 表示原版闪电。 */
    public static final int UNDEFINED_LIGHTNING_COLOR = 0xC7C9D5;
    public static final EntityDataAccessor<Integer> LIGHTNING_COLOR =
            SynchedEntityData.defineId(LightningBolt.class, EntityDataSerializers.INT);

    /**
     * 确保自定义闪电颜色数据项已注册。
     *
     * 某些整合环境会在 LightningBolt 的同步数据 Mixin 执行前创建实体，
     * 此时直接 set 会在 SynchedEntityData 中取到 null DataItem 并使服务器崩溃。
     * 颜色只是视觉效果，注册失败时应回退到原版闪电，而不是中断伤害事件。
     */
    public static boolean setLightningColor(LightningBolt lightning, int color) {
        if (lightning == null) return false;
        try {
            SynchedEntityData data = lightning.getEntityData();
            if (!data.hasItem(LIGHTNING_COLOR)) {
                data.define(LIGHTNING_COLOR, 0);
            }
            data.set(LIGHTNING_COLOR, color);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 客户端渲染读取颜色；数据项缺失时返回 0，表示使用原版颜色。 */
    public static int getLightningColor(LightningBolt lightning) {
        if (lightning == null) return 0;
        try {
            SynchedEntityData data = lightning.getEntityData();
            if (!data.hasItem(LIGHTNING_COLOR)) return 0;
            Integer color = data.get(LIGHTNING_COLOR);
            return color == null ? 0 : color;
        } catch (Throwable ignored) {
            return 0;
        }
    }
    public static final String SKULL_TARGET_KEY = "WTarget";
    public static final String FLAME_SKULL_KEY = "FlameSkull";
    /** 未定义凋灵：20% 追踪型凋零之首（改 xPower 锁敌 + 灵魂火尾焰） */
    public static final String SEEKING_SKULL_KEY = "SeekingSkull";
    public static final float SEEKING_SKULL_CHANCE = 0.2F;
    public static final EntityDataAccessor<Boolean> SEEKING_SKULL_DATA =
            EntityDataSerializers.BOOLEAN.createAccessor(80);
    public static final EntityDataAccessor<Integer> SEEKING_TARGET_ID =
            EntityDataSerializers.INT.createAccessor(81);
    /** 头衔9 坍缩力场：蓝色凋零之首爆炸后生成引力奇点 */
    public static final String SINGULARITY_SKULL_KEY = "SingularitySkull";
    /** 凋零之首正常碰撞/爆炸（非被外部吞掉） */
    public static final String SKULL_NATURAL_HIT_KEY = "NaturalHit";
    /** 头颅已被计入俯冲窗口，防止 remove + LeaveLevel 双计 */
    public static final String SKULL_DIVE_COUNTED_KEY = "DiveCounted";
    public static final String WITHER_OWNER_KEY = "WitherOwner";
    public static final String T6_FIRED_KEY = "T6Fired";
    /** 仆从凋灵骷髅寿命到期 gameTime（Goety setLimitedLife 同思路，0/缺失=永久，兼容旧存档） */
    public static final String WITHER_SERVANT_EXPIRE_KEY = "WitherServantExpire";
    /** 仆从默认寿命：1200 tick（60 秒），可由配置覆盖 */
    public static final int SERVANT_LIFETIME_TICKS = 1200;
    /** 仆从管理（消散/继承仇恨/反击）扫描半径与间隔 */
    public static final double SERVANT_MANAGE_RANGE = 32.0D;
    public static final int SERVANT_MANAGE_INTERVAL = 10;
    public static final String SMITE_EXPIRE_KEY = "SmiteExpire";
    /** T11 半血恢复独立压制 key（1 秒） */
    public static final String T11_SMITE_EXPIRE_KEY = "T11SmiteExpire";

    private static final Map<UUID, Long> SINGULARITY_MARK = new HashMap<>();
    /** 未定义凋灵受伤后剩余无敌 tick（服务端，不持久化） */
    private static final Map<UUID, Integer> HURT_DEFEND = new HashMap<>();
    /** 窗口激活时的 gameTime：激活当 tick 不硬拦，同 tick 多段伤害走每 tick 预算分摊 */
    private static final Map<UUID, Long> HURT_DEFEND_START_TICK = new HashMap<>();
    /** 每 tick 外来伤害预算剩余值（同 tick 多段共享 UNDEFINED_DAMAGE_CAP，先到先得，不读伤害来源） */
    private static final Map<UUID, Float> TICK_BUDGET_LEFT = new HashMap<>();
    /** 预算所属的 gameTime（跨 tick 自动重置为满额） */
    private static final Map<UUID, Long> TICK_BUDGET_TICK = new HashMap<>();

    /**
     * 隐藏真血（泽林 EXALTED 同思路）：正规 hurt()/heal() 才改这里。
     * 改血/直写先吃每 tick 20 点预算和 30 tick 无敌，再按适应系数扣真血、改回显示。
     */
    private static final Map<UUID, Float> HIDDEN_HP = new HashMap<>();
    /** 改血/直写适应段：0=100%，1=80%，2=60%，3+=10% */
    private static final Map<UUID, Integer> WRITE_ADAPT_STAGE = new HashMap<>();
    private static final float[] WRITE_ADAPT_FACTORS = {1.0F, 0.8F, 0.6F, 0.1F};
    /** 本 tick 已经按适应扣过真血：同 tick 的 setHealth + 数据层 + VarHandle 只计一次 */
    private static final Map<UUID, Long> WRITE_ADAPT_TICK = new HashMap<>();
    /** hurt()/heal() 进行中：数据层这次扣/加血算正规路径（按线程，避免集成端抢计数） */
    private static final ThreadLocal<Integer> LEGIT_WRITE_DEPTH = ThreadLocal.withInitial(() -> 0);
    /** 正在把 DATA_HEALTH 改回隐藏真血，限伤 mixin 放行 */
    private static final ThreadLocal<Boolean> DISPLAY_SYNC_ACTIVE = ThreadLocal.withInitial(() -> false);
    /** 本 mod 灵魂/纯粹恶意直写：全额扣隐藏真血，不进改血适应 */
    private static final ThreadLocal<Boolean> OWN_DIRECT_WRITE = ThreadLocal.withInitial(() -> false);

    /**
     * Reads the effective per-hit damage cap used by the target when one is
     * exposed by the target/mod. Minecraft itself has no common damage-cap API,
     * so this deliberately only accepts known cap-shaped methods/fields.
     */
    public static float getTargetDamageLimit(LivingEntity target) {
        if (target == null) return Float.POSITIVE_INFINITY;
        if (target instanceof Player player && StarCoronaCurios.hasSoulHeart(player)) return 20.0F;
        if (target instanceof WitherBoss wither && isUndefinedWither(wither)) {
            return WitherTitleConfig.hurtLimit();
        }

        String className = target.getClass().getName();
        if (className.contains("Apostle")) {
            Float configured = readStaticConfigLimit();
            if (configured != null) return configured;
        }

        Float methodLimit = readLimitMethod(target, target.getClass());
        if (methodLimit != null) return methodLimit;
        try {
            Object data = target.getEntityData();
            methodLimit = readLimitMethod(data, data.getClass());
            if (methodLimit != null) return methodLimit;
            Float fieldLimit = readLimitField(data, data.getClass());
            if (fieldLimit != null) return fieldLimit;
        } catch (Throwable ignored) {
        }
        Float fieldLimit = readLimitField(target, target.getClass());
        return fieldLimit != null ? fieldLimit : Float.POSITIVE_INFINITY;
    }

    private static Float readLimitMethod(Object instance, Class<?> type) {
        String[] names = {"getMaxDamagePertick", "getMaxDamagePerTick", "getDamageCap",
                "getMaxDamageTaken", "getHurtLimit", "getDamageLimit"};
        for (Class<?> clazz = type; clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
            for (String name : names) {
                try {
                    Method method = clazz.getDeclaredMethod(name);
                    method.setAccessible(true);
                    if (method.getParameterCount() != 0 || method.getReturnType() == void.class) continue;
                    Float value = asFiniteLimit(method.invoke(instance));
                    if (value != null) return value;
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static Float readLimitField(Object instance, Class<?> type) {
        String[] names = {"maxDamageTaken", "damageCap", "maxDamagePertick", "maxDamagePerTick",
                "hurtLimit", "damageLimit", "maxHurtDamage"};
        for (Class<?> clazz = type; clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
            for (String name : names) {
                try {
                    Field field = clazz.getDeclaredField(name);
                    if (Modifier.isStatic(field.getModifiers())) continue;
                    field.setAccessible(true);
                    Float value = asFiniteLimit(field.get(instance));
                    if (value != null) return value;
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static Float readStaticConfigLimit() {
        for (String className : new String[]{
                "z1gned.goetyrevelation.config.ModConfig",
                "goetyrevelation.config.ModConfig"}) {
            try {
                Class<?> config = Class.forName(className);
                Field field = config.getDeclaredField("APOLLYON_HURT_LIMIT");
                field.setAccessible(true);
                Object value = field.get(null);
                if (value instanceof Number number) return asFiniteLimit(number);
                Method get = value.getClass().getMethod("get");
                Float result = asFiniteLimit(get.invoke(value));
                if (result != null) return result;
            } catch (Throwable ignored) {
            }
        }
        return 20.0F;
    }

    private static Float asFiniteLimit(Object value) {
        if (!(value instanceof Number number)) return null;
        double amount = number.doubleValue();
        return Double.isFinite(amount) && amount > 0.0D && amount <= 1_000_000.0D
                ? (float) amount : null;
    }

    public static int getTitleId(WitherBoss wither) {
        return wither.getPersistentData().getInt(TITLE_KEY);
    }

    public static boolean hasTitle(WitherBoss wither, int id) {
        return getTitleId(wither) == id;
    }

    public static boolean hasTitle(WitherBoss wither) {
        return getTitleId(wither) >= MIN_TITLE;
    }

    public static void setTitleId(WitherBoss wither, int id) {
        wither.getPersistentData().putInt(TITLE_KEY, id);
    }

    public static Component getTitleName(int id) {
        return Component.translatable(LANG_PREFIX + id).withStyle(ChatFormatting.DARK_RED);
    }

    /**
     * 未定义凋灵血条名的纯文本形态，必须与 getUndefinedDisplayName 的字面文本保持一致。
     * 客户端血条识别（UndefinedWitherBossBar）与外部 mod 血条守卫
     * （EnhancedBossBarsUndefinedGuardMixin）共用这一处判定。
     */
    public static final String UNDEFINED_BAR_TEXT = "?凋灵?";

    /** 这管血条是不是未定义凋灵的（按显示名文本判定） */
    public static boolean isUndefinedBarName(Component name) {
        return name != null && UNDEFINED_BAR_TEXT.equals(name.getString());
    }

    /**
     * 未定义凋灵动态流动渐变存活名 "?凋灵?"。
     * getDisplayName 拦截（EntityGetDisplayNameMixin）与 BOSS 血条每 tick 更新共用。
     */
    public static MutableComponent getUndefinedDisplayName() {
        Style fontStyle = Style.EMPTY.withBold(true).withItalic(false);
        long tick = System.currentTimeMillis() / 50; // 速度加倍

        // "?凋灵?" 共 4 个字符，位置 0 和 3 固定白色，1-2 为"凋灵"部分流动渐变
        MutableComponent result = Component.literal("");
        String text = UNDEFINED_BAR_TEXT;
        int n = 2; // "凋灵"共 2 个字符
        for (int i = 0; i < text.length(); i++) {
            if (i == 0 || i == 3) {
                result.append(Component.literal(String.valueOf(text.charAt(i)))
                        .withStyle(fontStyle.withColor(0xFFFFFF)));
            } else {
                int j = i - 1;
                float t = ((tick + j * (20 / n)) % 20) / 20.0f;
                int color = gradient4Color(t);
                result.append(Component.literal(String.valueOf(text.charAt(i)))
                        .withStyle(fontStyle.withColor(color)));
            }
        }
        return result;
    }

    /** 渐变颜色: #07C0F2 -> #4CE2F2 -> #B9E8EB -> #707CE3 循环插值 */
    public static int gradient4Color(float t) {
        int c1 = 0x07C0F2, c2 = 0x4CE2F2, c3 = 0xB9E8EB, c4 = 0x707CE3;
        if (t < 1.0f / 4.0f) return lerpColor(c1, c2, t * 4.0f);
        if (t < 2.0f / 4.0f) return lerpColor(c2, c3, (t - 1.0f / 4.0f) * 4.0f);
        if (t < 3.0f / 4.0f) return lerpColor(c3, c4, (t - 2.0f / 4.0f) * 4.0f);
        return lerpColor(c4, c1, (t - 3.0f / 4.0f) * 4.0f);
    }

    public static Component getFullDisplayName(int id) {
        return Component.translatable("entity.minecraft.wither").append("·").append(getTitleName(id));
    }

    public static boolean isHurtDefendActive(WitherBoss wither) {
        return HURT_DEFEND.getOrDefault(wither.getUUID(), 0) > 0;
    }

    /**
     * 窗口是否进入"硬拦"阶段：激活当 tick 放行（同 tick 多段伤害——如 Fantasy-Ending 的
     * 预打击 + 主攻击——由每 tick 预算统一分摊，不做来源判断），之后的 tick 才拦截一切扣血。
     */
    public static boolean isHurtDefendBlocking(WitherBoss wither) {
        if (!isHurtDefendActive(wither)) return false;
        Long start = HURT_DEFEND_START_TICK.get(wither.getUUID());
        return start == null || wither.level().getGameTime() > start;
    }

    public static void activateHurtDefend(WitherBoss wither) {
        if (wither.level().isClientSide) return;
        HURT_DEFEND.put(wither.getUUID(), WitherTitleConfig.hurtDefendTicks());
        HURT_DEFEND_START_TICK.put(wither.getUUID(), wither.level().getGameTime());
    }

    /** 本 tick 剩余外来伤害预算：同 tick 多段伤害共享 UNDEFINED_DAMAGE_CAP，先到先得 */
    public static float getRemainingTickBudget(WitherBoss wither) {
        Long tick = TICK_BUDGET_TICK.get(wither.getUUID());
        if (tick == null || tick != wither.level().getGameTime()) return WitherTitleConfig.hurtLimit();
        return TICK_BUDGET_LEFT.getOrDefault(wither.getUUID(), WitherTitleConfig.hurtLimit());
    }

    public static void consumeTickBudget(WitherBoss wither, float amount) {
        if (amount <= 0.0F) return;
        TICK_BUDGET_TICK.put(wither.getUUID(), wither.level().getGameTime());
        float left = getRemainingTickBudget(wither) - amount;
        TICK_BUDGET_LEFT.put(wither.getUUID(), Math.max(0.0F, left));
    }

    public static void tickHurtDefend(WitherBoss wither) {
        HURT_DEFEND.computeIfPresent(wither.getUUID(), (k, v) -> v <= 1 ? null : v - 1);
    }

    public static void clearHurtDefend(WitherBoss wither) {
        HURT_DEFEND.remove(wither.getUUID());
        HURT_DEFEND_START_TICK.remove(wither.getUUID());
        TICK_BUDGET_LEFT.remove(wither.getUUID());
        TICK_BUDGET_TICK.remove(wither.getUUID());
    }

    public static boolean isLegitWrite() {
        return LEGIT_WRITE_DEPTH.get() > 0;
    }

    public static void beginLegitWrite() {
        LEGIT_WRITE_DEPTH.set(LEGIT_WRITE_DEPTH.get() + 1);
    }

    public static void endLegitWrite() {
        int depth = LEGIT_WRITE_DEPTH.get();
        if (depth <= 1) {
            LEGIT_WRITE_DEPTH.remove();
        } else {
            LEGIT_WRITE_DEPTH.set(depth - 1);
        }
    }

    public static boolean isDisplaySyncActive() {
        return DISPLAY_SYNC_ACTIVE.get();
    }

    public static boolean isOwnDirectWrite() {
        return Boolean.TRUE.equals(OWN_DIRECT_WRITE.get());
    }

    public static void beginOwnDirectWrite() {
        OWN_DIRECT_WRITE.set(true);
    }

    public static void endOwnDirectWrite() {
        OWN_DIRECT_WRITE.remove();
    }

    public static void initHiddenHp(WitherBoss wither, float health) {
        if (wither.level().isClientSide) return;
        HIDDEN_HP.put(wither.getUUID(), clampHidden(wither, health));
    }

    public static float getHiddenHp(WitherBoss wither) {
        Float hidden = HIDDEN_HP.get(wither.getUUID());
        if (hidden != null) return hidden;
        float fallback = wither.getMaxHealth();
        try {
            fallback = wither.getEntityData().get(com.enchantmentSkill.mixin.LivingEntityAccessor.shatter$getDataHealthId());
        } catch (Throwable ignored) {
        }
        if (!wither.level().isClientSide) {
            HIDDEN_HP.put(wither.getUUID(), clampHidden(wither, fallback));
        }
        return fallback;
    }

    public static void setHiddenHp(WitherBoss wither, float health) {
        if (wither.level().isClientSide) return;
        HIDDEN_HP.put(wither.getUUID(), clampHidden(wither, health));
    }

    public static void applyHiddenDamage(WitherBoss wither, float amount) {
        if (amount <= 0.0F) return;
        setHiddenHp(wither, getHiddenHp(wither) - amount);
    }

    public static void applyHiddenHeal(WitherBoss wither, float amount) {
        if (amount <= 0.0F) return;
        setHiddenHp(wither, getHiddenHp(wither) + amount);
    }

    public static float getWriteAdaptFactor(WitherBoss wither) {
        int stage = WRITE_ADAPT_STAGE.getOrDefault(wither.getUUID(), 0);
        if (stage < 0) stage = 0;
        if (stage >= WRITE_ADAPT_FACTORS.length) return WRITE_ADAPT_FACTORS[WRITE_ADAPT_FACTORS.length - 1];
        return WRITE_ADAPT_FACTORS[stage];
    }

    public static void noteIllicitWriteHit(WitherBoss wither) {
        if (wither.level().isClientSide) return;
        WRITE_ADAPT_STAGE.merge(wither.getUUID(), 1, Integer::sum);
    }

    /**
     * 改血/直写：先套旧版每 tick 20 点预算 + 30 tick 无敌，再按适应系数扣隐藏真血，最后把 DATA_HEALTH 改回真血。
     * 同一 gameTime 只适应一次（setHealth 与 VarHandle 常成对出现）。
     * 本 mod 灵魂伤害/纯粹恶意直写仍全额扣真血，不进预算。
     */
    public static float applyIllicitWriteAndRestore(WitherBoss wither, float illicitDrop) {
        if (illicitDrop > 0.0F) {
            if (isOwnDirectWrite()) {
                applyHiddenDamage(wither, illicitDrop);
            } else if (isHurtDefendBlocking(wither)) {
                restoreDisplayHealth(wither, getHiddenHp(wither));
                return getHiddenHp(wither);
            } else {
                float allowed = Math.min(illicitDrop, getRemainingTickBudget(wither));
                if (allowed > 0.0F) {
                    long now = wither.level().getGameTime();
                    Long last = WRITE_ADAPT_TICK.get(wither.getUUID());
                    if (last == null || last != now) {
                        applyHiddenDamage(wither, allowed * getWriteAdaptFactor(wither));
                        noteIllicitWriteHit(wither);
                        WRITE_ADAPT_TICK.put(wither.getUUID(), now);
                    }
                    consumeTickBudget(wither, allowed);
                    activateHurtDefend(wither);
                }
            }
        }
        float hidden = getHiddenHp(wither);
        restoreDisplayHealth(wither, hidden);
        return hidden;
    }

    public static void restoreDisplayHealth(WitherBoss wither, float hidden) {
        if (wither.level().isClientSide) return;
        DISPLAY_SYNC_ACTIVE.set(true);
        try {
            wither.getEntityData().set(
                    com.enchantmentSkill.mixin.LivingEntityAccessor.shatter$getDataHealthId(), hidden);
        } catch (Throwable ignored) {
        } finally {
            DISPLAY_SYNC_ACTIVE.remove();
        }
    }

    public static void clearHiddenHp(WitherBoss wither) {
        UUID id = wither.getUUID();
        HIDDEN_HP.remove(id);
        WRITE_ADAPT_STAGE.remove(id);
        WRITE_ADAPT_TICK.remove(id);
    }

    private static float clampHidden(WitherBoss wither, float health) {
        float cap = WitherTitleConfig.maxHealth();
        if (Float.isNaN(health)) return cap;
        return Math.max(0.0F, Math.min(health, cap));
    }

    public static void enforceMaxHealth(WitherBoss wither) {
        if (wither.level().isClientSide) return;
        var maxHealth = wither.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (maxHealth == null) return;
        double cap = WitherTitleConfig.maxHealth();
        if (maxHealth.getBaseValue() != cap) {
            maxHealth.setBaseValue(cap);
        }
        for (var modifier : new java.util.ArrayList<>(maxHealth.getModifiers())) {
            if (modifier.getAmount() < 0.0D) {
                maxHealth.removeModifier(modifier);
            }
        }
    }

    private static final class ExternalHealthBridge {
        private static final Method GET_DELTA;
        private static final Field DELTA_DATA;

        static {
            Method get = null;
            Field data = null;
            try {
                Class<?> asm = Class.forName("com.mega.uom.util.entity.EntityASMUtil");
                get = asm.getMethod("getHealthDelta", LivingEntity.class);
                data = asm.getField("FE_GET_HEALTH_DATA");
            } catch (Throwable ignored) {
            }
            GET_DELTA = get;
            DELTA_DATA = data;
        }
    }

    /**
     * 外部 Mod 把 getHealth 钳成 min(DATA_HEALTH, maxHealth + delta)。
     * Jade 上的负数血量来自 delta，不是 DATA_HEALTH。
     */
    public static float getExternalHealthDelta(LivingEntity entity) {
        if (ExternalHealthBridge.GET_DELTA == null) return 0.0F;
        try {
            Object value = ExternalHealthBridge.GET_DELTA.invoke(null, entity);
            return value instanceof Float f ? f : 0.0F;
        } catch (Throwable ignored) {
            return 0.0F;
        }
    }

    @SuppressWarnings("unchecked")
    public static void clearExternalHealthDelta(LivingEntity entity) {
        if (entity.level().isClientSide) return;
        try {
            if (ExternalHealthBridge.DELTA_DATA != null) {
                Object accessor = ExternalHealthBridge.DELTA_DATA.get(null);
                if (accessor instanceof EntityDataAccessor<?> data) {
                    entity.getEntityData().set((EntityDataAccessor<Float>) data, 0.0F);
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            Class<?> ec = Class.forName("com.mega.uom.util.itf.LivingEntityEC");
            if (ec.isInstance(entity)) {
                Object ctx = ec.getMethod("uom$livingECData").invoke(entity);
                if (ctx != null) {
                    ctx.getClass().getField("isDead").setBoolean(ctx, false);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** 与 外部 Mod special_getHealth 同一口径：min(DATA_HEALTH, maxHealth + delta) */
    public static float getEffectiveHealth(WitherBoss wither) {
        float dataHealth;
        try {
            dataHealth = wither.getEntityData().get(
                    com.enchantmentSkill.mixin.LivingEntityAccessor.shatter$getDataHealthId());
        } catch (Throwable t) {
            return Float.NaN;
        }
        return Math.min(dataHealth, wither.getMaxHealth() + getExternalHealthDelta(wither));
    }

    public static boolean isVanillaWitherSkull(WitherSkull skull) {
        return skull != null && skull.getClass() == WitherSkull.class;
    }

    public static boolean isSeekingSkull(WitherSkull skull) {
        if (isVanillaWitherSkull(skull)) {
            try {
                if (skull.getEntityData().get(SEEKING_SKULL_DATA)) return true;
            } catch (Exception ignored) {
            }
        }
        return skull.getPersistentData().getBoolean(SEEKING_SKULL_KEY);
    }

    public static Entity getSeekingTarget(WitherSkull skull) {
        if (isVanillaWitherSkull(skull)) {
            try {
                int id = skull.getEntityData().get(SEEKING_TARGET_ID);
                if (id > 0) {
                    Entity byId = skull.level().getEntity(id);
                    if (byId != null && byId.isAlive()) return byId;
                }
            } catch (Exception ignored) {
            }
        }
        if (skull.level().isClientSide) return null;
        if (!(skull.level() instanceof ServerLevel serverLevel)) return null;
        if (!skull.getPersistentData().hasUUID(SKULL_TARGET_KEY)) return null;
        Entity byUuid = serverLevel.getEntity(skull.getPersistentData().getUUID(SKULL_TARGET_KEY));
        if (byUuid != null && byUuid.isAlive() && isVanillaWitherSkull(skull)) {
            try {
                skull.getEntityData().set(SEEKING_TARGET_ID, byUuid.getId());
            } catch (Exception ignored) {
            }
        }
        return byUuid;
    }

    /** 未定义凋灵单次入魂伤害上限（配置项，默认 200） */
    public static float capIncomingSoulDamage(WitherBoss wither, float amount) {
        if (amount <= 0.0F) return amount;
        return Math.min(amount, WitherTitleConfig.soulHurtLimit());
    }

    public static boolean isDiveGhost(WitherBoss wither) {
        try {
            return wither.getEntityData().get(DIVE_GHOST);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void setDiveGhost(WitherBoss wither, boolean ghost) {
        if (wither.level().isClientSide) return;
        try {
            wither.getEntityData().set(DIVE_GHOST, ghost);
        } catch (Throwable ignored) {
        }
    }
    public static boolean isUndefinedWither(WitherBoss wither) {
        // 实体 Tag 会随实体生成/同步包发送到客户端。未定义凋灵的专属 NBT
        // 只保存在服务端，客户端仅靠 NBT 判定会把它当作普通凋灵，导致
        // 幽蓝皮肤、神环和环境穹顶全部不渲染。
        if (wither.getTags().contains("undefined_wither")) return true;
        // 优先使用数据同步器（高效、客户端同步）
        // 降级到 NBT 持久化数据（兼容旧存档）
        try {
            if (wither.getEntityData().get(UNDEFINED_DATA)) return true;
        } catch (Exception ignored) {
            // 数据同步器尚未初始化时（如构造阶段），忽略异常
        }
        return wither.getPersistentData().getBoolean(UNDEFINED_NBT_KEY);
    }

    public static boolean isWitherServant(Entity entity, UUID ownerUUID) {
        if (!(entity instanceof WitherSkeleton skeleton) || ownerUUID == null) return false;
        // 1.20.1 的 CompoundTag#getUUID 对不存在的键会直接 NPE（NbtUtils.loadUUID 不做空值检查），
        // 普通凋灵骷髅身上没有 WitherOwner，必须先判存在再读。
        if (!skeleton.getPersistentData().hasUUID(WITHER_OWNER_KEY)) return false;
        return ownerUUID.equals(skeleton.getPersistentData().getUUID(WITHER_OWNER_KEY));
    }

    public static void markSingularity(LivingEntity entity, int durationTicks) {
        if (entity.level().isClientSide) return;
        SINGULARITY_MARK.put(entity.getUUID(), entity.level().getGameTime() + durationTicks);
    }

    public static boolean isMarkedBySingularity(LivingEntity entity) {
        Long expire = SINGULARITY_MARK.get(entity.getUUID());
        if (expire == null) return false;
        if (entity.level().getGameTime() > expire) {
            SINGULARITY_MARK.remove(entity.getUUID());
            return false;
        }
        return true;
    }

    public static void cleanupStaleMarks(long gameTime) {
        Iterator<Map.Entry<UUID, Long>> it = SINGULARITY_MARK.entrySet().iterator();
        while (it.hasNext()) {
            if (gameTime > it.next().getValue()) it.remove();
        }
    }

    // ==================== 半血治疗压制（未定义凋灵） ====================
    /** 普通治疗路径（heal()/再生效果/吸血/自然回血）的压制系数：回血 -50% */
    public static final float HEAL_SUPPRESSION_FACTOR = 0.5F;
    /** 直写血量路径（setHealth()/SynchedEntityData 数据层写入）的压制系数：回血 -80% */
    public static final float HEAL_SUPPRESSION_DATA_FACTOR = 0.2F;
    /** 治疗压制持续时长：2 秒（40 tick），每次被攻击刷新 */
    public static final int HEAL_SUPPRESSION_TICKS = 40;
    /** 目标 UUID -> 压制到期 gameTime（服务端，不持久化） */
    private static final Map<UUID, Long> HEAL_SUPPRESSION = new HashMap<>();
    /**
     * heal() 路径写入进行中标记（服务端单线程）：
     * heal 层已按 -50% 削减，随后的 setHealth→数据层写入凭此标记放行，避免双重叠加。
     */
    public static boolean healPathActive = false;

    /** 未定义凋灵是否已进入半血（血量 <= 上限一半）——半血后其攻击才附加治疗压制 */
    public static boolean isAtOrBelowHalfHealth(WitherBoss wither) {
        return wither.getHealth() <= wither.getMaxHealth() * 0.5F;
    }

    /** 标记目标进入治疗压制窗口（2s），每次命中刷新到期时间 */
    public static void markHealSuppression(LivingEntity target) {
        if (target.level().isClientSide) return;
        long now = target.level().getGameTime();
        HEAL_SUPPRESSION.put(target.getUUID(), now + HEAL_SUPPRESSION_TICKS);
        if (HEAL_SUPPRESSION.size() > 256) {
            HEAL_SUPPRESSION.entrySet().removeIf(e -> e.getValue() <= now);
        }
    }

    /** 目标是否处于治疗压制窗口内；过期即惰性清理 */
    public static boolean isHealSuppressed(LivingEntity entity) {
        if (entity.level().isClientSide) return false;
        Long expire = HEAL_SUPPRESSION.get(entity.getUUID());
        if (expire == null) return false;
        if (entity.level().getGameTime() > expire) {
            HEAL_SUPPRESSION.remove(entity.getUUID());
            return false;
        }
        return true;
    }

    /** 普通治疗（heal() 路径）压制系数：压制中 0.5（回血 -50%），否则 1.0 */
    public static float getHealSuppressionFactor(LivingEntity entity) {
        return isHealSuppressed(entity) ? HEAL_SUPPRESSION_FACTOR : 1.0F;
    }

    /** 直写血量（setHealth()/数据层）压制系数：压制中 0.2（回血 -80%），否则 1.0 */
    public static float getHealSuppressionDataFactor(LivingEntity entity) {
        return isHealSuppressed(entity) ? HEAL_SUPPRESSION_DATA_FACTOR : 1.0F;
    }

    public static void clearHealSuppression(LivingEntity entity) {
        HEAL_SUPPRESSION.remove(entity.getUUID());
    }

    public static void spawnSphereParticles(ServerLevel level, Entity center, ParticleOptions option,
                                            double radius, int count, double speed) {
        double cy = center.getY() + center.getBbHeight() / 2.0D;
        for (int i = 0; i < count; i++) {
            double theta = level.getRandom().nextDouble() * Math.PI * 2.0D;
            double phi = Math.acos(2.0D * level.getRandom().nextDouble() - 1.0D);
            double px = radius * Math.sin(phi) * Math.cos(theta);
            double py = radius * Math.sin(phi) * Math.sin(theta);
            double pz = radius * Math.cos(phi);
            level.sendParticles(option, center.getX() + px, cy + py, center.getZ() + pz,
                    1, 0.0D, 0.0D, 0.0D, speed);
        }
    }

    /**
     * 生成循环流动渐变文本（和无垠长魂之剑一样的动画效果）。
     * 每个字符独立偏移相位，产生从左向右流动的动画。
     * 四色版本。
     */
    public static MutableComponent gradientTextAnimated(String text, int c1, int c2, int c3, int c4, Style baseStyle) {
        if (text.isEmpty()) return Component.literal("");

        long tick = System.currentTimeMillis() / 50; // 速度加倍
        MutableComponent result = Component.literal("");
        int n = Math.max(text.length(), 1);

        for (int i = 0; i < text.length(); i++) {
            float t = ((tick + i * (20 / n)) % 20) / 20.0f;
            int color = lerp4Color(c1, c2, c3, c4, t);
            result.append(Component.literal(String.valueOf(text.charAt(i)))
                    .withStyle(baseStyle.withColor(color)));
        }
        return result;
    }

    /** 三阶颜色循环插值 */
    private static int lerp3Color(int c1, int c2, int c3, float t) {
        if (t < 1.0f / 3.0f) {
            return lerpColor(c1, c2, t * 3.0f);
        } else if (t < 2.0f / 3.0f) {
            return lerpColor(c2, c3, (t - 1.0f / 3.0f) * 3.0f);
        } else {
            return lerpColor(c3, c1, (t - 2.0f / 3.0f) * 3.0f);
        }
    }

    /** 四阶颜色循环插值 */
    private static int lerp4Color(int c1, int c2, int c3, int c4, float t) {
        if (t < 1.0f / 4.0f) {
            return lerpColor(c1, c2, t * 4.0f);
        } else if (t < 2.0f / 4.0f) {
            return lerpColor(c2, c3, (t - 1.0f / 4.0f) * 4.0f);
        } else if (t < 3.0f / 4.0f) {
            return lerpColor(c3, c4, (t - 2.0f / 4.0f) * 4.0f);
        } else {
            return lerpColor(c4, c1, (t - 3.0f / 4.0f) * 4.0f);
        }
    }

    /** 插值两个 RGB 颜色 */
    private static int lerpColor(int from, int to, float t) {
        int fr = (from >> 16) & 0xFF, fg = (from >> 8) & 0xFF, fb = from & 0xFF;
        int tr = (to >> 16) & 0xFF, tg = (to >> 8) & 0xFF, tb = to & 0xFF;
        int r = (int) (fr + (tr - fr) * t);
        int g = (int) (fg + (tg - fg) * t);
        int b = (int) (fb + (tb - fb) * t);
        return (r << 16) | (g << 8) | b;
    }
}
