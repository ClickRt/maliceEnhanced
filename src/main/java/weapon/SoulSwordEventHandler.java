package weapon;

import com.enchantmentSkill.mixin.LivingEntityAccessor;
import com.enchantmentSkill.health.HealthDeltaBridge;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weapon.net.NetworkHandler;
import weapon.net.S2CSoulSwordTotemPack;
import weapon.starcrystal.StarCoronaCrystalEvents;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SoulSwordEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("SoulSword");

    private static final ThreadLocal<Boolean> REFLECTING = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> VOID_DAMAGING = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> SOUL_DAMAGING = ThreadLocal.withInitial(() -> false);
    /** 音波反馈守卫：soulHitFeedback 附加的 1 点音波 hurt 不再触发无垠长魂之剑近战链 */
    private static final ThreadLocal<Boolean> SONIC_FEEDBACK = ThreadLocal.withInitial(() -> false);
    private static volatile boolean SPECIAL_HURT_CHECKED;
    private static volatile Constructor<?> SPECIAL_HURT_CTOR;
    private static volatile Method SPECIAL_HURT_METHOD;
    private static volatile Method SPECIAL_INDEX_TYPE;
    private static final Map<UUID, int[]> ARMOR_SNAPSHOT = new HashMap<>();

    /** 已被判定为 OP 级 Boss 的实体：一旦达标永久记住，避免被无垠长魂之剑的 -10 最大生命削到阈值以下后灵魂伤害断档 */
    private static final Set<UUID> OP_BOSS_QUALIFIED = new HashSet<>();

    /** 灵魂伤害：无视所有 OP 级抗性/限伤标签的专属伤害类型 */
    private static final ResourceKey<DamageType> SOUL_DAMAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE,
                    ResourceLocation.fromNamespaceAndPath("malice_enhanced", "damage_soul"));

    /** OP 级 Boss 判定阈值（最大生命 >= 300） */
    private static final float OP_BOSS_MIN_MAX_HEALTH = 300.0F;
    /** 每刀附加对方最大生命 2% 的灵魂伤害 */
    private static final float SOUL_DAMAGE_PERCENT = 0.02F;
    /** 普通攻击附带的治疗压制系数（0.5 = 治疗 -50%） */
    private static final float SOUL_HEAL_SUPPRESSION_FACTOR = 0.5F;
    /** 伤害适应：格挡期间同一伤害类型反复命中时，在 99% 减伤基础上额外减免，每次叠加 20% */
    private static final float ADAPTATION_STEP = 0.2F;
    /** 伤害适应上限：最高额外减免 80% */
    private static final float MAX_ADAPTATION = 0.8F;
    /** 伤害适应窗口：20 秒（400 tick），超过未再命中则恢复普通格挡减伤流程 */
    private static final long ADAPTATION_WINDOW_TICKS = 400L;
    /** 直写改血回滚时允许的误差 */
    private static final float RESTORE_EPSILON = 0.001F;

    /**
     * 泛用隐藏真血探针缓存：目标类名 → 其真正的血量 DataItem（EXALTED/独立字段型真血，
     * 如无名者 CURRENT_COMBAT_HEALTH）。NOT_HIDDEN 表示该目标就用原版 DATA_HEALTH、无隐藏血存储。
     * EntityDataAccessor 是静态字段，探针结果可跨同类实例复用，故按类缓存、只探一次。
     */
    private static final Map<String, HiddenHealthRef> HIDDEN_HEALTH_CACHE = new HashMap<>();
    private static final HiddenHealthRef NOT_HIDDEN = new HiddenHealthRef(null, true);
    /** 探针缓存条目上限：仅按实体类去重，理论很小；超限整体清空当作廉价失效，防止病态增长 */
    private static final int HIDDEN_HEALTH_CACHE_CAP = 256;

    /** 探针锁定的隐藏真血存储引用（血量所在 DataItem 的 accessor + 是否 Float） */
    private static final class HiddenHealthRef {
        final EntityDataAccessor<?> accessor;
        final boolean isFloat;
        HiddenHealthRef(EntityDataAccessor<?> accessor, boolean isFloat) {
            this.accessor = accessor;
            this.isFloat = isFloat;
        }
    }

    @SubscribeEvent
    public static void onBlackHoleJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) return;
        Entity entity = event.getEntity();
        if (!(entity instanceof SoulSwordBlackHoleAccessor accessor)) return;
        if (accessor.soulSword$getColor() != 0) return;
        if (!(entity instanceof Projectile projectile)) return;
        if (!(projectile.getOwner() instanceof Player player)) return;
        if (!IronSpellsCompat.isSoulSwordCast(player) && !hasSoulSwordInHands(player)) return;
        accessor.soulSword$setColor(0x0412FC);
        LOGGER.info("[SoulSwordBlackHole] marked blue on join owner={}", player.getGameProfile().getName());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurtSnapshot(LivingHurtEvent event) {
        if (event.getEntity() instanceof Player player
                && player.isUsingItem()
                && player.getUseItem().getItem() instanceof SoulSwordItem) {
            int[] damages = new int[4];
            var armorInv = player.getInventory().armor;
            for (int i = 0; i < 4; i++) {
                damages[i] = armorInv.get(i).getDamageValue();
            }
            ARMOR_SNAPSHOT.put(player.getUUID(), damages);
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            // 自定义受击无敌帧（soulHurtDefend）：窗口内所有伤害事件直接归零
            if (isSoulHurtDefendActive(player)) {
                event.setAmount(0.0F);
            }
            // 只有正常伤害事件结算的伤害会被"直写回滚"视为已解释；格挡时累计
            if (isBlockingSoulSword(player)) {
                EXPLAINED_DAMAGE.merge(player.getUUID(), event.getAmount(), Float::sum);
            }
            int[] damages = ARMOR_SNAPSHOT.remove(player.getUUID());
            if (damages != null) {
                var armorInv = player.getInventory().armor;
                for (int i = 0; i < 4; i++) {
                    ItemStack armor = armorInv.get(i);
                    if (!armor.isEmpty()) {
                        armor.setDamageValue(damages[i]);
                    }
                }
            }
        }
    }

    /**
     * 伤害适应计数：正常 hurt() 与 RevelationFix 的 EntityActuallyHurt
     * （炼狱结界那半）两条路径都只触发一次 LivingAttackEvent，
     * 在这里按"伤害类型"独立累计命中次数（仅格挡无垠长魂之剑时生效）。
     * 首次命中计数为 1，之后每次 +1，配合 onLivingHurt 里的 20%/40%/…/80% 额外减免。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        // 自定义受击无敌帧：窗口内直接取消攻击事件（伤害不进入结算）
        if (isSoulHurtDefendActive(player)) {
            event.setCanceled(true);
            return;
        }
        if (!isBlockingSoulSword(player)) return;
        // 亚波伦机关箭在 onLivingHurt 阶段统一计数（保证按命中次数适应），这里跳过避免重复计
        if (isDoomArrowSource(event.getSource())) return;
        recordAdaptationHit(player, event.getSource());
    }

    /**
     * 亚波伦机关箭（DeathArrow）命中后还会走 heal(-5%最大生命) 直扣血量，
     * 该路径绕过所有伤害事件，格挡时直接拦截负治疗。
     */
    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        if (event.getAmount() >= 0.0F) return;
        if (event.getEntity() instanceof Player player) {
            // 格挡期间或自定义受击无敌帧（soulHurtDefend）窗口内，禁止负治疗直扣血
            if (isBlockingSoulSword(player) || isSoulHurtDefendActive(player)) {
                event.setAmount(0.0F);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!hasSoulSword(player)) return;

        if (TOTEM_COOLDOWNS.getOrDefault(player.getUUID(), 0) > 0) return;

        event.setCanceled(true);
        player.setHealth(1.0F);
        player.removeAllEffects();
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 3000, 3));
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 6000, 4));

        TOTEM_COOLDOWNS.put(player.getUUID(), 1200);
        SOUL_HURT_DEFEND.put(player.getUUID(), 600);
        player.invulnerableTime = 40;

        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new S2CSoulSwordTotemPack(new ItemStack(WeaponItems.SOUL_SWORD.get()), player.getId(), 600));
    }

    /**
     * 实体离开世界时的静态表清理，防止按 UUID 累加的静态 Map/Set 慢性泄漏。
     *  - 客户端：只清 CLIENT_SOUL_HURT_DEFEND（粒子特效计时）。
     *  - 服务端玩家离场（登出/换维）：清掉所有以玩家 UUID 为 key 的格挡/复活状态。
     *  - OP 级缓存是可重算的布尔值，任何离开都丢弃。
     *  - MAX_HP_REDUCTIONS 与实体身上的 permanent 属性修饰符绑定，区块卸载(UNLOADED)
     *    后实体会原样回来、需要保留累计值，故只在真正消失(KILLED/DISCARDED)时丢弃，
     *    否则重载后下一刀会把已堆叠的最大生命削减重置。无垠长魂之剑的独立表同步清理。
     */
    @SubscribeEvent
    public static void onEntityLeaveCleanup(EntityLeaveLevelEvent event) {
        Entity entity = event.getEntity();
        UUID id = entity.getUUID();
        if (event.getLevel().isClientSide()) {
            CLIENT_SOUL_HURT_DEFEND.remove(id);
            return;
        }
        StarFantasyHadesBan.onEntityLeave(entity, entity.getRemovalReason());
        if (entity instanceof Player) {
            SOUL_HURT_DEFEND.remove(id);
            TOTEM_COOLDOWNS.remove(id);
            ADAPTATION.remove(id);
            BLOCK_HEALTH_SNAPSHOT.remove(id);
            EXPLAINED_DAMAGE.remove(id);
            ARMOR_SNAPSHOT.remove(id);
        }
        OP_BOSS_QUALIFIED.remove(id);
        Entity.RemovalReason reason = entity.getRemovalReason();
        if (reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED) {
            MAX_HP_REDUCTIONS.remove(id);
            SoulBowArrow.clearMaxHpReduction(id);
        }
    }

    @SubscribeEvent
    public static void onShieldBlock(ShieldBlockEvent event) {
        if (event.getEntity() instanceof Player player
                && player.isUsingItem()
                && player.getUseItem().getItem() instanceof SoulSwordItem) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        // 自定义受击无敌帧：兜底归零（部分伤害路径不触发 LivingAttackEvent）
        if (isSoulHurtDefendActive(player)) {
            event.setAmount(0.0F);
            return;
        }
        if (!hasSoulSword(player)) return;

        if (isBlockingSoulSword(player)) {
            float originalDamage = event.getAmount();
            // 机关箭的适应计数放这里：箭伤确实走 LivingHurtEvent（99% 生效），
            // 但部分情况 LivingAttackEvent 不可靠，按命中次数计数必须在可结算处进行
            if (isDoomArrowSource(event.getSource())) {
                recordAdaptationHit(player, event.getSource());
            }
            // 伤害适应：同一伤害类型第二次命中起，在 99% 基础上再减 20%/40%/…/80%
            float adaptationReduction = getAdaptationReduction(player, event.getSource());
            event.setAmount(originalDamage * 0.01f * (1.0f - adaptationReduction));
            player.level().playSound(null, player.blockPosition(),
                    WeaponSounds.SWORD_PARRY.get(), SoundSource.PLAYERS, 0.8f, 1.0f);

            if (!REFLECTING.get()) {
                DamageSource source = event.getSource();
                Entity attacker = source.getEntity();
                // 不对 Apostle（亚波伦）反弹：炼狱结界的伤害来源是 Boss 自身，
                // 反弹会形成反馈，甚至触发其"无来源虚空伤害→清除自身"的判定
                if (attacker != null && !isApostleAttacker(attacker)) {
                    REFLECTING.set(true);
                    try {
                        attacker.hurt(source, originalDamage * 0.5f);
                    } finally {
                        REFLECTING.set(false);
                    }
                }
            }
        } else {
            event.setAmount(event.getAmount() * 0.5f);
        }
    }

    @SubscribeEvent
    public static void onMobEffectApplicable(MobEffectEvent.Applicable event) {
        if (event.getEntity() instanceof Player player
                && event.getEffectInstance().getEffect().getCategory() == MobEffectCategory.HARMFUL
                && hasSoulSword(player)) {
            event.setResult(Event.Result.DENY);
        }
        // 抗性压制：目标身上有抗性压制效果时，阻止其获得抗性提升（Resistance）
        if (event.getEffectInstance().getEffect() == MobEffects.DAMAGE_RESISTANCE
                && event.getEntity().hasEffect(WeaponRegistries.RESISTANCE_BLOCKADE.get())) {
            event.setResult(Event.Result.DENY);
        }
    }

    /** 每 tick 检测：目标身上有抗性压制 且 同时有抗性提升时，立即清除抗性提升 */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (entity.hasEffect(WeaponRegistries.RESISTANCE_BLOCKADE.get())
                && entity.hasEffect(MobEffects.DAMAGE_RESISTANCE)) {
            entity.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        }
    }

    private static final UUID ARMOR_UUID = UUID.fromString("b1c2d3e4-f5a6-7890-bcde-f12345678901");
    private static final UUID TOUGHNESS_UUID = UUID.fromString("c1d2e3f4-a5b6-7890-cdef-123456789012");
    private static final UUID SPEED_UUID = UUID.fromString("d1e2f3a4-b5c6-7890-defa-123456789013");
    private static final UUID MAX_HP_REDUCTION_UUID = UUID.fromString("e1f2a3b4-c5d6-7890-efab-123456789014");
    private static final UUID KB_RESIST_UUID = UUID.fromString("f1a2b3c4-d5e6-7890-fabc-123456789015");

    private static final ResourceKey<DamageType> SOUL_SWORD_DAMAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE,
                    ResourceLocation.fromNamespaceAndPath("malice_enhanced", "soul_sword"));
    private static final String LEVIATHAN_TYPE = "cataclysm:the_leviathan";
    private static final String APOLLYON_SUMMON_TYPE = "goety_revelation:summon_apollyon";
    private static final String TEST_DUMMY_TYPE = "dummmmmmy:target_dummy";
    private static final ResourceLocation FIRE_TORNADO_TYPE =
            ResourceLocation.fromNamespaceAndPath("goety", "fire_tornado");

    private static final Map<UUID, Float> MAX_HP_REDUCTIONS = new HashMap<>();
    /** 自定义受击无敌帧（soulHurtDefend）：复活后 30 秒窗口，事件层取消伤害 + 数据层拦扣血写入 */
    private static final Map<UUID, Integer> SOUL_HURT_DEFEND = new HashMap<>();
    /** 客户端侧 soulHurtDefend 剩余 tick，由 S2CSoulSwordTotemPack 同步，用于粒子特效 */
    private static final Map<UUID, Integer> CLIENT_SOUL_HURT_DEFEND = new HashMap<>();
    private static final Map<UUID, Integer> TOTEM_COOLDOWNS = new HashMap<>();
    /** 格挡时的伤害适应状态：玩家UUID → 伤害类型 → 命中次数/最后命中tick */
    private static final Map<UUID, Map<String, AdaptationEntry>> ADAPTATION = new HashMap<>();
    /** 格挡时每 tick 记录的血量快照，用于回滚"改底层血量"类直写伤害 */
    private static final Map<UUID, Float> BLOCK_HEALTH_SNAPSHOT = new HashMap<>();
    /** 本 tick 已通过正常伤害事件结算的伤害量（直写回滚时视为"已解释"的伤害） */
    private static final Map<UUID, Float> EXPLAINED_DAMAGE = new HashMap<>();

    /**
     * 构造无垠长魂之剑虚空伤害源。
     * soul_sword 伤害类型带 bypasses_invulnerability（无视无敌帧），且不在魔法伤害标签里，
     * 因此不受魔法类伤害抗性影响。direct 传投射物/黑洞实体、causing 传玩家，
     * 既满足“来源玩家”，又避免被当成近战攻击触发 onSoulSwordAttack。
     */
    public static DamageSource soulSwordVoidSource(Level level, Entity direct, Entity causing) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(SOUL_SWORD_DAMAGE), direct, causing);
    }

    /** 灵魂伤害源（direct/causing 都传玩家本人，避免被当作间接伤害） */
    public static DamageSource soulDamageSource(Level level, Entity direct, Entity causing) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(SOUL_DAMAGE), direct, causing);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void debugLivingAttack(LivingAttackEvent event) {
        DamageSource source = event.getSource();
        if (source == null) return;
        if (!(source.getEntity() instanceof Player p)) return;
        ItemStack main = p.getMainHandItem();
        if (!(main.getItem() instanceof SoulSwordItem)) return;
        if (event.isCanceled()) {
            LOGGER.info("[SoulSword] ATTACK CANCELLED on " + BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()) + " source=" + event.getSource());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onSoulSwordAttack(LivingDamageEvent event) {
        if (VOID_DAMAGING.get()) return;
        if (SOUL_DAMAGING.get()) return;
        if (SONIC_FEEDBACK.get()) return;
        if (event.getEntity().level().isClientSide()) return;
        DamageSource source = event.getSource();
        // 只处理无垠长魂之剑的近战攻击：直接伤害来源必须是玩家本人。
        // 否则黑洞等法术的每 tick 伤害（间接来源是持剑的玩家）也会触发近战专属效果
        // （永久 -10 最大生命、2.7e24 虚空秒杀），导致持剑时法术伤害被错误放大。
        if (!(source.getDirectEntity() instanceof Player)) return;
        if (!(source.getEntity() instanceof Player player)) return;

        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        if (!(mainHand.getItem() instanceof SoulSwordItem)
                && !(offHand.getItem() instanceof SoulSwordItem)) return;

        LivingEntity target = event.getEntity();

        // 抗性压制：无论攻击强度如何、无论目标死活，只要无垠长魂之剑命中就施加
        // 先清除目标身上已有的抗性提升（不然旧抗性持续时间内目标仍免疫伤害）
        target.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        target.addEffect(new MobEffectInstance(WeaponRegistries.RESISTANCE_BLOCKADE.get(), 160, 0));

        if (player.getAttackStrengthScale(0.5f) < 1.0f) return;

        String typeKey = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString();
        if (TEST_DUMMY_TYPE.equals(typeKey)) {
            return;
        }
        LOGGER.info("[SoulSword] onSoulSwordAttack target=" + typeKey + " dead=" + target.isDeadOrDying() + " hp=" + target.getHealth() + "/" + target.getMaxHealth());

        UUID targetId = target.getUUID();

        // 边界修复：必须在 -10 最大生命削减之前判定 OP Boss。
        // 否则 300 血阈值 Boss 第一刀就被削到 290，getMaxHealth() 永远 < 300，
        // 灵魂伤害从第一刀起就静默失效；且打久了（每刀 -10）也会中途掉出阈值。
        boolean opBoss = OP_BOSS_QUALIFIED.contains(targetId) || isOpBoss(target);
        if (opBoss) {
            if (OP_BOSS_QUALIFIED.size() > 2048) {
                OP_BOSS_QUALIFIED.clear();
            }
            OP_BOSS_QUALIFIED.add(targetId);
        }

        // -10 最大生命已移至 tryDirectWriteDamage（所有左键攻击必经之路），
        // 此处不再重复扣，避免 LivingDamageEvent 也触发时双倍扣除。

        if (target.isDeadOrDying()) return;

        if (LEVIATHAN_TYPE.equals(typeKey) || APOLLYON_SUMMON_TYPE.equals(typeKey)) return;
        if (isApollyonForm(target)) return;

        DamageSource voidSource = new DamageSource(
                player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                        .getHolderOrThrow(SOUL_SWORD_DAMAGE), player);
        VOID_DAMAGING.set(true);
        try {
            target.invulnerableTime = 0;
            target.hurt(voidSource, 2.71828e24f);
        } finally {
            VOID_DAMAGING.set(false);
        }

        // 灵魂伤害：只针对"非 Apollyon / 非针对性实体 / 非无尽套"的 OP 级 Boss
        if (opBoss && !SoulSwordItem.isInfiniteArmorUser(target)) {
            // 边界修复：虚空一击可能已经把目标打死，死目标不再结算灵魂伤害
            if (target.isDeadOrDying()) {
                OP_BOSS_QUALIFIED.remove(targetId);
                return;
            }
            // 1) 直接解除 Boss 的特殊无敌帧（自定义字段 + 原版无敌帧）
            clearBossInvulnFrames(target);
            // 2) 普通攻击附带治疗压制 -50%（黑洞的 90% 压制互不冲突）
            SoulHealSuppression.mark(target, SOUL_HEAL_SUPPRESSION_FACTOR);
            // 3) 附加对方最大生命 2% 的灵魂伤害
            float soulAmount = target.getMaxHealth() * SOUL_DAMAGE_PERCENT;
            DamageSource soulSource = soulDamageSource(target.level(), player, player);
            SOUL_DAMAGING.set(true);
            try {
                target.invulnerableTime = 0;
                // 所有实体均走直写改血，绕过 setHealth 及其所有限伤重写
                directWriteHealth(target, soulAmount);
                soulHitFeedback(target, soulSource);
                recordSoulDamageFeedback(target, player, soulAmount);
            } finally {
                SOUL_DAMAGING.set(false);
            }
        }
    }

    /**
     * 直写伤害没有走 hurt()，不会自动记录战斗反馈。
     * 这里手动补齐：
     *  - CombatTracker 最后一条 = 灵魂伤害（1.20.1 死亡播报取最后一条的伤害来源）
     *  - lastHurtByPlayer / lastHurtByMob = 玩家（getKillCredit 击杀归属、进度、击杀经验）
     *  - lastDamageSource / lastDamageStamp / lastHurtByPlayerTime（部分代码路径直接读这些字段）
     */
    private static void recordSoulDamageFeedback(LivingEntity target, Player player, float amount) {
        try {
            DamageSource soulSource = soulDamageSource(target.level(), player, player);
            target.getCombatTracker().recordDamage(soulSource, amount);
            target.setLastHurtByPlayer(player);
            target.setLastHurtByMob(player);
            try {
                Field sourceField = findField(LivingEntity.class, "lastDamageSource", "f_20958_");
                if (sourceField != null) sourceField.set(target, soulSource);
                Field stampField = findField(LivingEntity.class, "lastDamageStamp", "f_20930_");
                if (stampField != null) stampField.setLong(target, target.level().getGameTime());
                // 1.20.1 的 setLastHurtByPlayer 会写成 tickCount，掉落判定要的是倒计时 100
                Field timeField = findField(LivingEntity.class, "lastHurtByPlayerTime", "f_20889_");
                if (timeField != null) timeField.setInt(target, 100);
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            LOGGER.warn("[SoulSword] record soul damage feedback failed: " + t);
        }
    }

    /**
     * 直写扣血：绕过特殊 EntityData 的 setHealth 闸门（外部 ASM 特化），
     * 同时对所有实体通用。
     *
     * 外部凋灵用特殊 EntityData（SynchedEntityData 子类）重写了 set()，
     * 任何 setHealth 都会过 "min(35→14, 0.4×伤害)" 的限伤 + 2 血锁血时停。
     * 这里绕过 set()，直接改 itemsById 里 HEALTH 对应 DataItem 的 value，
     * 并标记 dirty 让服务端下个 tick 同步给客户端。
     *
     * 副作用（都是"无视限伤"的预期结果）：
     *  - 不再吃 35% 闪避 / 5 tick 无敌帧（伤害不经过 hurt()）
     *  - 不再触发 2 血锁血 + 紧急时停
     *  - 不产生伤害事件/死亡播报（纯扣血）
     */
    private static boolean tryDirectSoulDamage(LivingEntity target, float amount) {
        return directWriteHealth(target, amount);
    }

    /**
     * 通用直写扣血：绕过 setHealth（包括被 mod 重写的限伤闸门），
     * 直接修改 SynchedEntityData 里 HEALTH 对应 DataItem 的 value 并标记 dirty，
     * 让服务端下个 tick 同步给客户端。对所有实体通用。
     */
    private static boolean directWriteHealth(LivingEntity target, float amount) {
        return directWriteHealth(target, amount, false);
    }

    /**
     * Undefined Wither attacks get one extra fallback: if DATA_HEALTH writes do
     * not change effective health, apply the generic shadow delta channel before
     * falling back to the target's own hurt() implementation.
     */
    private static boolean directWriteHealth(LivingEntity target, float amount, boolean allowDelta) {
        SynchedEntityData data = target.getEntityData();
        try {
            // External-mod-compatible generic authoritative-store path. This is
            // intentionally before DATA_HEALTH: many bosses expose a mirror
            // there while their real health lives in another accessor.
            if (writeHiddenHealthByAmount(target, amount)) return true;
            Object dataItem = findHealthDataItem(data);
            if (dataItem == null) return allowDelta && HealthDeltaBridge.applyDamage(target, amount);
            Field valueField = findField(dataItem.getClass(), "value", "f_135391_");
            if (valueField == null) return allowDelta && HealthDeltaBridge.applyDamage(target, amount);
            float oldHealth = (Float) valueField.get(dataItem);
            float healthBefore = target.getHealth();
            if (target instanceof Player player) {
                // This path intentionally bypasses setHealth(), so apply the
                // same raw 20-point cap and direct-write mitigation explicitly.
                if (!StarCoronaCrystalEvents.isSoulHeartHurtInProgress(player)) {
                    amount = StarCoronaCrystalEvents.reduceDirectHealthDamage(player, amount);
                } else {
                    amount = StarCoronaCrystalEvents.capSoulHeartDamageBeforeReduction(player, amount);
                }
            }
            if (target instanceof net.minecraft.world.entity.boss.wither.WitherBoss wither
                    && wither_title.WitherTitleHelper.isUndefinedWither(wither)) {
                amount = wither_title.WitherTitleHelper.capIncomingSoulDamage(wither, amount);
                wither_title.WitherTitleHelper.beginOwnDirectWrite();
                try {
                    wither_title.WitherTitleHelper.applyHiddenDamage(wither, amount);
                    float hidden = wither_title.WitherTitleHelper.getHiddenHp(wither);
                    if (directSetHealth(target, hidden)) return true;
                    wither_title.WitherTitleHelper.restoreDisplayHealth(wither, hidden);
                    return true;
                } finally {
                    wither_title.WitherTitleHelper.endOwnDirectWrite();
                }
            }
            if (directSetHealth(target, oldHealth - amount)) {
                boolean effective = target.getHealth() < healthBefore - 0.5F
                        || (healthBefore > 0.0F && target.getHealth() <= 0.0F);
                if (allowDelta && amount > 0.0F && !effective
                        && HealthDeltaBridge.applyDamage(target, amount)) {
                    return true;
                }
                // 泛用隐藏真血探针（外部 ASM 式逆向直写）：上一步只写进了原版 DATA_HEALTH。
                // 若 getHealth() 没随之下降，说明目标是"真血不在 DATA_HEALTH、每 tick 用它反向
                // 覆盖"的 EXALTED/独立字段型（如无名者 CURRENT_COMBAT_HEALTH），DATA_HEALTH
                // 只是它不看的镜像。此时用探针定位"随 getHealth 变化"的真正血量 DataItem 并
                // 直写，绕开目标 hurt 的每击上限/命中免伤/仆从减伤/护甲。
                // 跳过玩家与已有专用多写路径的 Apollyon 等特殊实体，避免与其逻辑重复扣血。
                if (amount > 1.0F && !(target instanceof Player)
                        && !isApollyonForm(target) && !isSpecialHealthEntity(target)
                        && target.getHealth() > healthBefore - 0.5F) {
                    effective = writeHiddenHealthIfAny(target, healthBefore - amount);
                }
                return effective;
            }
            return allowDelta && HealthDeltaBridge.applyDamage(target, amount);
        } catch (Throwable t) {
            LOGGER.warn("[SoulSword] direct write health failed: " + t);
            return false;
        }
    }

    /**
     * 探针式隐藏真血直写：定位并写入"随 getHealth 变化"的非原版血量 DataItem，绕开限伤。
     * 命中隐藏存储返回 true；目标用原版 DATA_HEALTH（无隐藏血）返回 false。
     */
    private static boolean writeHiddenHealthIfAny(LivingEntity target, float newHealth) {
        HiddenHealthRef ref = probeHiddenHealthStore(target);
        if (ref == null) return false;
        try {
            SynchedEntityData data = target.getEntityData();
            Object dataItem = findDataItem(data, ref.accessor);
            if (dataItem == null) return false;
            Field valueField = findField(dataItem.getClass(), "value", "f_135391_");
            if (valueField == null) return false;
            Object beforeValue = valueField.get(dataItem);
            double before = beforeValue instanceof Number number ? number.doubleValue() : Double.NaN;
            if (!Double.isFinite(before)) return false;
            float clamped = Math.max(0.0F, newHealth);
            Object box = ref.isFloat ? Float.valueOf(clamped) : Double.valueOf((double) clamped);
            valueField.set(dataItem, box);
            notifyDataItemUpdated(target, dataItem, ref.accessor);
            markSynchedDataDirty(data, dataItem);
            Object afterValue = valueField.get(dataItem);
            double after = afterValue instanceof Number number ? number.doubleValue() : Double.NaN;
            // Validate the authoritative item itself. getHealth() may be
            // clamped by the target and is therefore not a valid success test.
            if (!(before - after >= 0.001D)) {
                HIDDEN_HEALTH_CACHE.remove(target.getClass().getName());
                return false;
            }
            return true;
        } catch (Throwable t) {
            LOGGER.warn("[SoulSword] hidden health direct write failed: " + t);
            HIDDEN_HEALTH_CACHE.remove(target.getClass().getName());
            return false;
        }
    }

    /**
     * 泛用探针：找出目标实体里"随 getHealth() 变化"的隐藏真血 DataItem（非原版 DATA_HEALTH）。
     * 值+行为双验证：先筛出数值等于当前 getHealth 的 Float/Double 项，再用 setHealth 微扰确认
     * 该项会跟着 getHealth 一起变（只有 getHealth 真正读取的存储才满足），最后还原血量。
     * 结果按类缓存。派生血型（如泽林 getHealth=NORMAL-AWAY）无任何单项等于 getHealth，探针
     * 扑空 → 返回 null → 上层自动回落 hurt，符合"派生血无法泛用定位"的预期。
     */
    private static HiddenHealthRef probeHiddenHealthStore(LivingEntity target) {
        String key = target.getClass().getName();
        HiddenHealthRef cached = HIDDEN_HEALTH_CACHE.get(key);
        if (cached != null) return cached == NOT_HIDDEN ? null : cached;
        HiddenHealthRef found = null;
        try {
            int vanillaId = vanillaHealthId();
            SynchedEntityData data = target.getEntityData();
            HiddenHealthRef declared = findDeclaredHealthStore(target.getClass(), data, vanillaId);
            if (declared != null) {
                found = declared;
            }
            Field itemsField = findField(SynchedEntityData.class, "itemsById", "f_135345_");
            if (found == null && itemsField != null) {
                Object itemsMap = itemsField.get(data);
                Collection<?> items = (Collection<?>) itemsMap.getClass().getMethod("values").invoke(itemsMap);
                float cur = target.getHealth();
                List<Object> candidates = new ArrayList<>();
                for (Object item : items) {
                    Field vf = findField(item.getClass(), "value", "f_135391_");
                    Field af = findField(item.getClass(), "accessor", "f_135389_");
                    if (vf == null || af == null) continue;
                    EntityDataAccessor<?> acc = (EntityDataAccessor<?>) af.get(item);
                    if (acc == null || acc.getId() == vanillaId) continue;
                    Object v = vf.get(item);
                    if (v instanceof Float fv && Math.abs(fv - cur) < 1.0E-3F) {
                        candidates.add(item);
                    } else if (v instanceof Double dv && Math.abs(dv - (double) cur) < 1.0E-3D) {
                        candidates.add(item);
                    }
                }
                found = confirmHiddenHealthItem(target, candidates, cur);
            }
        } catch (Throwable t) {
            LOGGER.warn("[SoulSword] hidden health probe failed: " + t);
        }
        if (HIDDEN_HEALTH_CACHE.size() >= HIDDEN_HEALTH_CACHE_CAP) {
            HIDDEN_HEALTH_CACHE.clear();
        }
        HIDDEN_HEALTH_CACHE.put(key, found == null ? NOT_HIDDEN : found);
        return found;
    }

    /** External-mod-compatible first pass: use static HEALTH EntityDataAccessor fields. */
    private static HiddenHealthRef findDeclaredHealthStore(Class<?> type,
                                                            SynchedEntityData data,
                                                            int vanillaId) {
        Class<?> current = type;
        while (current != null && LivingEntity.class.isAssignableFrom(current)) {
            for (Field field : current.getDeclaredFields()) {
                String normalized = field.getName().replace("_", "").toUpperCase(Locale.ROOT);
                if (!Modifier.isStatic(field.getModifiers())
                        || !normalized.contains("HEALTH")
                        || normalized.contains("MAXHEALTH")
                        || !EntityDataAccessor.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(null);
                    if (!(value instanceof EntityDataAccessor<?> accessor)
                            || accessor.getId() == vanillaId) continue;
                    Object item = findDataItem(data, accessor);
                    if (item == null) continue;
                    Field valueField = findField(item.getClass(), "value", "f_135391_");
                    Object itemValue = valueField == null ? null : valueField.get(item);
                    if (itemValue instanceof Float || itemValue instanceof Double) {
                        return new HiddenHealthRef(accessor, itemValue instanceof Float);
                    }
                } catch (Throwable ignored) {
                    // Optional or malformed accessor: continue probing the hierarchy.
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Object findDataItem(SynchedEntityData data, EntityDataAccessor<?> accessor) throws Throwable {
        return findDataItemById(data, accessor.getId());
    }

    private static Object findDataItemById(SynchedEntityData data, int id) throws Throwable {
        Field itemsField = findField(SynchedEntityData.class, "itemsById", "f_135345_");
        if (itemsField == null) return null;
        Object itemsMap = itemsField.get(data);
        if (itemsMap instanceof Map<?, ?> map) return map.get(id);
        return itemsMap.getClass().getMethod("get", int.class).invoke(itemsMap, id);
    }

    private static EntityDataAccessor<?> dataItemAccessor(Object dataItem) {
        if (dataItem == null) return null;
        try {
            Field accessor = findField(dataItem.getClass(), "accessor", "f_135389_");
            Object value = accessor == null ? null : accessor.get(dataItem);
            return value instanceof EntityDataAccessor<?> dataAccessor ? dataAccessor : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Same onSyncedDataUpdated callback the external mod invokes after changing a DataItem. */
    private static void notifyDataItemUpdated(LivingEntity target, Object dataItem,
                                               EntityDataAccessor<?> fallbackAccessor) {
        try {
            EntityDataAccessor<?> accessor = dataItemAccessor(dataItem);
            if (accessor == null) accessor = fallbackAccessor;
            if (accessor == null) return;
            Method callback = findMethod(net.minecraft.world.entity.Entity.class,
                    "onSyncedDataUpdated", "m_7350_");
            if (callback != null) {
                callback.setAccessible(true);
                callback.invoke(target, accessor);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 直接微扰候选 DataItem 确认权威存储。探针不调用 target.setHealth()，
     * 避免再次经过目标自己的单次限伤/冷却；因此同一套泛化逻辑可用于
     * 普通 DATA_HEALTH、独立战斗血量和其它自定义血量实体。
     */
    private static HiddenHealthRef confirmHiddenHealthItem(LivingEntity target, List<Object> candidates, float cur) {
        if (candidates.isEmpty()) return null;
        float max = target.getMaxHealth();
        float testVal = cur >= max - 0.5F ? Math.max(0.5F, cur - 0.5F) : cur + 0.5F;
        SynchedEntityData data = target.getEntityData();
        try {
            for (Object item : candidates) {
                Field vf = findField(item.getClass(), "value", "f_135391_");
                Field af = findField(item.getClass(), "accessor", "f_135389_");
                if (vf == null || af == null) continue;
                Object original = vf.get(item);
                Object probe = original instanceof Float
                        ? Float.valueOf(testVal) : Double.valueOf((double) testVal);
                vf.set(item, probe);
                markSynchedDataDirty(data, item);
                float newCur = target.getHealth();
                vf.set(item, original);
                markSynchedDataDirty(data, item);
                boolean tracks = Math.abs(newCur - testVal) < 1.0E-3F;
                if (tracks) {
                    EntityDataAccessor<?> acc = (EntityDataAccessor<?>) af.get(item);
                    return new HiddenHealthRef(acc, original instanceof Float);
                }
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean writeHiddenHealthByAmount(LivingEntity target, float amount) {
        if (target == null || amount <= 0.0F || !Float.isFinite(amount)) return false;
        HiddenHealthRef ref = probeHiddenHealthStore(target);
        if (ref == null) return false;
        try {
            Object item = findDataItem(target.getEntityData(), ref.accessor);
            Field valueField = item == null ? null : findField(item.getClass(), "value", "f_135391_");
            if (valueField == null) return false;
            Object beforeValue = valueField.get(item);
            if (!(beforeValue instanceof Number number) || !Double.isFinite(number.doubleValue())) return false;
            double next = Math.max(0.0D, number.doubleValue() - amount);
            return writeHiddenHealthIfAny(target, (float) next);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void markSynchedDataDirty(SynchedEntityData data, Object dataItem) throws Throwable {
        Field dirtyItemField = findField(dataItem.getClass(), "dirty", "f_135392_");
        if (dirtyItemField != null) dirtyItemField.setBoolean(dataItem, true);
        Field dirtyField = findField(SynchedEntityData.class, "isDirty", "f_135348_");
        if (dirtyField != null) dirtyField.setBoolean(data, true);
    }

    /** 原版血量 DATA_HEALTH 的 id（探针据此排除原版项）。 */
    private static int vanillaHealthId() throws Throwable {
        Field healthIdField = findField(LivingEntity.class, "DATA_HEALTH_ID", "f_20961_");
        if (healthIdField == null) return -1;
        EntityDataAccessor<Float> acc = (EntityDataAccessor<Float>) healthIdField.get(null);
        return acc.getId();
    }

    /**
     * 强制击杀：直写后剩余生命 <= 20 的实体立刻进入死亡流程（无视无敌/死亡保护/图腾）。
     * 玩家与 Apollyon 除外（后者血量/死亡走专用序列，强杀会表现为被直接清除）。
     * 死亡来源必须记成玩家击杀，否则掉落表的 killed_by_player 不会出战利品。
     */
    public static boolean tryForceKillIfBelowThreshold(LivingEntity target) {
        return tryForceKillIfBelowThreshold(target, null);
    }

    public static boolean tryForceKillIfBelowThreshold(LivingEntity target, LivingEntity attacker) {
        if (target == null || target.isRemoved() || target.dead) return false;
        if (target instanceof Player) return false;
        if (isApollyonForm(target)) return false;
        if (target.getHealth() > 20.0F) return false;

        LivingEntity killer = attacker;
        if (killer == null) {
            Entity credit = target.getKillCredit();
            if (credit instanceof LivingEntity livingCredit) {
                killer = livingCredit;
            }
        }
        if (killer instanceof Player player) {
            recordSoulDamageFeedback(target, player, Math.max(target.getHealth(), 1.0F));
            forceKillEntity(target, target.damageSources().playerAttack(player));
        } else if (killer != null) {
            forceKillEntity(target, soulDamageSource(target.level(), killer, killer));
        } else {
            forceKillEntity(target, target.damageSources().genericKill());
        }
        return true;
    }

    /** 强制击杀实体（绕过所有死亡保护，直接触发死亡流程） */
    private static void forceKillEntity(LivingEntity target, DamageSource source) {
        try {
            target.invulnerableTime = 0;
            clearDeathBlockFields(target);
            directSetHealth(target, 0.0f);
            clearBossHealFields(target);
            boolean alreadyDead = target.dead;
            target.die(source);
            if (!alreadyDead && !target.dead) {
                dropDeathLootDirect(target, source);
                target.dead = true;
            }
        } catch (Throwable ignored) {
            LOGGER.warn("[SoulSword] force kill failed for " + target);
        }
    }

    /** die() 被其它模组取消时，仍按原版掉落表出战利品 */
    private static void dropDeathLootDirect(LivingEntity target, DamageSource source) {
        try {
            java.lang.reflect.Method drop = findMethod(LivingEntity.class, "dropAllDeathLoot", "m_6668_");
            if (drop != null) {
                drop.setAccessible(true);
                drop.invoke(target, source);
            }
        } catch (Throwable t) {
            LOGGER.warn("[SoulSword] dropAllDeathLoot failed: " + t);
        }
    }

    private static java.lang.reflect.Method findMethod(Class<?> clazz, String... names) {
        for (java.lang.reflect.Method m : clazz.getDeclaredMethods()) {
            for (String name : names) {
                if (m.getName().equals(name) && m.getParameterCount() == 1) {
                    return m;
                }
            }
        }
        return null;
    }

    /** 清除BOSS的自然回血相关字段，防止死亡动画期间回血 */
    private static void clearBossHealFields(LivingEntity target) {
        // 常见回血相关字段名
        String[] healBlockNames = {
                "ticksWithoutTarget", "noTargetTicks", "lostTargetTime",
                "healCooldown", "regenCooldown", "naturalHealTimer"
        };
        Class<?> clazz = target.getClass();
        while (clazz != null && clazz != Object.class) {
            for (String name : healBlockNames) {
                try {
                    Field f = clazz.getDeclaredField(name);
                    f.setAccessible(true);
                    Class<?> type = f.getType();
                    if (type == int.class) {
                        // 设置为最大值，使回血条件不满足（ticksWithoutTarget > 0 才回血）
                        f.setInt(target, Integer.MAX_VALUE);
                    }
                } catch (NoSuchFieldException ignored) {
                } catch (Exception e) {
                    LOGGER.warn("[SoulSword] clear heal field {} failed: {}", name, e.getMessage());
                }
            }
            clazz = clazz.getSuperclass();
        }
        // 尝试调用设置目标的方法，使 targetIsNotNull() 返回 true（阻止回血）
        try {
            java.lang.reflect.Method setTarget = target.getClass().getMethod("setTarget", LivingEntity.class);
            // 设置一个虚拟目标（比如自己），使 targetIsNotNull() 返回 true
            setTarget.invoke(target, target);
        } catch (Throwable ignored) {
        }
    }

    /** 反射查找实体中所有可能阻塞死亡的自定义字段，设为允许死亡 */
    private static void clearDeathBlockFields(LivingEntity target) {
        // 常见死亡阻塞字段名（兼容不同 Boss 的自定义命名）
        String[] deathBlockNames = {
                "canDie", "canNotDie", "cannotDie", "isDeathLocked",
                "deathLocked", "preventDeath", "preventDeath",
                "reviving", "isReviving", "reviveLocked"
        };
        Class<?> clazz = target.getClass();
        while (clazz != null && clazz != Object.class) {
            for (String name : deathBlockNames) {
                try {
                    Field f = clazz.getDeclaredField(name);
                    f.setAccessible(true);
                    Class<?> type = f.getType();
                    if (type == boolean.class) {
                        f.setBoolean(target, name.equals("canDie") || name.equals("isDeathLocked")
                                || name.equals("deathLocked") || name.equals("canNotDie")
                                ? !name.contains("Not")  // canDie=true, canNotDie=false
                                : false);                // 其他字段名：reviving=false, preventDeath=false
                    } else if (type == int.class) {
                        f.setInt(target, 0);             // 复活倒计时归零
                    }
                } catch (NoSuchFieldException ignored) {
                } catch (Exception e) {
                    LOGGER.warn("[SoulSword] clear field {} failed: {}", name, e.getMessage());
                }
            }
            // 检查 target 的字段是否可直接可访问
            clazz = clazz.getSuperclass();
        }
    }

    /** 直写设置实体血量（绕过 setHealth 及其所有限伤重写），对所有实体通用 */
    public static boolean directSetHealth(LivingEntity target, float health) {
        SynchedEntityData data = target.getEntityData();
        try {
            Object dataItem = findHealthDataItem(data);
            if (dataItem == null) return false;
            Field valueField = findField(dataItem.getClass(), "value", "f_135391_");
            Field dirtyItemField = findField(dataItem.getClass(), "dirty", "f_135392_");
            if (valueField == null || dirtyItemField == null) return false;
            valueField.set(dataItem, Math.max(0.0F, health));
            EntityDataAccessor<?> accessor = dataItemAccessor(dataItem);
            if (accessor != null) notifyDataItemUpdated(target, dataItem, accessor);
            dirtyItemField.setBoolean(dataItem, true);
            // 整个 SynchedEntityData 标记 dirty，确保同步到客户端
            Field dirtyField = findField(SynchedEntityData.class, "isDirty", "f_135348_");
            if (dirtyField == null) return false;
            dirtyField.setBoolean(data, true);
            return true;
        } catch (Throwable t) {
            LOGGER.warn("[SoulSword] direct set health failed: " + t);
            return false;
        }
    }

    /**
     * 读取并直写原版 DATA_HEALTH。返回值按底层 DataItem 的前后值判断，
     * 不依赖目标 getHealth() 是否被其它模组延迟同步或临时钳制。
     */
    private static boolean directWriteRawHealth(LivingEntity target, float amount) {
        if (target == null || amount <= 0.0F || !Float.isFinite(amount)) return false;
        try {
            Object dataItem = findHealthDataItem(target.getEntityData());
            if (dataItem == null) return false;
            Field valueField = findField(dataItem.getClass(), "value", "f_135391_");
            if (valueField == null) return false;
            Object value = valueField.get(dataItem);
            if (!(value instanceof Float before) || !Float.isFinite(before) || before <= 0.0F) return false;
            float next = Math.max(0.0F, before - amount);
            if (!directSetHealth(target, next)) return false;
            Object afterValue = valueField.get(dataItem);
            return afterValue instanceof Float after && before - after >= 0.001F;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 定位 SynchedEntityData 中血量对应的 DataItem（直写用，双名兼容） */
    private static Object findHealthDataItem(SynchedEntityData data) throws Throwable {
        // 双名兼容：开发环境是 Mojang 名，生产环境 Minecraft 类成员是 SRG 名（f_xxxxx）
        Field healthIdField = findField(LivingEntity.class, "DATA_HEALTH_ID", "f_20961_");
        if (healthIdField == null) return null;
        EntityDataAccessor<Float> healthAccessor = (EntityDataAccessor<Float>) healthIdField.get(null);
        int healthId = healthAccessor.getId();
        // SynchedEntityData.itemsById（private final Int2ObjectMap）
        Field itemsField = findField(SynchedEntityData.class, "itemsById", "f_135345_");
        if (itemsField == null) return null;
        Object itemsMap = itemsField.get(data);
        Method getById = itemsMap.getClass().getMethod("get", int.class);
        return getById.invoke(itemsMap, healthId);
    }

    /** 判断目标是否使用了特殊 EntityData（外部 Mod 重写 set 的限伤闸门） */
    public static boolean isSpecialHealthEntity(LivingEntity target) {
        return target != null
                && "com.mega.uom.common.entity.boss.uom.UomWither$UomEntityData"
                .equals(target.getEntityData().getClass().getName());
    }

    /**
     * 对所有实体直写扣血：绕过 setHealth 及其所有限伤重写，无视 INVULNERABLE 等自定义效果。
     * 附带清特殊无敌帧 + 击杀反馈 + 永久-10最大生命（所有实体都有，不依赖 LivingDamageEvent）。
     */
    public static boolean tryDirectWriteDamage(LivingEntity target, Entity causing, float amount) {
        // Apollyon 专用分流：其真血在 apollyonHealth / MegaCapability（tick 会回写覆盖
        // DATA_HEALTH_ID），通用直写表现为"掉一点后锁血"，必须走三写专用路径
        if (isApollyonForm(target) && causing instanceof LivingEntity livingAttacker) {
            return apollyonSoulDamage(target, livingAttacker, amount, true);
        }
        clearBossInvulnFrames(target);
        DamageSource soulSource = soulDamageSource(target.level(), causing, causing);
        
        // OP Boss 判定
        UUID targetId = target.getUUID();
        boolean isTargetOpBoss = causing instanceof Player && (OP_BOSS_QUALIFIED.contains(targetId) || isOpBoss(target));
        if (isTargetOpBoss) {
            if (OP_BOSS_QUALIFIED.size() > 2048) {
                OP_BOSS_QUALIFIED.clear();
            }
            OP_BOSS_QUALIFIED.add(targetId);
        }
        
        if (directWriteHealth(target, amount)) {
            markSpecialHurtFeedback(target, amount);
            soulHitFeedback(target, soulSource);
            // Apollyon：直写已把血量归零但不走强杀 → 补一次有来源的灵魂伤害，
            // 触发其正常死亡流程（专用 die 序列），避免血 0 不死/被异常清除
            if (target.isAlive() && target.getHealth() <= 0.5F && isApollyonForm(target)
                    && causing instanceof LivingEntity livingAttacker) {
                soulDamageFallbackHurt(target, livingAttacker, 1.0F);
            }
            // OP Boss 附加虚空伤害+灵魂伤害（与副手无垠长魂之剑逻辑一致）
            if (causing instanceof Player player && !(target instanceof Player) && isTargetOpBoss
                    && !SoulSwordItem.isInfiniteArmorUser(target)) {
                // 1) 先造成 2.7e24 虚空伤害（直接秒杀）
                if (!target.isDeadOrDying()) {
                    DamageSource voidSource = new DamageSource(
                            player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                                    .getHolderOrThrow(SOUL_SWORD_DAMAGE), player);
                    VOID_DAMAGING.set(true);
                    try {
                        target.invulnerableTime = 0;
                        target.hurt(voidSource, 2.71828e24f);
                    } finally {
                        VOID_DAMAGING.set(false);
                    }
                }
                // 2) 如果还没死，附加灵魂伤害（直写血量+强制击杀）
                if (target.isAlive() && !target.isDeadOrDying()) {
                    clearBossInvulnFrames(target);
                    SoulHealSuppression.mark(target, SOUL_HEAL_SUPPRESSION_FACTOR);
                    float soulAmount = target.getMaxHealth() * SOUL_DAMAGE_PERCENT;
                    DamageSource soulDmgSource = soulDamageSource(target.level(), player, player);
                    SOUL_DAMAGING.set(true);
                    try {
                        target.invulnerableTime = 0;
                        directWriteHealth(target, soulAmount);
                        soulHitFeedback(target, soulDmgSource);
                        recordSoulDamageFeedback(target, player, soulAmount);
                    } finally {
                        SOUL_DAMAGING.set(false);
                    }
                }
                // 3) 永久减 10 最大生命
                float current = MAX_HP_REDUCTIONS.getOrDefault(targetId, 0f);
                float reduction = current + 10f;
                MAX_HP_REDUCTIONS.put(targetId, reduction);

                AttributeInstance maxHp = target.getAttribute(Attributes.MAX_HEALTH);
                if (maxHp != null) {
                    maxHp.removeModifier(MAX_HP_REDUCTION_UUID);
                    maxHp.addPermanentModifier(new AttributeModifier(MAX_HP_REDUCTION_UUID,
                            "SoulSword maxHP reduction", -reduction, AttributeModifier.Operation.ADDITION));
                }
            } else if (causing instanceof Player player && !(target instanceof Player)) {
                // 非 OP Boss：只减 10 最大生命
                float current = MAX_HP_REDUCTIONS.getOrDefault(targetId, 0f);
                float reduction = current + 10f;
                MAX_HP_REDUCTIONS.put(targetId, reduction);

                AttributeInstance maxHp = target.getAttribute(Attributes.MAX_HEALTH);
                if (maxHp != null) {
                    maxHp.removeModifier(MAX_HP_REDUCTION_UUID);
                    maxHp.addPermanentModifier(new AttributeModifier(MAX_HP_REDUCTION_UUID,
                            "SoulSword maxHP reduction", -reduction, AttributeModifier.Operation.ADDITION));
                }
                recordSoulDamageFeedback(target, player, amount);
            }
            return true;
        }
        return false;
    }

    /** 直写不经过 hurt()，Boss 条的伤害数字（读 getLastHurtDamage + hurtTime）不会自己刷新，这里手动补 */
    private static void markSpecialHurtFeedback(LivingEntity target, float amount) {
        try {
            java.lang.reflect.Method setLast = target.getClass().getMethod("setLastHurtDamage", float.class);
            setLast.invoke(target, amount);
        } catch (Throwable ignored) {
        }
        try {
            Field hurtTime = findField(LivingEntity.class, "hurtTime", "f_20916_");
            if (hurtTime != null) {
                hurtTime.setInt(target, 10);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 普通攻击（非黑洞）附带的治疗压制 -50% */
    public static void markSoulSwordHealSuppression(LivingEntity target) {
        SoulHealSuppression.mark(target, SOUL_HEAL_SUPPRESSION_FACTOR);
    }

    /**
     * 纯粹恶意专用直写后门：正常阶段绕过未定义凋灵 20 点单次限伤。
     * 普通伤害最多 20 点，再叠加纯粹恶意额外伤害，V 级单次最多可达 30 点。
     * 尾杀阶段禁止任何额外直写，避免破坏尾杀流程。
     */
    public static boolean applyPureMaliceDamage(LivingEntity target, LivingEntity attacker, float amount) {
        if (target == null || attacker == null || !target.isAlive()) return false;
        if (target.level().isClientSide) return false;
        if (target instanceof net.minecraft.world.entity.boss.wither.WitherBoss wither
                && wither_title.WitherTitleHelper.isUndefinedWither(wither)) {
            try {
                if (wither.getEntityData().get(wither_title.WitherTitleHelper.UNDEFINED_FINAL_SKILL)) {
                    return false;
                }
            } catch (Throwable ignored) {
            }
        }
        clearBossInvulnFrames(target);
        DamageSource soulSource = soulDamageSource(target.level(), attacker, attacker);
        if (!directWriteHealth(target, amount)) return false;
        soulDamageFeedback(target, attacker, amount);
        tryForceKillIfBelowThreshold(target, attacker);
        // 只播红闪/粒子，禁止再 hurt()：音波 1 点会走 set() 提前打开
        // 未定义凋灵 30 tick 限伤窗，把同一刀尚未结算的武器伤害整段吞掉。
        soulHitFeedbackVisual(target, soulSource);
        return true;
    }

    /**
     * 灵魂伤害通用入口（damage_soul 语义：几乎无视所有减伤/抗性/限伤/改血）。
     * 对所有实体统一直写扣血，绕开一切 setHealth 限伤/减伤/改血：
     *  - 外部 Mod：直写 SynchedEntityData 血量（含无敌帧清理 + Boss 条反馈）；
     *  - 下界 Apollyon：新旧两版血量双写（apollyonHealth 同步字段 + DATA_HEALTH_ID）；
     *  - 其余所有实体（普通 Apostle、非下界 Apollyon、玩家等）：通用直写血量。
     * 调用方需自行保证不递归（灵魂伤害自身触发的伤害事件不应再次进入）。
     */
    public static boolean applySoulDamage(LivingEntity target, LivingEntity attacker, float amount) {
        return applySoulDamageInternal(target, attacker, amount, false);
    }

    /** Soul damage emitted by an Undefined Wither; enables the generic delta channel. */
    public static boolean applyUndefinedSoulDamage(LivingEntity target, LivingEntity attacker, float amount) {
        return applySoulDamageInternal(target, attacker, amount, true);
    }

    /**
     * Applies damage to GoetyAwaken's Nameless One through its authoritative
     * combat-health manager. Its DATA_HEALTH value is only a mirror and is
     * restored from CURRENT_COMBAT_HEALTH, so generic direct writes appear to
     * work for one tick and then snap back.
     */
    private static boolean applyNamelessSoulDamage(LivingEntity target, LivingEntity attacker, float amount) {
        if (target == null || amount <= 0.0F || !Float.isFinite(amount)) return false;
        try {
            Class<?> namelessClass = Class.forName(
                    "com.k1sak1.goetyawaken.common.entities.hostile.undead.necromancer.AbstractNamelessOne");
            if (!namelessClass.isInstance(target)) return false;
            Method getManager = namelessClass.getMethod("getDamageCapManager");
            Object manager = getManager.invoke(target);
            if (manager == null) return false;

            float before = target.getHealth();
            clearSoulDamageBlockers(target);
            try {
                manager.getClass().getMethod("setDamageCooldownTicks", int.class).invoke(manager, 0);
            } catch (Throwable ignored) {
            }
            Method reduce = manager.getClass().getMethod("applyCombatHealthReduction", float.class);
            reduce.invoke(manager, amount);
            target.invulnerableTime = 0;
            float after = target.getHealth();
            return after < before - 0.001F || (before > 0.0F && after <= 0.0F);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Calls Fantasy Ending's generic authoritative damage executor. This is
     * deliberately class-agnostic: EntityActuallyHurt probes the target's
     * health DataItem and writes it after the normal damage calculation.
     */
    private static boolean applySpecialAuthoritativeDamage(LivingEntity target, DamageSource source, float amount) {
        if (target == null || target.level().isClientSide || amount <= 0.0F
                || !Float.isFinite(amount) || isUndefinedWitherTarget(target)
                || !specialHealthProbeIsSafe(target.getClass())) {
            return false;
        }
        if (!ensureSpecialHurtExecutor()) {
            if (target.getMaxHealth() >= OP_BOSS_MIN_MAX_HEALTH) {
                LOGGER.warn("[SoulSword] special executor unavailable for {}", target.getClass().getName());
            }
            return false;
        }
        try {
            float before = target.getHealth();
            Object executor = SPECIAL_HURT_CTOR.newInstance(target);
            Object healthSlot = null;
            double beforeData = Double.NaN;
            if (SPECIAL_INDEX_TYPE != null) {
                healthSlot = SPECIAL_INDEX_TYPE.invoke(null, target);
                beforeData = readSpecialHealthSlot(target, healthSlot);
            }
            SPECIAL_HURT_METHOD.invoke(executor, source, amount, true);
            double afterData = readSpecialHealthSlot(target, healthSlot);
            if (Double.isFinite(beforeData) && Double.isFinite(afterData)
                    && beforeData - afterData >= 0.001D) {
                LOGGER.debug("[SoulSword] authoritative hit {}: {} -> {}",
                        target.getClass().getName(), beforeData, afterData);
                return true;
            }
            float after = target.getHealth();
            boolean effective = after < before - 0.001F || (before > 0.0F && after <= 0.0F);
            if (effective) {
                LOGGER.debug("[SoulSword] effective hit {}: {} -> {}",
                        target.getClass().getName(), before, after);
            }
            return effective;
        } catch (Throwable ignored) {
            if (target.getMaxHealth() >= OP_BOSS_MIN_MAX_HEALTH) {
                LOGGER.warn("[SoulSword] special executor failed for {}", target.getClass().getName());
            }
            return false;
        }
    }

    private static boolean ensureSpecialHurtExecutor() {
        if (SPECIAL_HURT_CHECKED) return SPECIAL_HURT_CTOR != null && SPECIAL_HURT_METHOD != null;
        synchronized (SoulSwordEventHandler.class) {
            if (SPECIAL_HURT_CHECKED) return SPECIAL_HURT_CTOR != null && SPECIAL_HURT_METHOD != null;
            try {
                Class<?> type = Class.forName("com.mega.uom.util.entity.EntityActuallyHurt");
                SPECIAL_HURT_CTOR = type.getConstructor(LivingEntity.class);
                SPECIAL_HURT_METHOD = type.getMethod("actuallyHurt", DamageSource.class, float.class, boolean.class);
                try {
                    Class<?> context = Class.forName("com.mega.uom.util.data.LivingEntityExpandedContext");
                    SPECIAL_INDEX_TYPE = context.getMethod("getIndexAndType", LivingEntity.class);
                } catch (Throwable ignored) {
                    SPECIAL_INDEX_TYPE = null;
                }
                LOGGER.info("[SoulSword] special executor loaded indexType={}", SPECIAL_INDEX_TYPE != null);
            } catch (Throwable ignored) {
                SPECIAL_HURT_CTOR = null;
                SPECIAL_HURT_METHOD = null;
            } finally {
                SPECIAL_HURT_CHECKED = true;
            }
            return SPECIAL_HURT_CTOR != null && SPECIAL_HURT_METHOD != null;
        }
    }

    private static boolean specialHealthProbeIsSafe(Class<?> type) {
        Class<?> current = type;
        while (current != null && LivingEntity.class.isAssignableFrom(current)) {
            for (Field field : current.getDeclaredFields()) {
                String normalized = field.getName().replace("_", "").toUpperCase(Locale.ROOT);
                if (!Modifier.isStatic(field.getModifiers())
                        || !normalized.contains("HEALTH")
                        || normalized.contains("MAXHEALTH")
                        || !EntityDataAccessor.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    if (field.get(null) == null) return false;
                } catch (Throwable ignored) {
                    return false;
                }
            }
            current = current.getSuperclass();
        }
        return true;
    }

    private static double readSpecialHealthSlot(LivingEntity target, Object healthSlot) {
        if (healthSlot == null) return Double.NaN;
        try {
            Method indexMethod = healthSlot.getClass().getMethod("index");
            int index = ((Number) indexMethod.invoke(healthSlot)).intValue();
            Object item = findDataItemById(target.getEntityData(), index);
            if (item == null) return Double.NaN;
            Field valueField = findField(item.getClass(), "value", "f_135391_");
            Object value = valueField == null ? null : valueField.get(item);
            return value instanceof Number number ? number.doubleValue() : Double.NaN;
        } catch (Throwable ignored) {
            return Double.NaN;
        }
    }

    private static boolean isUndefinedWitherTarget(LivingEntity target) {
        return target instanceof net.minecraft.world.entity.boss.wither.WitherBoss wither
                && wither_title.WitherTitleHelper.isUndefinedWither(wither);
    }

    private static boolean applySoulDamageInternal(LivingEntity target, LivingEntity attacker,
                                                    float amount, boolean allowDelta) {
        if (target == null || attacker == null || !target.isAlive()) return false;
        if (target.level().isClientSide) return false;

        DamageSource soulSource = soulDamageSource(target.level(), attacker, attacker);

        // The external mod's final skill uses its own actuallyHurt(..., true) executor.
        // That executor bypasses LivingEntity.hurt(), then writes the
        // authoritative health DataItem. Reuse it generically when available;
        // the preflight rejects malformed optional entities before the external mod's own
        // scanner can turn a null accessor into a crash.
        if (allowDelta && applySpecialAuthoritativeDamage(target, soulSource, amount)) {
            soulDamageFeedback(target, attacker, amount);
            soulHitFeedbackVisual(target, soulSource);
            tryForceKillIfBelowThreshold(target, attacker);
            return true;
        }

        // Hidden-health targets must use their authoritative store before the
        // generic delta channel. A subclass may override getHealth(), so a
        // shadow write can otherwise be reported as effective while leaving
        // the real health unchanged.
        if (isApollyonForm(target)) {
            if (apollyonSoulDamage(target, attacker, amount, !allowDelta)) return true;
            // Undefined Wither attacks must not re-enter hurt() after the
            // authoritative path fails: title-phase targets can cancel that
            // path and recreate their external invulnerability state.
            if (allowDelta) return false;
            return soulDamageFallbackHurt(target, attacker, amount);
        }

        // Undefined Wither attacks use the generic effective-health channel first.
        // It is independent of hurt()/setHealth(), so targets that cancel hurt in a
        // phase or apply a per-hit cap still receive the delta.  The bridge selects
        // the active storage (Fantasy Ending when present, local fallback otherwise)
        // without naming any target mod or entity class.
        if (allowDelta && HealthDeltaBridge.applyDamage(target, amount)) {
            soulDamageFeedback(target, attacker, amount);
            // A delta hit has already applied the complete amount. Use visual
            // feedback only so a target's ordinary hurt event cannot recreate
            // an external invulnerability timer after the bypass.
            soulHitFeedbackVisual(target, soulSource);
            tryForceKillIfBelowThreshold(target, attacker);
            return true;
        }

        // GoetyAwaken 无名者的权威血量保存在 DamageCapManager 的
        // CURRENT_COMBAT_HEALTH 中。DATA_HEALTH 只是同步镜像，直接改它会
        // 在下一 tick 被覆盖；尾杀/黑洞的 delta 通道必须先走这里。
        if (allowDelta && applyNamelessSoulDamage(target, attacker, amount)) {
            soulDamageFeedback(target, attacker, amount);
            soulHitFeedback(target, soulSource);
            tryForceKillIfBelowThreshold(target, attacker);
            return true;
        }

        // 所有实体均走直写改血，绕过 setHealth 及其所有限伤重写
        clearBossInvulnFrames(target);
        float hpBeforeWrite = target.getHealth();
        if (directWriteHealth(target, amount, allowDelta)) {
            // 泛用行为探测：直写后回读 getHealth()。EXALTED 型隐藏血（如泽林变体）不认
            // DATA_HEALTH，直写只是改了个它不看的字段，血量纹丝不动 → 判定直写无效，
            // 改走 hurt() 灌注，触发目标自身真正扣血的那条路径。零 mod 类名硬编码。
            if (isIneffectiveDirectWrite(target, hpBeforeWrite, amount)) {
                return soulDamageFallbackHurt(target, attacker, amount);
            }
            soulDamageFeedback(target, attacker, amount);
            soulHitFeedback(target, soulSource);
            tryForceKillIfBelowThreshold(target, attacker);
            return true;
        }
        // 直写失败兜底：清命中冷却/无敌帧后正常 hurt()
        return soulDamageFallbackHurt(target, attacker, amount);
    }

    /**
     * 泛用行为探测：判断一次直写扣血是否真正生效，无需识别具体 mod 实体。
     * 依据直写前后 getHealth() 的变化量：
     *  - 普通目标：getHealth 读 DATA_HEALTH，直写改的就是它，血量按量下降 → 生效；
     *  - EXALTED 型隐藏血（getHealth 被重写、真血不在 DATA_HEALTH 且每 tick 用真血覆盖回
     *    DATA_HEALTH，如泽林变体 VariantZsieinEntity）：直写只动了它不看的字段，血量几乎不变
     *    → 无效，应改走 hurt() 灌注。
     * 玩家（走灵魂之心专用直写）与未定义凋灵（走隐藏血专用直写）各有成熟路径，不参与此判定，
     * 避免把它们的正常直写误判为无效而错误回落 hurt。
     * 仅当伤害本身有意义（>1F）且血量下降不足 0.5F 时判为无效，规避微量伤害误判。
     */
    private static boolean isIneffectiveDirectWrite(LivingEntity target, float hpBefore, float amount) {
        if (target instanceof Player) return false;
        if (target instanceof net.minecraft.world.entity.boss.wither.WitherBoss wither
                && wither_title.WitherTitleHelper.isUndefinedWither(wither)) return false;
        if (amount <= 1.0F) return false;
        return hpBefore - target.getHealth() < 0.5F;
    }

    /**
     * 直写命中的视觉反馈（不走 hurt()）：
     *  - 红色受击闪烁：广播伤害事件包（客户端 handleDamageEvent 会把实体闪红）；
     *  - 灵魂沙式蓝色粒子：以目标为中心向四周爆出。
     */
    private static void soulHitFeedbackVisual(LivingEntity target, DamageSource soulSource) {
        try {
            target.level().broadcastDamageEvent(target, soulSource);
        } catch (Throwable ignored) {
        }
        if (target.level() instanceof ServerLevel serverLevel) {
            double x = target.getX();
            double y = target.getY() + target.getBbHeight() * 0.5D;
            double z = target.getZ();
            serverLevel.sendParticles(ParticleTypes.SOUL, x, y, z, 24, 0.6D, 0.6D, 0.6D, 0.4D);
            serverLevel.sendParticles(ParticleTypes.SOUL, x, y, z, 10, 0.15D, 0.2D, 0.15D, 0.15D);
        }
    }

    /**
     * 直写命中反馈：视觉 + 1 点音波 hurt（给其它 Boss 补全身发红）。
     * 未定义凋灵的纯粹恶意路径禁止走音波 hurt，见 applyPureMaliceDamage。
     */
    private static void soulHitFeedback(LivingEntity target, DamageSource soulSource) {
        soulHitFeedbackVisual(target, soulSource);
        // 附加 1 点音波伤害：走 hurt 流程产生 BOSS 受击全身发红反馈（直写扣血本身无 hurt 反馈）
        // 有来源（soulSource 的 causing，玩家/凋灵），SONIC_FEEDBACK 守卫防止
        // hurt → LivingDamageEvent(onSoulSwordAttack) → 再直写 → 再反馈 的递归循环
        // hurt 前清无敌帧：黑洞/右键每 10 tick 一跳，上一跳 hurt 设的 invulnerableTime=20
        // 会把后续音波 hurt 前置拦截（拦截时不广播伤害包 → 无红闪），必须每跳清零
        if (!SONIC_FEEDBACK.get() && target.isAlive() && !target.isDeadOrDying()) {
            SONIC_FEEDBACK.set(true);
            try {
                target.invulnerableTime = 0;
                target.hurt(target.level().damageSources().sonicBoom(soulSource.getEntity()), 1.0F);
            } catch (Throwable ignored) {
            } finally {
                SONIC_FEEDBACK.set(false);
            }
        }
    }

    /** 下界 Apollyon 判定：isApollyon 且位于下界（其血量走 RevelationFix 同步字段） */
    private static boolean isNetherApollyon(LivingEntity target) {
        if (!isApollyonForm(target)) return false;
        try {
            return (Boolean) Class.forName("com.Polarice3.Goety.common.entities.boss.Apostle")
                    .getMethod("isInNether").invoke(target);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 下界 Apollyon 专属直写：兼容新旧两版血量存储。
     *  - 旧版（2.3.2 / RevelationFix 4.2）：血量存于 apollyonHealth 同步字段；
     *  - 新版（2.3.3fix）：血量回到普通 DATA_HEALTH_ID。
     * 两处都扣，实际生效的那处必中；绕开 setHealth 的每击上限/命中冷却/永久减伤。
     */
    private static boolean apollyonSoulDamage(LivingEntity target, LivingEntity attacker,
                                              float amount, boolean allowHurtFallback) {
        try {
            Class<?> apostleClass = Class.forName("com.Polarice3.Goety.common.entities.boss.Apostle");
            if (!apostleClass.isInstance(target)) return false;

            clearBossInvulnFrames(target);
            boolean wrote = false;
            // First use the same generic authoritative DataItem probe as the external mod.
            // This bypasses hurt(), setHealth(), and RevelationFix hitCooldown
            // without naming a target-specific field or mixin.
            wrote = writeHiddenHealthByAmount(target, amount);
            // 旧版 RevelationFix：直写 apollyonHealth 同步字段
            try {
                Method getHp = apostleClass.getMethod("revelaionfix$getApollyonHealth");
                Method setHp = apostleClass.getMethod("revelaionfix$setApollyonHealth", float.class);
                float current = (Float) getHp.invoke(target);
                if (current > 0.0F) {
                    setHp.invoke(target, Math.max(0.0F, current - amount));
                    float after = (Float) getHp.invoke(target);
                    wrote = decreased(current, after);
                }
            } catch (Throwable ignored) {
            }
            // 新版 RevelationFix 4.4：revelaionfix$setApollyonHealth 运行时被 ASM 改名（防篡改），
            // 反射找不到；血量实际在 EndingLib 的 MegaCapability，走未改名的 EventUtil.getData/setData。
            if (!wrote) {
                try {
                    Class<?> eventUtilClass = Class.forName("com.mega.revelationfix.util.EventUtil");
                    Method getData = eventUtilClass.getMethod("getData", apostleClass);
                    Method setData = eventUtilClass.getMethod("setData", apostleClass, float.class);
                    float current = (Float) getData.invoke(null, target);
                    if (current > 0.0F) {
                        setData.invoke(null, target, Math.max(0.0F, current - amount));
                        float after = (Float) getData.invoke(null, target);
                        wrote = decreased(current, after);
                    }
                } catch (Throwable ignored) {
                }
            }
            // 新版 2.3.3fix：血量在普通 DATA_HEALTH_ID，直接扣
            if (!wrote) {
                float beforeDirect = target.getHealth();
                boolean rawWritten = directWriteRawHealth(target, amount);
                if (rawWritten) {
                    // DATA_HEALTH may be only a mirror on older RevelationFix
                    // builds. Accept it only when the effective health also
                    // moved; a changed mirror alone is not damage.
                    wrote = decreased(beforeDirect, target.getHealth());
                } else if (directWriteHealth(target, amount)) {
                    wrote = decreased(beforeDirect, target.getHealth());
                }
            }

            if (!wrote) {
                LOGGER.warn("[SoulSword] Apollyon authoritative health write failed");
                return allowHurtFallback && soulDamageFallbackHurt(target, attacker, amount);
            }
            soulDamageFeedback(target, attacker, amount);
            // 受击反馈（红闪 + 1点音波）：与通用直写路径一致，
            // 否则 Apollyon 分流后右键AOE/黑洞/骷髅附加全是"纯改血无反馈"
            // 外部 LivingHurtEvent 可能重新打开目标的受击保护；直写已经
            // 完成实际伤害，这里只发视觉反馈，避免为了红闪再进入 hurt()
            // 触发目标自己的限伤链。
            soulHitFeedbackVisual(target, soulDamageSource(target.level(), attacker, attacker));
            // soulHitFeedback 使用一次 sonicBoom hurt 产生红闪；Apollyon 的
            // actuallyHurt 会因此重新写入 30 tick hitCooldown，必须在本次攻击
            // 完成后清掉，否则下一次黑洞跳跃会在 hurt HEAD 被直接拒绝。
            clearBossInvulnFrames(target);
            return true;
        } catch (Throwable t) {
            LOGGER.warn("[SoulSword] Apollyon health field direct write failed, falling back: " + t);
            float beforeDirect = target.getHealth();
            boolean rawWritten = directWriteRawHealth(target, amount);
            boolean directWritten = rawWritten || directWriteHealth(target, amount);
            if (directWritten && decreased(beforeDirect, target.getHealth())) {
                soulDamageFeedback(target, attacker, amount);
                soulHitFeedbackVisual(target, soulDamageSource(target.level(), attacker, attacker));
                clearBossInvulnFrames(target);
                return true;
            }
            return allowHurtFallback && soulDamageFallbackHurt(target, attacker, amount);
        }
    }

    private static boolean decreased(float before, float after) {
        return Float.isFinite(before) && Float.isFinite(after)
                && (before - after >= 0.001F || (before > 0.0F && after <= 0.0F));
    }

    /** 直写扣血后的击杀归属/死亡播报反馈（仅玩家攻击者需要） */
    private static void soulDamageFeedback(LivingEntity target, LivingEntity attacker, float amount) {
        if (attacker instanceof Player player) {
            recordSoulDamageFeedback(target, player, amount);
        }
    }

    /** 直写失败兜底：清命中冷却/无敌帧后走正常 hurt() */
    private static boolean soulDamageFallbackHurt(LivingEntity target, LivingEntity attacker, float amount) {
        clearSoulDamageBlockers(target);
        target.invulnerableTime = 0;
        SOUL_DAMAGING.set(true);
        try {
            return target.hurt(soulDamageSource(target.level(), attacker, attacker), amount);
        } finally {
            SOUL_DAMAGING.set(false);
        }
    }

    /** 清除常规限伤拦截：原版无敌帧 + Apostle.moddedInvul + 两套命中冷却（不碰 obsidianInvul 结界） */
    private static void clearSoulDamageBlockers(LivingEntity target) {
        target.invulnerableTime = 0;
        target.hurtTime = 0;
        for (String fieldName : new String[]{"customInvulnerableTime", "moddedInvul"}) {
            try {
                Field f;
                try {
                    f = target.getClass().getField(fieldName);
                } catch (NoSuchFieldException ignored) {
                    f = target.getClass().getDeclaredField(fieldName);
                }
                f.setAccessible(true);
                if (f.getType() == int.class) {
                    f.setInt(target, 0);
                }
            } catch (Throwable ignored) {
            }
        }
        // GoetyRevelation 命中冷却
        try {
            target.getClass().getMethod("allTitlesApostle_1_20_1$setHitCooldown", int.class).invoke(target, 0);
        } catch (Throwable ignored) {
        }
        // RevelationFix 命中冷却（HIT_COOLDOWN2）
        try {
            target.getClass().getMethod("revelaionfix$setHitCooldown", int.class).invoke(target, 0);
        } catch (Throwable ignored) {
        }
    }

    /** 按顺序尝试多个字段名（Mojang 开发名 / SRG 运行时名），找到即返回并放开访问 */
    private static Field findField(Class<?> clazz, String... names) {
        for (String name : names) {
            try {
                Field f = clazz.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    /** OP 级 Boss 判定：非玩家、非驯服生物，且最大生命 >= 阈值 */
    private static boolean isOpBoss(LivingEntity target) {
        if (target instanceof Player) return false;
        if (target instanceof TamableAnimal tamed && tamed.isTame()) return false;
        return target.getMaxHealth() >= OP_BOSS_MIN_MAX_HEALTH;
    }

    /** 清掉 Boss 自定义的"特殊无敌帧"，让灵魂伤害（以及后续攻击）必定命中 */
    private static void clearBossInvulnFrames(LivingEntity target) {
        target.invulnerableTime = 0;
        target.hurtTime = 0;
        // 外部 Mod 的 customInvulnerableTime、Goety Apostle 的 moddedInvul / obsidianInvul
        for (String fieldName : new String[]{"customInvulnerableTime", "moddedInvul", "obsidianInvul"}) {
            try {
                java.lang.reflect.Field f;
                try {
                    f = target.getClass().getField(fieldName);
                } catch (NoSuchFieldException ignored) {
                    f = target.getClass().getDeclaredField(fieldName);
                }
                f.setAccessible(true);
                if (f.getType() == int.class) {
                    f.setInt(target, 0);
                }
            } catch (Throwable ignored) {
            }
        }
        // GoetyRevelation 的 hitCooldown（mixin 注入的公开方法）
        try {
            java.lang.reflect.Method m = target.getClass().getMethod("allTitlesApostle_1_20_1$setHitCooldown", int.class);
            m.invoke(target, 0);
        } catch (Throwable ignored) {
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        boolean has = hasSoulSword(player);

        AttributeInstance armor = player.getAttribute(Attributes.ARMOR);
        if (armor != null) {
            if (has && armor.getModifier(ARMOR_UUID) == null) {
                armor.addPermanentModifier(new AttributeModifier(ARMOR_UUID,
                        "SoulSword armor", 40, AttributeModifier.Operation.ADDITION));
            } else if (!has) {
                armor.removeModifier(ARMOR_UUID);
            }
        }

        AttributeInstance toughness = player.getAttribute(Attributes.ARMOR_TOUGHNESS);
        if (toughness != null) {
            if (has && toughness.getModifier(TOUGHNESS_UUID) == null) {
                toughness.addPermanentModifier(new AttributeModifier(TOUGHNESS_UUID,
                        "SoulSword toughness", 20, AttributeModifier.Operation.ADDITION));
            } else if (!has) {
                toughness.removeModifier(TOUGHNESS_UUID);
            }
        }

        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            if (has && speed.getModifier(SPEED_UUID) == null) {
                speed.addPermanentModifier(new AttributeModifier(SPEED_UUID,
                        "SoulSword speed", 0.2, AttributeModifier.Operation.MULTIPLY_BASE));
            } else if (!has) {
                speed.removeModifier(SPEED_UUID);
            }
        }

        AttributeInstance kb = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        boolean blocking = player.isUsingItem()
                && player.getUseItem().getItem() instanceof SoulSwordItem;
        if (kb != null) {
            if (blocking && kb.getModifier(KB_RESIST_UUID) == null) {
                kb.addPermanentModifier(new AttributeModifier(KB_RESIST_UUID,
                        "SoulSword knockback", 10.0, AttributeModifier.Operation.ADDITION));
            } else if (!blocking) {
                kb.removeModifier(KB_RESIST_UUID);
            }
        }

        if (has && player.getHealth() < player.getMaxHealth()) {
            float healPerTick = player.getMaxHealth() * 0.05f / 20f;
            player.heal(healPerTick);
        }

        if (has && player.tickCount % 20 == 0) {
            player.getFoodData().eat(2, 0.5f);
        }

        if (has && !player.level().isClientSide() && player.tickCount % 5 == 0) {
            clearNearbyFireTornadoes(player);
        }

        // 伤害适应过期清理 + 直写改血防护（快照回滚）
        if (!player.level().isClientSide()) {
            pruneAdaptation(player);
            restoreUnexplainedHealth(player);
        }

        if (!player.level().isClientSide()) {
            SOUL_HURT_DEFEND.computeIfPresent(player.getUUID(), (k, v) -> v <= 1 ? null : v - 1);
            TOTEM_COOLDOWNS.computeIfPresent(player.getUUID(), (k, v) -> v <= 1 ? null : v - 1);
        }

        // 客户端：复活后 30 秒 soulHurtDefend 窗口内，生成少量灵魂粒子环绕玩家
        if (player.level().isClientSide()) {
            UUID id = player.getUUID();
            int remaining = CLIENT_SOUL_HURT_DEFEND.getOrDefault(id, 0);
            if (remaining > 0) {
                // 每 4 tick 生成 2 个灵魂粒子，围绕玩家随机飘散
                if (player.tickCount % 4 == 0) {
                    double x = player.getX() + (player.getRandom().nextDouble() - 0.5) * 1.8;
                    double y = player.getY() + player.getRandom().nextDouble() * 2.0;
                    double z = player.getZ() + (player.getRandom().nextDouble() - 0.5) * 1.8;
                    player.level().addParticle(ParticleTypes.SOUL, x, y, z,
                            0, 0.04, 0);
                    player.level().addParticle(ParticleTypes.SOUL,
                            player.getX() + (player.getRandom().nextDouble() - 0.5) * 1.8,
                            player.getY() + player.getRandom().nextDouble() * 2.0,
                            player.getZ() + (player.getRandom().nextDouble() - 0.5) * 1.8,
                            0, 0.04, 0);
                }
                CLIENT_SOUL_HURT_DEFEND.put(id, remaining - 1);
            } else {
                CLIENT_SOUL_HURT_DEFEND.remove(id);
            }
        }
    }

    /** 自定义受击无敌帧是否生效（复活后 30 秒窗口，由 mixin 拦截 HEALTH 扣血写入） */
    public static boolean isSoulHurtDefendActive(Player player) {
        return SOUL_HURT_DEFEND.getOrDefault(player.getUUID(), 0) > 0;
    }

    /** 客户端侧设置 soulHurtDefend 剩余时长，由 S2CSoulSwordTotemPack 调用 */
    public static void setClientSoulHurtDefend(UUID uuid, int ticks) {
        CLIENT_SOUL_HURT_DEFEND.put(uuid, ticks);
    }

    /**
     * 外部 Mod 式"改底层血量"亡语防护：格挡时每 tick 记录血量快照，
     * 若血量下降超出本 tick 已通过正常伤害事件结算的量，说明是绕过事件直写血量
     * （catchSetTrueHealth / 健康修正等），把多扣的部分改回快照值。
     */
    private static void restoreUnexplainedHealth(Player player) {
        UUID id = player.getUUID();
        boolean blocking = isBlockingSoulSword(player);
        // soulHurtDefend 窗口内即使不格挡也执行快照回滚：
        // 事件层（取消/归零）和 set() 层（mixin）拦不住的 VarHandle 直写（外部 Mod catchSetTrueHealth 等）
        // 只能靠每 tick 快照回滚兜住，否则窗口内血会被直写慢慢抽干
        boolean defending = isSoulHurtDefendActive(player);
        if ((blocking || defending) && !player.isRemoved()) {
            // 外部 Mod 的健康修正会把 getHealth 钳制在 最大生命+delta，先清空 delta 才能按原始血回滚
            resetSpecialHealthDelta(player);
            float explained = EXPLAINED_DAMAGE.getOrDefault(id, 0.0F);
            Float snapshot = BLOCK_HEALTH_SNAPSHOT.get(id);
            if (snapshot != null && snapshot > 0.0F) {
                float expected = snapshot - explained;
                float current = player.getHealth();
                if (expected > 0.0F && current < expected - RESTORE_EPSILON) {
                    float restore = Math.min(expected - current, player.getMaxHealth() - current);
                    if (restore > RESTORE_EPSILON) {
                        player.setHealth(current + restore);
                        // 直写可能已把玩家打进死亡流程，解除死亡标记/死亡计时
                        reviveFromForcedDeath(player);
                    }
                }
            }
            BLOCK_HEALTH_SNAPSHOT.put(id, player.getHealth());
        } else {
            BLOCK_HEALTH_SNAPSHOT.remove(id);
        }
        EXPLAINED_DAMAGE.remove(id);
    }

    /** 清空外部 Mod 的 FE_GET_HEALTH_DATA 健康修正（delta），使 getHealth 不再被钳制 */
    private static void resetSpecialHealthDelta(Player player) {
        try {
            Class<?> utilClass = Class.forName("com.mega.uom.util.entity.EntityASMUtil");
            Object delta = utilClass.getMethod("getHealthDelta", LivingEntity.class).invoke(null, player);
            if (delta instanceof Float f && Math.abs(f) > RESTORE_EPSILON) {
                utilClass.getMethod("setHealthDelta", LivingEntity.class, float.class)
                        .invoke(null, player, 0.0F);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 直写伤害把血量清零后，若玩家已被强制标记死亡，解除死亡状态使其继续存活 */
    private static void reviveFromForcedDeath(Player player) {
        if (!player.isDeadOrDying() && player.getHealth() > 0.0F) return;
        try {
            ((LivingEntityAccessor) player).shatter$setDead(false);
            ((LivingEntityAccessor) player).shatter$setDeathTime(0);
        } catch (Throwable ignored) {
        }
        // 外部 Mod 的 livingEC isDead 标记（若被置位会强制 getHealth()=0 / isDeadOrDying=true），一并清除
        try {
            Class<?> ecInterface = Class.forName("com.mega.uom.util.itf.LivingEntityEC");
            if (ecInterface.isInstance(player)) {
                Object ec = ecInterface.getMethod("uom$livingECData").invoke(player);
                Field isDead = ec.getClass().getField("isDead");
                isDead.setBoolean(ec, false);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 是否正在右键格挡无垠长魂之剑 */
    private static boolean isBlockingSoulSword(Player player) {
        return player.isUsingItem() && player.getUseItem().getItem() instanceof SoulSwordItem;
    }

    /** 攻击者是否为 Goety 的 Apostle（亚波伦） */
    private static boolean isApostleAttacker(Entity attacker) {
        try {
            return Class.forName("com.Polarice3.Goety.common.entities.boss.Apostle").isInstance(attacker);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 伤害类型标识：优先用注册表 key（同种伤害类型共享同一计数），无 key 时退回 msgId */
    private static String damageTypeKey(DamageSource source) {
        return source.typeHolder().unwrapKey()
                .map(key -> key.location().toString())
                .orElseGet(source::getMsgId);
    }

    /** 是否为亚波伦下界机关箭（revelationfix:doom_arrow） */
    private static boolean isDoomArrowSource(DamageSource source) {
        return source.typeHolder().unwrapKey()
                .map(key -> {
                    String path = key.location().getPath();
                    return key.location().getNamespace().equals("revelationfix") && path.equals("doom_arrow");
                })
                .orElse(false);
    }

    private static void recordAdaptationHit(Player player, DamageSource source) {
        long now = player.level().getGameTime();
        String type = damageTypeKey(source);
        Map<String, AdaptationEntry> perPlayer = ADAPTATION.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
        AdaptationEntry entry = perPlayer.get(type);
        if (entry == null) {
            perPlayer.put(type, new AdaptationEntry(1, now));
        } else {
            entry.hits++;
            entry.lastHitTick = now;
        }
    }

    /** 该伤害类型当前的适应额外减伤（0 = 首次/未适应，之后 0.2/0.4/…/0.8） */
    private static float getAdaptationReduction(Player player, DamageSource source) {
        Map<String, AdaptationEntry> perPlayer = ADAPTATION.get(player.getUUID());
        if (perPlayer == null) return 0.0F;
        AdaptationEntry entry = perPlayer.get(damageTypeKey(source));
        if (entry == null) return 0.0F;
        return Math.min(MAX_ADAPTATION, ADAPTATION_STEP * (entry.hits - 1));
    }

    /** 移除超过 20 秒未再命中的伤害类型计数，使其回到普通格挡减伤流程 */
    private static void pruneAdaptation(Player player) {
        long now = player.level().getGameTime();
        Map<String, AdaptationEntry> perPlayer = ADAPTATION.get(player.getUUID());
        if (perPlayer == null) return;
        perPlayer.entrySet().removeIf(e -> now - e.getValue().lastHitTick > ADAPTATION_WINDOW_TICKS);
        if (perPlayer.isEmpty()) {
            ADAPTATION.remove(player.getUUID());
        }
    }

    private static final class AdaptationEntry {
        int hits;
        long lastHitTick;

        AdaptationEntry(int hits, long lastHitTick) {
            this.hits = hits;
            this.lastHitTick = lastHitTick;
        }
    }

    private static void clearNearbyFireTornadoes(Player player) {
        for (Entity e : player.level().getEntities(player,
                player.getBoundingBox().inflate(10.0D))) {
            if (FIRE_TORNADO_TYPE.equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()))) {
                e.discard();
            }
        }
    }

    static boolean isApollyonForm(LivingEntity entity) {
        try {
            Class<?> apostleClass = Class.forName("com.Polarice3.Goety.common.entities.boss.Apostle");
            if (!apostleClass.isInstance(entity)) return false;
            try {
                java.lang.reflect.Method m = apostleClass.getDeclaredMethod("isApollyon");
                m.setAccessible(true);
                return (Boolean) m.invoke(entity);
            } catch (NoSuchMethodException e) {
                try {
                    java.lang.reflect.Method m = apostleClass.getDeclaredMethod("allTitlesApostle_1_20_1$isApollyon");
                    m.setAccessible(true);
                    return (Boolean) m.invoke(entity);
                } catch (NoSuchMethodException e2) {
                    return false;
                }
            }
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean hasSoulSword(Player player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof SoulSwordItem) return true;
        }
        for (ItemStack stack : player.getInventory().armor) {
            if (stack.getItem() instanceof SoulSwordItem) return true;
        }
        if (player.getOffhandItem().getItem() instanceof SoulSwordItem) return true;
        return false;
    }

    public static boolean hasSoulSwordInHands(Player player) {
        return player.getMainHandItem().getItem() instanceof SoulSwordItem
                || player.getOffhandItem().getItem() instanceof SoulSwordItem;
    }
}
