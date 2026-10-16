package weapon.starcrystal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import wither_title.BallLightningEntity;
import wither_title.GravitySingularityEntity;
import wither_title.StarCoronaBeamEntity;
import wither_title.WitherTitleHelper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Mod.EventBusSubscriber(modid = "malice_enhanced", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StarCoronaCrystalEvents {
    private static final UUID ARMOR_UUID = UUID.fromString("6e3c1a90-7c21-4d8a-9b44-11aa22bb33c1");
    private static final UUID ATTACK_SPEED_UUID = UUID.fromString("6e3c1a90-7c21-4d8a-9b44-11aa22bb33c3");
    private static final UUID ALLAY_MAX_HEALTH_UUID = UUID.fromString("6e3c1a90-7c21-4d8a-9b44-11aa22bb33c4");
    private static final UUID ALLAY_ARMOR_UUID = UUID.fromString("6e3c1a90-7c21-4d8a-9b44-11aa22bb33c5");
    private static final float ASCENSION_DAMAGE_FACTOR = 0.56F;
    private static final float SOUL_HEART_DAMAGE_CAP = 20.0F;
    private static final int SOUL_HEART_HURT_DEFEND_TICKS = 30;
    private static final String SERVANT_EXPIRE = "malice_enhanced:star_allay_expire";
    private static final String SERVANT_OWNER = "malice_enhanced:star_allay_owner";
    private static final String PLAYER_SINGULARITY = "malice_enhanced:player_singularity";
    private static final String FOCUS_PROJECTILE = "malice_enhanced:star_focus_projectile";
    private static final String OVERCHARGE_PROJECTILE = "malice_enhanced:star_overcharge_projectile";

    private static final Map<UUID, Long> RESPAWN_TELEPORT_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> HIT_HEAL_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> SERVANT_SUMMON_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> SINGULARITY_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> BALL_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> REVIVAL_SHIELD_UNTIL = new HashMap<>();
    private static final Map<UUID, Integer> FOCUS_PENDING = new HashMap<>();
    private static final Map<UUID, Boolean> OVERCHARGE_PENDING = new HashMap<>();
    private static final Map<UUID, Long> SOUL_HEART_HURT_DEFEND_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> SOUL_HEART_HURT_DEFEND_START = new HashMap<>();
    private static final Map<UUID, Long> SOUL_HEART_DAMAGE_TICK = new HashMap<>();
    private static final Map<UUID, Float> SOUL_HEART_DAMAGE_USED = new HashMap<>();
    private static final Map<UUID, Integer> BOW_USE_TICKS = new HashMap<>();
    private static final Map<UUID, Boolean> LONG_CHARGE_READY = new HashMap<>();
    private static final Map<UUID, boolean[]> SOUL_FLIGHT_SAVED = new HashMap<>();
    /** The flight state most recently chosen by the player (double-jump). */
    private static final Map<UUID, Boolean> SOUL_FLIGHT_REQUESTED = new HashMap<>();
    private static final Map<UUID, Boolean> SOUL_FLIGHT_TICK_STATE = new HashMap<>();
    private static final WeakHashMap<ServerLevel, Long> CLEANUP_TICK_BY_LEVEL = new WeakHashMap<>();
    private static final WeakHashMap<Level, Map<UUID, Integer>> CROSSBOW_USE_TICKS = new WeakHashMap<>();
    private static final WeakHashMap<Level, Map<UUID, Integer>> BOW_ACCELERATION_TICKS = new WeakHashMap<>();
    private static final ThreadLocal<Boolean> BONUS_DAMAGE = ThreadLocal.withInitial(() -> false);
    /**
     * Players whose LivingEntity.hurt() call is currently being processed.
     * Health writes made from inside hurt() already went through LivingHurtEvent,
     * so the data-layer guard must not apply direct-write mitigation a second time.
     */
    private static final ThreadLocal<Map<UUID, Integer>> SOUL_HEART_HURT_CONTEXT =
            ThreadLocal.withInitial(HashMap::new);

    /**
     * State that must be temporarily opened for bonus damage. Several bosses
     * keep a second invulnerability timer outside LivingEntity.invulnerableTime.
     * The values are restored after the nested hurt() call so the outer hit can
     * finish normally and the boss does not lose its regular attack cadence.
     */
    private static final class SpecialInvulnerabilityState {
        private static final String[] TIMER_FIELDS = {
                "customInvulnerableTime", // Fantasy Ending / external mod
                "moddedInvul"              // Goety bosses
        };
        private static final String[][] COOLDOWN_ACCESSORS = {
                {"allTitlesApostle_1_20_1$getHitCooldown", "allTitlesApostle_1_20_1$setHitCooldown"},
                {"revelaionfix$getHitCooldown", "revelaionfix$setHitCooldown"}
        };

        private final LivingEntity target;
        private final int invulnerableTime;
        private final int hurtTime;
        private final int hurtDuration;
        private final float lastHurt;
        private final Map<Field, Object> fields = new HashMap<>();
        private final Map<Method, Integer> cooldowns = new HashMap<>();

        private SpecialInvulnerabilityState(LivingEntity target) {
            this.target = target;
            this.invulnerableTime = target.invulnerableTime;
            this.hurtTime = target.hurtTime;
            this.hurtDuration = target.hurtDuration;
            this.lastHurt = target.lastHurt;

            target.invulnerableTime = 0;
            target.hurtTime = 0;
            target.lastHurt = 0.0F;
            for (String name : TIMER_FIELDS) {
                Field field = findField(target.getClass(), name);
                if (field == null || field.getType() != int.class) continue;
                try {
                    fields.put(field, field.get(target));
                    field.setInt(target, 0);
                } catch (Throwable ignored) {
                }
            }

            // maxDamageTaken is a cap, not a generic timer. It is opened only
            // for Goety's Apostle, whose class owns this exact field.
            if (target.getClass().getName().toLowerCase().contains("apostle")) {
                Field field = findField(target.getClass(), "maxDamageTaken");
                if (field != null && (field.getType() == float.class || field.getType() == double.class)) {
                    try {
                        fields.put(field, field.get(target));
                        if (field.getType() == float.class) field.setFloat(target, Float.MAX_VALUE);
                        else field.setDouble(target, Double.MAX_VALUE);
                    } catch (Throwable ignored) {
                    }
                }
            }

            for (String[] accessor : COOLDOWN_ACCESSORS) {
                Method getter = findMethod(target.getClass(), accessor[0]);
                Method setter = findMethod(target.getClass(), accessor[1], int.class);
                if (getter == null || setter == null) continue;
                try {
                    Object value = getter.invoke(target);
                    if (value instanceof Number number) {
                        cooldowns.put(setter, number.intValue());
                        setter.invoke(target, 0);
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        private void restore() {
            target.invulnerableTime = invulnerableTime;
            target.hurtTime = hurtTime;
            target.hurtDuration = hurtDuration;
            target.lastHurt = lastHurt;
            for (Map.Entry<Field, Object> entry : fields.entrySet()) {
                try {
                    entry.getKey().set(target, entry.getValue());
                } catch (Throwable ignored) {
                }
            }
            for (Map.Entry<Method, Integer> entry : cooldowns.entrySet()) {
                try {
                    entry.getKey().invoke(target, entry.getValue());
                } catch (Throwable ignored) {
                }
            }
        }

        private static Field findField(Class<?> type, String name) {
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                try {
                    Field field = current.getDeclaredField(name);
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException ignored) {
                } catch (Throwable ignored) {
                    return null;
                }
            }
            return null;
        }

        private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                try {
                    Method method = current.getDeclaredMethod(name, parameterTypes);
                    method.setAccessible(true);
                    return method;
                } catch (NoSuchMethodException ignored) {
                } catch (Throwable ignored) {
                    return null;
                }
            }
            try {
                Method method = type.getMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    private StarCoronaCrystalEvents() {
    }

    private static boolean has(LivingEntity entity, int title) {
        return StarCoronaCurios.hasTitle(entity, title);
    }

    private static boolean cooled(Map<UUID, Long> map, UUID id, long now, int ticks) {
        if (now < map.getOrDefault(id, 0L)) return false;
        map.put(id, now + ticks);
        return true;
    }

    /** Independent 1.5-second damage window granted by an equipped Soul Heart. */
    public static boolean isSoulHeartHurtDefendBlocking(Player player) {
        if (player == null || player.level().isClientSide || !StarCoronaCurios.hasSoulHeart(player)) return false;
        UUID id = player.getUUID();
        Long until = SOUL_HEART_HURT_DEFEND_UNTIL.get(id);
        if (until == null || player.level().getGameTime() >= until) return false;
        Long start = SOUL_HEART_HURT_DEFEND_START.get(id);
        return start == null || player.level().getGameTime() > start;
    }

    /** Remaining 20-point damage budget for this server tick. */
    public static float getSoulHeartRemainingTickBudget(Player player) {
        if (player == null) return 0.0F;
        UUID id = player.getUUID();
        long now = player.level().getGameTime();
        if (SOUL_HEART_DAMAGE_TICK.getOrDefault(id, Long.MIN_VALUE) != now) {
            SOUL_HEART_DAMAGE_TICK.put(id, now);
            SOUL_HEART_DAMAGE_USED.put(id, 0.0F);
        }
        return Math.max(0.0F, SOUL_HEART_DAMAGE_CAP - SOUL_HEART_DAMAGE_USED.getOrDefault(id, 0.0F));
    }

    public static void consumeSoulHeartTickBudget(Player player, float amount) {
        if (player == null || amount <= 0.0F) return;
        float remaining = getSoulHeartRemainingTickBudget(player);
        SOUL_HEART_DAMAGE_USED.put(player.getUUID(),
                Math.min(SOUL_HEART_DAMAGE_CAP, SOUL_HEART_DAMAGE_CAP - remaining + amount));
    }

    public static void activateSoulHeartHurtDefend(Player player) {
        if (player == null || player.level().isClientSide) return;
        long now = player.level().getGameTime();
        SOUL_HEART_HURT_DEFEND_UNTIL.put(player.getUUID(), now + SOUL_HEART_HURT_DEFEND_TICKS);
        SOUL_HEART_HURT_DEFEND_START.put(player.getUUID(), now);
    }

    /**
     * Marks the duration of a player's normal hurt() call. This is used by the
     * SynchedEntityData guard to distinguish event damage from direct health writes.
     */
    public static void beginSoulHeartHurt(Player player) {
        if (player == null) return;
        Map<UUID, Integer> context = SOUL_HEART_HURT_CONTEXT.get();
        UUID id = player.getUUID();
        context.put(id, context.getOrDefault(id, 0) + 1);
    }

    public static void endSoulHeartHurt(Player player) {
        if (player == null) return;
        Map<UUID, Integer> context = SOUL_HEART_HURT_CONTEXT.get();
        UUID id = player.getUUID();
        int depth = context.getOrDefault(id, 0) - 1;
        if (depth <= 0) context.remove(id);
        else context.put(id, depth);
        if (context.isEmpty()) SOUL_HEART_HURT_CONTEXT.remove();
    }

    public static boolean isSoulHeartHurtInProgress(Player player) {
        return player != null && SOUL_HEART_HURT_CONTEXT.get().containsKey(player.getUUID());
    }

    /**
     * Applies the Soul Heart's per-hit raw damage cap. This deliberately runs
     * before any title or ascension mitigation.
     */
    public static float capSoulHeartDamageBeforeReduction(Player player, float amount) {
        if (player == null || !StarCoronaCurios.hasSoulHeart(player)
                || amount <= 0.0F || Float.isNaN(amount)) return amount;
        return Math.min(amount, SOUL_HEART_DAMAGE_CAP);
    }

    /**
     * Applies mitigation for a direct health write after its raw amount has been
     * capped when a Soul Heart is present. Direct writes use the Particle Shield's
     * stronger 40% reduction; title mitigation still applies without a Soul Heart.
     */
    public static float reduceDirectHealthDamage(Player player, float amount) {
        if (player == null || amount <= 0.0F || Float.isNaN(amount)) return amount;
        amount = capSoulHeartDamageBeforeReduction(player, amount);
        amount *= StarCoronaCurios.getEquippedTitleDamageFactor(player);
        if (has(player, 1)) amount *= 0.60F;
        return amount;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        // Keep the Soul Heart flight permission present on both sides. The
        // server remains authoritative, while the client must also retain
        // mayfly when another ability syncs player abilities.
        updateSoulFlight(player);
        if (player.level().isClientSide) return;
        long now = player.level().getGameTime();
        applyAttributes(player);
        updateAscensionBenefits(player);
        updateSoulHeartDefenseState(player);
        if (player.level() instanceof ServerLevel level) {
            if (has(player, 6)) {
                if (player.getHealth() <= player.getMaxHealth() * 0.5F
                        && cooled(SERVANT_SUMMON_UNTIL, player.getUUID(), now, 600)) {
                    summonAllays(player, level);
                }
                maintainAllays(player, level, now);
            } else {
                discardAllays(player, level);
            }
            cleanupOrphanBeams(player, level, now);
            if (player.tickCount % 200 == 0 && CLEANUP_TICK_BY_LEVEL.getOrDefault(level, -1L) != now) {
                CLEANUP_TICK_BY_LEVEL.put(level, now);
                cleanupExpiredState(level, now);
            }
        }
    }

    /**
     * Restore flight after the whole dimension has ticked. No-flight effects
     * are commonly applied by the entity that owns the zone, after the player
     * tick has already finished, so a player-tick-only guard is not sufficient.
     */
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            for (Player player : event.level.players()) {
                if (StarCoronaCurios.hasSoulHeart(player)) {
                    SOUL_FLIGHT_TICK_STATE.put(player.getUUID(), player.getAbilities().flying);
                }
            }
            return;
        }
        if (event.phase != TickEvent.Phase.END) return;

        for (Player player : event.level.players()) {
            // No-flight zones usually write flying=false directly from the
            // ticking entity after the player's own tick has completed. Do
            // not require the player to have been flying at the start of this
            // tick: the first write is exactly when that state is lost.
            enforceSoulHeartFlight(player);
            SOUL_FLIGHT_TICK_STATE.remove(player.getUUID());
        }
        SOUL_FLIGHT_TICK_STATE.clear();
    }

    /**
     * Keeps the Soul Heart's flight permission and active flight state after
     * other entities or effects write directly to PlayerAbilities. This is
     * deliberately independent of the source of the no-flight write.
     */
    public static void enforceSoulHeartFlight(Player player) {
        if (player == null) return;
        if (!StarCoronaCurios.hasSoulHeart(player)) {
            SOUL_FLIGHT_REQUESTED.remove(player.getUUID());
            return;
        }
        boolean requested = SOUL_FLIGHT_REQUESTED.getOrDefault(player.getUUID(), true);
        boolean abilitiesChanged = !player.getAbilities().mayfly
                || player.getAbilities().flying != requested;
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = requested;
        if (abilitiesChanged) player.onUpdateAbilities();
    }

    /** Records a client-side double-jump choice or its serverbound sync. */
    public static void recordSoulHeartFlightRequest(Player player, boolean flying) {
        if (player == null || !StarCoronaCurios.hasSoulHeart(player)) return;
        SOUL_FLIGHT_REQUESTED.put(player.getUUID(), flying);
    }

    private static void applyAttributes(Player player) {
        AttributeInstance armor = player.getAttribute(Attributes.ARMOR);
        if (armor != null) {
            AttributeModifier old = armor.getModifier(ARMOR_UUID);
            if (old != null) armor.removeModifier(old);
            if (has(player, 1)) {
                double wornArmor = player.getArmorValue();
                armor.addTransientModifier(new AttributeModifier(ARMOR_UUID, "star_corona_armor",
                        wornArmor * 0.2D + 4.0D, AttributeModifier.Operation.ADDITION));
            }
        }
        AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeed != null) {
            AttributeModifier old = attackSpeed.getModifier(ATTACK_SPEED_UUID);
            boolean title10 = has(player, 10);
            if (!title10) {
                if (old != null) attackSpeed.removeModifier(old);
            } else if (old == null
                    || old.getOperation() != AttributeModifier.Operation.MULTIPLY_TOTAL
                    || old.getAmount() != 0.2D) {
                if (old != null) attackSpeed.removeModifier(old);
                attackSpeed.addTransientModifier(new AttributeModifier(ATTACK_SPEED_UUID,
                        "star_corona_attack_speed", 0.2D, AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        }
    }

    private static boolean hasAscension(Player player) {
        ItemStack equipped = StarCoronaCurios.findEquipped(player);
        return !equipped.isEmpty() && StarCoronaInventory.of(equipped).hasAllTitles();
    }

    private static void updateAscensionBenefits(Player player) {
        if (!hasAscension(player)) return;
        for (MobEffectInstance effect : new java.util.ArrayList<>(player.getActiveEffects())) {
            if (effect.getEffect().getCategory() == MobEffectCategory.HARMFUL) {
                player.removeEffect(effect.getEffect());
            }
        }
        if (player.tickCount % 40 == 0) {
            // FoodData.eat(2, 0.25) adds 2 hunger and exactly 1 saturation.
            player.getFoodData().eat(2, 0.25F);
        }
    }

    private static void updateSoulHeartDefenseState(Player player) {
        if (StarCoronaCurios.hasSoulHeart(player)) return;
        UUID id = player.getUUID();
        SOUL_HEART_HURT_DEFEND_UNTIL.remove(id);
        SOUL_HEART_HURT_DEFEND_START.remove(id);
        SOUL_HEART_DAMAGE_TICK.remove(id);
        SOUL_HEART_DAMAGE_USED.remove(id);
    }

    private static void updateSoulFlight(Player player) {
        boolean enabled = false;
        ItemStack equipped = StarCoronaCurios.findEquipped(player);
        if (!equipped.isEmpty()) enabled = StarCoronaInventory.of(equipped).hasSoulHeart();
        if (enabled) {
            UUID playerId = player.getUUID();
            boolean firstEnable = !SOUL_FLIGHT_SAVED.containsKey(playerId);
            if (firstEnable) {
                SOUL_FLIGHT_SAVED.put(playerId,
                        new boolean[]{player.getAbilities().mayfly, player.getAbilities().flying});
                SOUL_FLIGHT_REQUESTED.put(playerId, true);
            }
            boolean abilitiesChanged = false;
            if (firstEnable && !player.getAbilities().flying) {
                // Start in flight when the Soul Heart is equipped; later toggles are controlled by double-jump.
                player.getAbilities().flying = true;
                abilitiesChanged = true;
            }
            if (!player.getAbilities().mayfly) abilitiesChanged = true;
            player.getAbilities().mayfly = true;
            if (abilitiesChanged) player.onUpdateAbilities();
        } else {
            boolean[] saved = SOUL_FLIGHT_SAVED.remove(player.getUUID());
            if (saved != null) {
                player.getAbilities().mayfly = saved[0];
                player.getAbilities().flying = saved[1];
                player.onUpdateAbilities();
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(LivingAttackEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;
        if (event.getSource().getEntity() instanceof Player attacker && target != attacker) {
            StarCoronaServantAllay.notifyOwnerAttack(attacker, target);
        }
        if (!(target instanceof Player player)) return;
        if (isSoulHeartHurtDefendBlocking(player)) {
            event.setCanceled(true);
            return;
        }
        if (!has(player, 2)) return;
        if (event.getSource().getDirectEntity() instanceof Projectile && player.getRandom().nextFloat() < 0.30F) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;
        DamageSource source = event.getSource();
        Entity attacker = source.getEntity();
        Entity direct = source.getDirectEntity();
        long now = target.level().getGameTime();

        if (target instanceof Player player && isSoulHeartHurtDefendBlocking(player)) {
            event.setCanceled(true);
            return;
        }

        Player playerTarget = target instanceof Player player ? player : null;
        boolean soulHeartTarget = playerTarget != null && StarCoronaCurios.hasSoulHeart(playerTarget);

        // Offensive title modifiers belong to the raw hit. For a Soul Heart
        // target they must be included before the 20-point cap and mitigation.
        if (soulHeartTarget && attacker instanceof LivingEntity livingAttacker
                && !Boolean.TRUE.equals(BONUS_DAMAGE)
                && !StarCoronaCurios.findEquipped(livingAttacker).isEmpty()) {
            applyOnHit(livingAttacker, target, source, direct, event);
        }

        if (soulHeartTarget && WitherTitleHelper.isMarkedBySingularity(target)) {
            event.setAmount(event.getAmount() * 1.15F);
        }

        // The cap is intentionally before all defensive multipliers. This also
        // limits generic-kill/Float.MAX_VALUE damage to a 20-point raw hit.
        if (soulHeartTarget) {
            event.setAmount(capSoulHeartDamageBeforeReduction(playerTarget, event.getAmount()));
        }

        if (target instanceof Player player) {
            event.setAmount(event.getAmount() * StarCoronaCurios.getEquippedTitleDamageFactor(player));
        }

        if (target instanceof Player player && hasAscension(player)
                && (source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypes.SONIC_BOOM))) {
            event.setAmount(event.getAmount() * ASCENSION_DAMAGE_FACTOR);
        }

        if (!soulHeartTarget && WitherTitleHelper.isMarkedBySingularity(target)) {
            event.setAmount(event.getAmount() * 1.15F);
        }

        if (target instanceof Player player && has(player, 1)) event.setAmount(event.getAmount() * 0.80F);

        if (target instanceof Player player && has(player, 11)
                && player.getHealth() <= 0
                && cooled(REVIVAL_SHIELD_UNTIL, player.getUUID(), now, 1200)) {
            event.setCanceled(true);
            player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + player.getMaxHealth() * 0.5F));
            player.setAbsorptionAmount(player.getMaxHealth());
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 300, 4));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 300, 1));
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
            return;
        }

        if (target instanceof Player player && has(player, 2)
                && event.getAmount() >= player.getHealth()
                && cooled(RESPAWN_TELEPORT_UNTIL, player.getUUID(), now, 1200)) {
            event.setCanceled(true);
            teleportToRespawn(player);
            return;
        }

        if (!soulHeartTarget && attacker instanceof LivingEntity livingAttacker && !Boolean.TRUE.equals(BONUS_DAMAGE)
                && !StarCoronaCurios.findEquipped(livingAttacker).isEmpty()) {
            applyOnHit(livingAttacker, target, source, direct, event);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        // Negative healing is damage and must not receive the positive-healing bonus.
        if (event.getAmount() > 0.0F && hasAscension(player)) {
            event.setAmount(event.getAmount() * 1.30F);
        } else if (event.getAmount() < 0.0F) {
            if (isSoulHeartHurtDefendBlocking(player)) {
                event.setAmount(0.0F);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMobEffectApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof Player player) || !hasAscension(player)) return;
        if (event.getEffectInstance().getEffect().getCategory() == MobEffectCategory.HARMFUL) {
            event.setResult(Event.Result.DENY);
        }
    }

    private static void applyOnHit(LivingEntity attacker, LivingEntity target, DamageSource source,
                                   Entity direct, LivingHurtEvent event) {
        long now = attacker.level().getGameTime();
        boolean ranged = direct instanceof Projectile;

        if (has(attacker, 3)) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ranged ? 40 : 60,
                    ranged ? 1 : 0), attacker);
            if (attacker.level().getRandom().nextFloat() < 0.20F) {
                dealBonusDamage(target, target.getMaxHealth() * 0.05F,
                        target.damageSources().freeze(), attacker);
            }
        }

        if (has(attacker, 4) && cooled(HIT_HEAL_UNTIL, attacker.getUUID(), now, 10)) {
            float amount = 1.0F + attacker.getMaxHealth() * 0.005F;
            if (attacker.getHealth() < attacker.getMaxHealth() * 0.5F) amount *= 2.0F;
            restoreCurrentHealth(attacker, amount);
        }

        if (has(attacker, 5)) {
            float chance = ranged ? 0.25F : 0.20F;
            if (attacker.level().getRandom().nextFloat() < chance
                    && attacker.level() instanceof ServerLevel level) {
                summonLightning(level, target);
                float lightningDamage = ranged ? event.getAmount() : 5.0F + target.getHealth() * 0.10F;
                dealBonusDamage(target, lightningDamage,
                        target.damageSources().lightningBolt(), attacker);
            }
        }

        if (has(attacker, 8) && ranged && direct.getPersistentData().getBoolean(OVERCHARGE_PROJECTILE)) {
            direct.getPersistentData().putBoolean(OVERCHARGE_PROJECTILE, false);
            event.setAmount(event.getAmount() * 6.0F);
        }

        if (has(attacker, 9) && ranged && attacker.level() instanceof ServerLevel level
                && attacker.level().getRandom().nextFloat() < 0.10F) {
            spawnPlayerSingularity(attacker, target.position(), level, now);
        }

        if (has(attacker, 12) && ranged && attacker.level() instanceof ServerLevel level
                && attacker.level().getRandom().nextFloat() < 0.10F) {
            spawnBallLightning(attacker, target.position(), level, now);
        }

        if (has(attacker, 13)) {
            boolean wasOnFire = target.isOnFire();
            target.setSecondsOnFire(3);
            if (wasOnFire) event.setAmount(event.getAmount() * 1.50F);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity killer)
                || killer == event.getEntity() || !has(killer, 4)) return;
        restoreCurrentHealth(killer, killer.getMaxHealth() * 0.05F + 5.0F);
    }

    /**
     * Restores current health through the normal LivingEntity.heal() path.
     * This keeps the title consistent with other healing sources: Forge's
     * LivingHealEvent and any healing-suppression mixins get one opportunity
     * to adjust or cancel the recovery, and vanilla clamps it to max health.
     */
    private static void restoreCurrentHealth(LivingEntity entity, float amount) {
        if (entity == null || !entity.isAlive() || amount <= 0.0F
                || Float.isNaN(amount) || Float.isInfinite(amount)) return;
        entity.heal(amount);
    }

    private static void dealBonusDamage(LivingEntity target, float amount, DamageSource source,
                                        LivingEntity attacker) {
        if (amount <= 0.0F || !target.isAlive()) return;

        // This helper is called from LivingHurtEvent, after the outer hurt()
        // call has already installed vanilla's 20-tick damage cooldown. Run
        // the bonus hit through the normal damage pipeline. Temporarily clear
        // vanilla and known mod-specific timers so a mod boss cannot discard
        // the nested hit. Restore the outer state afterwards.
        SpecialInvulnerabilityState state = new SpecialInvulnerabilityState(target);
        BONUS_DAMAGE.set(true);
        try {
            boolean accepted = target.hurt(source, amount);

            // Apostle, external mod and similar bosses may reject the LIGHTNING_BOLT
            // damage type outright. Retry with an attacker-owned source so the
            // title's bonus damage is not silently lost to that hard-coded
            // source immunity. Other damage types keep their original source.
            if (!accepted && source.is(DamageTypes.LIGHTNING_BOLT)
                    && attacker != null && target.isAlive()) {
                state.restore();
                SpecialInvulnerabilityState fallbackState = new SpecialInvulnerabilityState(target);
                try {
                    DamageSource fallback = attacker instanceof Player player
                            ? target.damageSources().playerAttack(player)
                            : target.damageSources().mobAttack(attacker);
                    target.hurt(fallback, amount);
                } finally {
                    fallbackState.restore();
                }
            }
        } finally {
            state.restore();
            BONUS_DAMAGE.remove();
        }
    }

    private static void summonLightning(ServerLevel level, LivingEntity target) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) return;
        bolt.moveTo(target.getX(), target.getY(), target.getZ());
        bolt.setVisualOnly(true);
        for (Creeper creeper : level.getEntitiesOfClass(Creeper.class,
                target.getBoundingBox().inflate(2.0D), Entity::isAlive)) {
            creeper.thunderHit(level, bolt);
        }
        level.addFreshEntity(bolt);
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getProjectile().level().isClientSide) return;
        if (!(event.getProjectile().getOwner() instanceof LivingEntity owner) || !has(owner, 12)) return;
        if (event.getRayTraceResult().getType() != HitResult.Type.BLOCK) return;
        if (owner.level() instanceof ServerLevel level && owner.level().getRandom().nextFloat() < 0.10F) {
            spawnBallLightning(owner, event.getRayTraceResult().getLocation(), level,
                    owner.level().getGameTime());
        }
    }

    private static void spawnBallLightning(LivingEntity owner, Vec3 pos, ServerLevel level, long now) {
        if (!cooled(BALL_UNTIL, owner.getUUID(), now, 20)) return;
        AABB box = owner.getBoundingBox().inflate(32.0D);
        if (!level.getEntitiesOfClass(BallLightningEntity.class, box,
                ball -> owner.getUUID().equals(ball.getOwnerUUID())).isEmpty()) return;
        level.addFreshEntity(new BallLightningEntity(level, pos.x, pos.y, pos.z,
                owner.getUUID(), 2.0F, 80, 20));
    }

    private static void spawnPlayerSingularity(LivingEntity owner, Vec3 pos, ServerLevel level, long now) {
        if (!cooled(SINGULARITY_UNTIL, owner.getUUID(), now, 40)) return;
        AABB box = owner.getBoundingBox().inflate(32.0D);
        if (!level.getEntitiesOfClass(GravitySingularityEntity.class, box,
                e -> owner.getUUID().equals(e.getOwnerUUID())
                        && e.getPersistentData().getBoolean(PLAYER_SINGULARITY)).isEmpty()) return;
        GravitySingularityEntity singularity = new GravitySingularityEntity(level, pos.x, pos.y, pos.z, owner.getUUID());
        singularity.setPullRadius(3.0D);
        singularity.setLifetime(60);
        singularity.setBlueMode(true);
        singularity.setSkipPlayers(true);
        singularity.getPersistentData().putBoolean(PLAYER_SINGULARITY, true);
        level.addFreshEntity(singularity);
    }

    @SubscribeEvent
    public static void onUseStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getItem().getItem() instanceof BowItem && has(player, 8)) {
            BOW_USE_TICKS.put(player.getUUID(), 0);
            LONG_CHARGE_READY.put(player.getUUID(), false);
        }
        if (event.getItem().getItem() instanceof BowItem && has(player, 10)) {
            BOW_ACCELERATION_TICKS.computeIfAbsent(player.level(), level -> new HashMap<>())
                    .put(player.getUUID(), 0);
        }
        if (event.getItem().getItem() instanceof CrossbowItem && has(player, 10)) {
            CROSSBOW_USE_TICKS.computeIfAbsent(player.level(), level -> new HashMap<>())
                    .put(player.getUUID(), 0);
        }
    }

    @SubscribeEvent
    public static void onUseTick(LivingEntityUseItemEvent.Tick event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack stack = event.getItem();
        int used = stack.getUseDuration() - event.getDuration();
        if (stack.getItem() instanceof BowItem && has(player, 8)) {
            int bowTicks = BOW_USE_TICKS.merge(player.getUUID(), 1, Integer::sum);
            if (bowTicks >= 100 && !LONG_CHARGE_READY.getOrDefault(player.getUUID(), false)) {
                LONG_CHARGE_READY.put(player.getUUID(), true);
                if (!player.level().isClientSide) {
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.8F, 1.0F);
                }
            }
        }
        // 原版每 tick 会自动减少 1 tick；每两个使用 tick 额外减少 1 tick，
        // 平均蓄力速度为原来的 1.5 倍。只做单调递减，不重写总 duration，避免蓄力状态重置。
        if ((stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem)
                && has(player, 10)) {
            Map<UUID, Integer> progress = BOW_ACCELERATION_TICKS.computeIfAbsent(
                    player.level(), level -> new HashMap<>());
            int accelerationTick = progress.merge(player.getUUID(), 1, Integer::sum);
            if (accelerationTick % 2 == 0) {
                event.setDuration(Math.max(0, event.getDuration() - 1));
            }
        }
        if (stack.getItem() instanceof CrossbowItem) {
            CROSSBOW_USE_TICKS.computeIfAbsent(player.level(), level -> new HashMap<>())
                    .put(player.getUUID(), used);
        }
    }

    @SubscribeEvent
    public static void onArrowLoose(ArrowLooseEvent event) {
        Player player = event.getEntity();
        int charge = event.getCharge();
        if (has(player, 7) && charge >= 20) increment(FOCUS_PENDING, player.getUUID());
        if (has(player, 8) && (charge >= 100
                || BOW_USE_TICKS.getOrDefault(player.getUUID(), 0) >= 100)) {
            OVERCHARGE_PENDING.put(player.getUUID(), true);
        }
        BOW_USE_TICKS.remove(player.getUUID());
        LONG_CHARGE_READY.remove(player.getUUID());
        Map<UUID, Integer> acceleration = BOW_ACCELERATION_TICKS.get(player.level());
        if (acceleration != null) acceleration.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void onUseStop(LivingEntityUseItemEvent.Stop event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack stack = event.getItem();
        int used = stack.getUseDuration() - event.getDuration();
        if (stack.getItem() instanceof BowItem) {
            BOW_USE_TICKS.remove(player.getUUID());
            LONG_CHARGE_READY.remove(player.getUUID());
            Map<UUID, Integer> acceleration = BOW_ACCELERATION_TICKS.get(player.level());
            if (acceleration != null) acceleration.remove(player.getUUID());
        }
        if (stack.getItem() instanceof CrossbowItem) {
            Map<UUID, Integer> progress = CROSSBOW_USE_TICKS.get(player.level());
            if (progress != null) progress.remove(player.getUUID());
            if (has(player, 7) && used >= CrossbowItem.getChargeDuration(stack)) {
                increment(FOCUS_PENDING, player.getUUID());
            }
        }
    }

    @SubscribeEvent
    public static void onProjectileJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof Projectile projectile)) return;
        if (!(projectile.getOwner() instanceof LivingEntity owner)) return;
        UUID id = owner.getUUID();
        if (has(owner, 7) && takePending(FOCUS_PENDING, id)) {
            projectile.setDeltaMovement(projectile.getDeltaMovement().scale(1.5D));
            projectile.getPersistentData().putBoolean(FOCUS_PROJECTILE, true);
        }
        if (has(owner, 8) && OVERCHARGE_PENDING.getOrDefault(id, false)) {
            OVERCHARGE_PENDING.put(id, false);
            if (projectile instanceof AbstractArrow) {
                projectile.getPersistentData().putBoolean(OVERCHARGE_PROJECTILE, true);
            }
        }
    }

    private static void increment(Map<UUID, Integer> map, UUID id) {
        map.put(id, map.getOrDefault(id, 0) + 1);
    }

    private static boolean takePending(Map<UUID, Integer> map, UUID id) {
        int count = map.getOrDefault(id, 0);
        if (count <= 0) return false;
        if (count == 1) map.remove(id);
        else map.put(id, count - 1);
        return true;
    }

    private static void teleportToRespawn(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || serverPlayer.getServer() == null) return;
        BlockPos respawn = serverPlayer.getRespawnPosition();
        net.minecraft.resources.ResourceKey<Level> dimension = serverPlayer.getRespawnDimension();
        ServerLevel destination = serverPlayer.getServer().getLevel(dimension);
        if (destination == null) destination = serverPlayer.serverLevel();
        if (respawn == null) respawn = destination.getSharedSpawnPos();
        BlockPos safe = destination.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, respawn);
        Vec3 from = serverPlayer.position();
        serverPlayer.teleportTo(destination, safe.getX() + 0.5D, safe.getY(), safe.getZ() + 0.5D,
                serverPlayer.getYRot(), serverPlayer.getXRot());
        destination.sendParticles(ParticleTypes.PORTAL, from.x, from.y + 1.0D, from.z,
                24, 0.3D, 0.6D, 0.3D, 0.2D);
        destination.sendParticles(ParticleTypes.PORTAL, safe.getX() + 0.5D, safe.getY() + 1.0D,
                safe.getZ() + 0.5D, 24, 0.3D, 0.6D, 0.3D, 0.2D);
        destination.playSound(null, safe, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private static void summonAllays(Player owner, ServerLevel level) {
        for (int i = 0; i < 3; i++) {
            StarCoronaServantAllay allay = new StarCoronaServantAllay(
                    wither_title.WitherTitleEntities.STAR_CORONA_SERVANT_ALLAY.get(), level);
            allay.moveTo(owner.getX() + (i - 1) * 0.8D, owner.getY() + 1.0D, owner.getZ() + 0.5D,
                    owner.getYRot(), 0.0F);
            allay.setPersistenceRequired();
            allay.getPersistentData().putUUID(SERVANT_OWNER, owner.getUUID());
            allay.getPersistentData().putLong(SERVANT_EXPIRE, level.getGameTime() + 900L);
            maintainAllayStats(allay);
            allay.setHealth(allay.getMaxHealth());
            level.addFreshEntity(allay);
            StarCoronaBeamEntity beam = new StarCoronaBeamEntity(level, allay.getUUID(), owner.getUUID());
            beam.moveTo(allay.getX(), allay.getY(), allay.getZ(), 0.0F, 0.0F);
            level.addFreshEntity(beam);
        }
    }

    private static void maintainAllays(Player owner, ServerLevel level, long now) {
        AABB box = owner.getBoundingBox().inflate(128.0D);
        for (Allay allay : level.getEntitiesOfClass(Allay.class, box,
                a -> a.getPersistentData().hasUUID(SERVANT_OWNER)
                        && owner.getUUID().equals(a.getPersistentData().getUUID(SERVANT_OWNER)))) {
            long expire = allay.getPersistentData().getLong(SERVANT_EXPIRE);
            if (expire > 0L && now >= expire) {
                allay.discard();
                continue;
            }
            if (allay.distanceToSqr(owner) > 144.0D) {
                allay.teleportTo(owner.getX() + 0.5D, owner.getY() + 1.0D, owner.getZ() + 0.5D);
            }
            maintainAllayStats(allay);
            if (owner.tickCount % 20 == 0) owner.heal(2.0F + owner.getMaxHealth() * 0.005F);
        }
    }

    private static void maintainAllayStats(Allay allay) {
        // 自定义悦灵仆从在 createAttributes() 中已建立 MAX_HEALTH=30、ARMOR=10，
        // 无需再加属性修饰符；仅旧存档残留的原版悦灵沿用 +10 生命/+10 护甲的运行时加成。
        if (!(allay instanceof StarCoronaServantAllay)) {
            AttributeInstance maxHealth = allay.getAttribute(Attributes.MAX_HEALTH);
            if (maxHealth != null) {
                AttributeModifier modifier = maxHealth.getModifier(ALLAY_MAX_HEALTH_UUID);
                if (modifier == null || modifier.getOperation() != AttributeModifier.Operation.ADDITION
                        || modifier.getAmount() != 10.0D) {
                    if (modifier != null) maxHealth.removeModifier(modifier);
                    maxHealth.addPermanentModifier(new AttributeModifier(ALLAY_MAX_HEALTH_UUID,
                            "star_allay_max_health", 10.0D, AttributeModifier.Operation.ADDITION));
                }
            }

            AttributeInstance armor = allay.getAttribute(Attributes.ARMOR);
            if (armor != null) {
                AttributeModifier modifier = armor.getModifier(ALLAY_ARMOR_UUID);
                if (modifier == null || modifier.getOperation() != AttributeModifier.Operation.ADDITION
                        || modifier.getAmount() != 10.0D) {
                    if (modifier != null) armor.removeModifier(modifier);
                    armor.addPermanentModifier(new AttributeModifier(ALLAY_ARMOR_UUID,
                            "star_allay_armor", 10.0D, AttributeModifier.Operation.ADDITION));
                }
            }
        }

        ensureAllayEffect(allay, MobEffects.DAMAGE_RESISTANCE);
        ensureAllayEffect(allay, MobEffects.REGENERATION);
        if (allay.getHealth() > allay.getMaxHealth()) allay.setHealth(allay.getMaxHealth());
    }

    private static void ensureAllayEffect(Allay allay, net.minecraft.world.effect.MobEffect effect) {
        MobEffectInstance current = allay.getEffect(effect);
        if (current == null || current.getAmplifier() != 1
                || current.getDuration() != MobEffectInstance.INFINITE_DURATION) {
            allay.addEffect(new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION,
                    1, true, false, true));
        }
    }

    private static void discardAllays(Player owner, ServerLevel level) {
        AABB box = owner.getBoundingBox().inflate(128.0D);
        for (Allay allay : level.getEntitiesOfClass(Allay.class, box,
                a -> a.getPersistentData().hasUUID(SERVANT_OWNER)
                        && owner.getUUID().equals(a.getPersistentData().getUUID(SERVANT_OWNER)))) {
            allay.discard();
        }
    }

    private static void cleanupOrphanBeams(Player owner, ServerLevel level, long now) {
        // 清理旧版本生成的末影水晶；新版本的连线只由 StarCoronaBeamEntity 渲染。
        AABB box = owner.getBoundingBox().inflate(128.0D);
        for (EndCrystal crystal : level.getEntitiesOfClass(EndCrystal.class, box,
                e -> e.getPersistentData().hasUUID(SERVANT_OWNER)
                        && owner.getUUID().equals(e.getPersistentData().getUUID(SERVANT_OWNER)))) {
            crystal.discard();
        }
        for (StarCoronaBeamEntity beam : level.getEntitiesOfClass(StarCoronaBeamEntity.class, box)) {
            if (beam.isExpired()) beam.discard();
        }
    }

    private static void cleanupExpiredState(ServerLevel level, long now) {
        removeExpired(RESPAWN_TELEPORT_UNTIL, now);
        removeExpired(HIT_HEAL_UNTIL, now);
        removeExpired(SERVANT_SUMMON_UNTIL, now);
        removeExpired(SINGULARITY_UNTIL, now);
        removeExpired(BALL_UNTIL, now);
        removeExpired(REVIVAL_SHIELD_UNTIL, now);
        removeExpired(SOUL_HEART_HURT_DEFEND_UNTIL, now);
        SOUL_HEART_HURT_DEFEND_START.keySet().removeIf(id -> !SOUL_HEART_HURT_DEFEND_UNTIL.containsKey(id));
        SOUL_HEART_DAMAGE_TICK.keySet().removeIf(id -> !SOUL_HEART_HURT_DEFEND_UNTIL.containsKey(id));
        SOUL_HEART_DAMAGE_USED.keySet().removeIf(id -> !SOUL_HEART_HURT_DEFEND_UNTIL.containsKey(id));
        if (level.getServer() == null) return;
        Set<UUID> active = new HashSet<>();
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) active.add(player.getUUID());
        pruneInactive(RESPAWN_TELEPORT_UNTIL, active);
        pruneInactive(HIT_HEAL_UNTIL, active);
        pruneInactive(SERVANT_SUMMON_UNTIL, active);
        pruneInactive(SINGULARITY_UNTIL, active);
        pruneInactive(BALL_UNTIL, active);
        pruneInactive(REVIVAL_SHIELD_UNTIL, active);
        pruneInactive(SOUL_HEART_HURT_DEFEND_UNTIL, active);
        pruneInactive(SOUL_HEART_HURT_DEFEND_START, active);
        pruneInactive(SOUL_HEART_DAMAGE_TICK, active);
        pruneInactive(SOUL_HEART_DAMAGE_USED, active);
        pruneInactive(FOCUS_PENDING, active);
        pruneInactive(OVERCHARGE_PENDING, active);
        pruneInactive(BOW_USE_TICKS, active);
        pruneInactive(LONG_CHARGE_READY, active);
        pruneInactive(SOUL_FLIGHT_SAVED, active);
        pruneInactive(SOUL_FLIGHT_REQUESTED, active);
        pruneInactive(SOUL_FLIGHT_TICK_STATE, active);
        Map<UUID, Integer> crossbow = CROSSBOW_USE_TICKS.get(level);
        if (crossbow != null) pruneInactive(crossbow, active);
        Map<UUID, Integer> bowAcceleration = BOW_ACCELERATION_TICKS.get(level);
        if (bowAcceleration != null) pruneInactive(bowAcceleration, active);
    }

    private static void removeExpired(Map<UUID, Long> map, long now) {
        map.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    private static void pruneInactive(Map<UUID, ?> map, Set<UUID> active) {
        map.keySet().removeIf(id -> !active.contains(id));
    }
}
