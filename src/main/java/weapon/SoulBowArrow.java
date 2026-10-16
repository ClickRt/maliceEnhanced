package weapon;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

public class SoulBowArrow extends Arrow {
    private static final int SPECTRAL_GLOW_TICKS = 200;
    private static final float BONUS_MAX_HEALTH_DAMAGE = 0.10F;
    private static final float MAX_HEALTH_REDUCTION = 0.02F;
    private static final float MIN_MAX_HEALTH = 1.0F;
    private static final UUID MAX_HP_REDUCTION_UUID = UUID.fromString("7e3c9b1a-4d62-4f80-9c2e-8a1b0d5e6f73");
    private static final Map<UUID, Double> MAX_HP_REDUCTIONS = new HashMap<>();

    /** 吞噬者"段→头"反射方法缓存（按段类名），避免每次命中重复查找 */
    private static final Map<String, Method> DEVOURER_RESOLVE_HEAD_CACHE = new HashMap<>();

    private ItemStack pickupOverride = ItemStack.EMPTY;
    private ItemStack sourceBow = ItemStack.EMPTY;
    private final Set<UUID> countedKills = new HashSet<>();
    private boolean spectral;
    private int storedKnockback;
    
    /** 硬锁定追踪：目标网络 id（-1 表示无目标），仅服务端使用，不持久化（箭寿命极短） */
    private int homingTargetId = -1;
    private int homingTicks;
    /** 单次锁定后的最长追踪 tick 数（约 3 秒），超时则放弃追踪直线飞行 */
    private static final int HOMING_MAX_TICKS = 60;
    /** 目标超出此距离则断开锁定 */
    private static final double HOMING_BREAK_RANGE = 140.0D;
    /** 已命中/自行回收：此后允许 DISCARDED/KILLED。飞行中则拦截外部清弹 */
    private boolean spent;
    /** 无弹药射出的临时箭，命中方块后 10 秒清除 */
    private boolean conjured;
    private int conjuredGroundTicks;
    private static final int CONJURED_LIFETIME = 200;

    public SoulBowArrow(EntityType<? extends SoulBowArrow> type, Level level) {
        super(type, level);
    }

    public SoulBowArrow(Level level, LivingEntity shooter) {
        this(WeaponEntities.SOUL_BOW_ARROW.get(), level);
        this.setOwner(shooter);
        this.setPos(shooter.getX(), shooter.getEyeY() - 0.1D, shooter.getZ());
    }

    public static SoulBowArrow wrap(AbstractArrow original) {
        SoulBowArrow copy = original.getOwner() instanceof LivingEntity living
                ? new SoulBowArrow(original.level(), living)
                : new SoulBowArrow(WeaponEntities.SOUL_BOW_ARROW.get(), original.level());
        CompoundTag tag = new CompoundTag();
        original.saveWithoutId(tag);
        copy.load(tag);
        copy.setUUID(UUID.randomUUID());
        copy.setOwner(original.getOwner());
        copy.setPos(original.getX(), original.getY(), original.getZ());
        if (original instanceof SpectralArrow) {
            copy.spectral = true;
            copy.pickupOverride = new ItemStack(Items.SPECTRAL_ARROW);
        }
        copy.setBaseDamage(SoulBowItem.BASE_DAMAGE);
        copy.storedKnockback = tag.getInt("knockback");
        return copy;
    }

    public void setSourceBow(ItemStack bow) {
        this.sourceBow = bow == null ? ItemStack.EMPTY : bow;
    }

    public void markConjured() {
        this.conjured = true;
        this.pickup = Pickup.DISALLOWED;
    }

    public void tryRecordKill(Entity target) {
        if (this.level().isClientSide() || target == null || this.sourceBow.isEmpty()) {
            return;
        }
        if (!(this.sourceBow.getItem() instanceof SoulBowItem)) {
            return;
        }
        if (!this.countedKills.add(target.getUUID())) {
            return;
        }
        SoulBowItem.addKill(this.sourceBow, this.getOwner());
    }

    private void tryRecordKillIfDead(Entity target) {
        if (target instanceof LivingEntity living) {
            if (!living.isAlive() || living.isDeadOrDying() || living.getHealth() <= 0.0F) {
                tryRecordKill(living);
            }
            return;
        }
        if (!target.isAlive()) {
            tryRecordKill(target);
        }
    }

    public boolean blocksExternalClear(RemovalReason reason) {
        if (this.level().isClientSide()) {
            return false;
        }
        if (reason != RemovalReason.DISCARDED && reason != RemovalReason.KILLED) {
            return false;
        }
        if (this.spent || this.inGround) {
            return false;
        }
        return this.getY() >= this.level().getMinBuildHeight() - 64;
    }

    private void markSpent() {
        this.spent = true;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (blocksExternalClear(reason)) {
            return;
        }
        super.remove(reason);
    }

    @Override
    public void setKnockback(int knockback) {
        super.setKnockback(knockback);
        this.storedKnockback = knockback;
    }

    /** 由弓在发射瞬间调用：为这支箭指派一个硬锁定目标 */
    public void setHomingTarget(LivingEntity target) {
        this.homingTargetId = target == null ? -1 : target.getId();
        this.homingTicks = 0;
    }

    /**
     * 服务端每 tick 的硬锁定：把当前速度整体重定向到目标眼位（保留速率，仅改方向）。
     * 只追踪发射瞬间被指派的那个目标；目标死亡/超距/超时则立即放弃追踪、直线飞行，
     * 绝不中途吸附准星以外的活物[过于变态]。
     */
    private void serverHomingTick() {
        if (this.inGround) {
            return;
        }
        LivingEntity target = resolveHomingTarget();
        if (target == null) {
            // 从未指派目标，或目标已死亡/消失：直接直线飞行，不做任何中途吸怪
            this.homingTargetId = -1;
            return;
        }
        if (this.homingTicks++ > HOMING_MAX_TICKS) {
            this.homingTargetId = -1;
            return;
        }
        Vec3 to = target.getEyePosition().subtract(this.position());
        if (to.lengthSqr() > HOMING_BREAK_RANGE * HOMING_BREAK_RANGE) {
            this.homingTargetId = -1;
            return;
        }
        double speed = this.getDeltaMovement().length();
        if (speed < 0.2D) {
            speed = SoulBowItem.FULL_DRAW_VELOCITY;
        }
        Vec3 dir = to.normalize();
        this.setDeltaMovement(dir.x * speed, dir.y * speed, dir.z * speed);
        this.hasImpulse = true;
    }

    private LivingEntity resolveHomingTarget() {
        if (this.homingTargetId < 0) {
            return null;
        }
        Entity entity = this.level().getEntity(this.homingTargetId);
        if (entity instanceof LivingEntity living && living.isAlive() && !living.isRemoved()) {
            return living;
        }
        return null;
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide()) {
            this.serverHomingTick();
        }
        boolean crit = this.isCritArrow();
        if (this.level().isClientSide()) {
            this.setCritArrow(false);
        }
        super.tick();
        if (this.level().isClientSide()) {
            this.setCritArrow(crit);
        }
        if (this.level().isClientSide() && !this.inGround) {
            Vec3 motion = this.getDeltaMovement();
            for (int i = 0; i < 4; ++i) {
                this.level().addAlwaysVisibleParticle(
                        ParticleTypes.SOUL_FIRE_FLAME,
                        true,
                        this.getX() + motion.x * i / 4.0D,
                        this.getY() + motion.y * i / 4.0D,
                        this.getZ() + motion.z * i / 4.0D,
                        0.0D, 0.0D, 0.0D);
            }
        }
        if (!this.level().isClientSide() && this.conjured && this.inGround) {
            if (++this.conjuredGroundTicks >= CONJURED_LIFETIME) {
                this.markSpent();
                this.discard();
            }
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity target = result.getEntity();
        // 末影龙受击走"部位"碰撞盒（Forge 包装为 PartEntity，其 getParent() 才是龙本体 LivingEntity），
        // 直接拿 Part 结算会走到 Part.hurt() 返回 false → 箭被弹落地面。这里还原成龙本体，后续按本体正常吃灵魂伤害。
        if (target instanceof net.minecraftforge.entity.PartEntity<?> part) {
            Entity parent = part.getParent();
            if (parent != null) {
                target = parent;
            }
        }
        // 神明吞噬者(dog mod)：段/尾都是独立 LivingEntity，但真正的血条挂在"头"上，
        // 直写段血量毫无意义。这里把命中的段/尾还原成头本体，后续按头正常吃灵魂伤害。
        // 按要求不做演出无敌判定：过场期间命中段也照常扣头血。
        if (isDevourerPart(target)) {
            Entity devourerHead = resolveDevourerHead(target);
            if (devourerHead != null) {
                target = devourerHead;
            }
        }
        float speed = (float) this.getDeltaMovement().length();
        int damage = Mth.ceil(Mth.clamp(speed * this.getBaseDamage(), 0.0D, 2.147483647E9D));
        if (this.isCritArrow()) {
            damage += this.random.nextInt(damage / 2 + 2);
        }

        Entity owner = this.getOwner();
        DamageSource dummySource = owner == null
                ? this.damageSources().arrow(this, this)
                : this.damageSources().arrow(this, owner);
        if (owner instanceof LivingEntity livingOwner) {
            livingOwner.setLastHurtMob(target);
        }

        boolean enderDragonPart = target.getType() == EntityType.ENDER_DRAGON;
        int remainingFire = target.getRemainingFireTicks();
        if (this.isOnFire() && !enderDragonPart) {
            target.setSecondsOnFire(5);
        }

        boolean applied = applySoulHit(target, damage, dummySource);
        if (applied) {
            tryRecordKillIfDead(target);
            if (enderDragonPart) {
                this.playSound(this.getHitGroundSoundEvent(), 1.0F, 1.2F / (this.random.nextFloat() * 0.2F + 0.9F));
                if (this.getPierceLevel() <= 0) {
                    this.markSpent();
                    this.discard();
                }
                return;
            }
            if (target instanceof LivingEntity living) {
                if (!this.level().isClientSide && this.getPierceLevel() <= 0) {
                    living.setArrowCount(living.getArrowCount() + 1);
                }
                if (this.storedKnockback > 0) {
                    double resist = Math.max(0.0D, 1.0D - living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
                    Vec3 knock = this.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D).normalize()
                            .scale(this.storedKnockback * 0.6D * resist);
                    if (knock.lengthSqr() > 0.0D) {
                        living.push(knock.x, 0.1D, knock.z);
                    }
                }
                if (!this.level().isClientSide && owner instanceof LivingEntity livingOwner) {
                    EnchantmentHelper.doPostHurtEffects(living, livingOwner);
                    EnchantmentHelper.doPostDamageEffects(livingOwner, living);
                }
                this.doPostHurtEffects(living);
                if (living != owner && living instanceof Player && owner instanceof ServerPlayer
                        && !this.isSilent()) {
                    ((ServerPlayer) owner).connection.send(
                            new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                                    net.minecraft.network.protocol.game.ClientboundGameEventPacket.ARROW_HIT_PLAYER, 0.0F));
                }
            }
            this.playSound(this.getHitGroundSoundEvent(), 1.0F, 1.2F / (this.random.nextFloat() * 0.2F + 0.9F));
            if (this.getPierceLevel() <= 0) {
                this.markSpent();
                this.discard();
            }
            return;
        }

        target.setRemainingFireTicks(remainingFire);
        this.setDeltaMovement(this.getDeltaMovement().scale(-0.1D));
        this.setYRot(this.getYRot() + 180.0F);
        this.yRotO += 180.0F;
        if (!this.level().isClientSide && this.getDeltaMovement().lengthSqr() < 1.0E-7D) {
            if (this.pickup == AbstractArrow.Pickup.ALLOWED) {
                this.spawnAtLocation(this.getPickupItem(), 0.1F);
            }
            this.markSpent();
            this.discard();
        }
    }

    private boolean applySoulHit(Entity target, int damage, DamageSource dummySource) {
        if (this.level().isClientSide()) {
            return true;
        }
        if (damage <= 0) {
            return false;
        }
        if (StarFantasyHadesBan.tryBanAndDiscard(target)) {
            return true;
        }
        LivingEntity living = resolveLiving(target);
        if (living == null) {
            return target.hurt(dummySource, damage);
        }
        Entity owner = this.getOwner();
        LivingEntity attacker = owner instanceof LivingEntity livingOwner ? livingOwner : living;
        float maxHealth = living.getMaxHealth();
        float total = damage + maxHealth * BONUS_MAX_HEALTH_DAMAGE;
        boolean applied;
        if (living instanceof WitherBoss wither
                && wither_title.WitherTitleHelper.isUndefinedWither(wither)) {
            applied = SoulSwordEventHandler.applyPureMaliceDamage(living, attacker, total);
            if (applied) {
                reduceMaxHealth(living);
                SoulSwordEventHandler.tryForceKillIfBelowThreshold(living, attacker);
            }
            return applied;
        }
        applied = SoulSwordEventHandler.applySoulDamage(living, attacker, total);
        if (applied) {
            if (living instanceof EnderDragon) {
                // 末影龙在末影水晶存活时会把当前血量每秒回满，直写当前血形同无效；
                // 改把这一发的伤害转成“永久最大生命削减”，回血无法反补，够几发即压穿致死。
                accumulateMaxHealthCut(living, total);
            } else {
                reduceMaxHealth(living);
            }
            SoulSwordEventHandler.tryForceKillIfBelowThreshold(living, attacker);
        }
        return applied;
    }

    /** 实体真正消失时由 SoulSwordEventHandler 的离开事件回调，清掉累计，防止静态表泄漏 */
    public static void clearMaxHpReduction(UUID id) {
        MAX_HP_REDUCTIONS.remove(id);
    }

    /** 常规命中：按 2% 缓慢削减最大生命 */
    private static void reduceMaxHealth(LivingEntity target) {
        double currentMax = target.getMaxHealth();
        double extra = Math.max(0.0D, currentMax - Math.max(MIN_MAX_HEALTH, currentMax * (1.0D - MAX_HEALTH_REDUCTION)));
        if (extra > 0.0D) {
            accumulateMaxHealthCut(target, extra);
        }
    }

    /**
     * 永久削减 {@code cut} 点最大生命（累计，封顶至上限只剩 {@link #MIN_MAX_HEALTH}）。
     * 因为改的是最大生命，末影水晶那种“把当前血回满”无法反补，
     * 专用于会持续回满血的 Boss（末影龙）。削减后当前血超过新上限会被同步拉回。
     */
    private static void accumulateMaxHealthCut(LivingEntity target, double cut) {
        AttributeInstance maxHp = target.getAttribute(Attributes.MAX_HEALTH);
        if (maxHp == null || cut <= 0.0D) {
            return;
        }
        double cap = Math.max(0.0D, maxHp.getBaseValue() - MIN_MAX_HEALTH);
        UUID id = target.getUUID();
        double total = Math.min(cap, MAX_HP_REDUCTIONS.getOrDefault(id, 0.0D) + cut);
        MAX_HP_REDUCTIONS.put(id, total);
        maxHp.removeModifier(MAX_HP_REDUCTION_UUID);
        maxHp.addPermanentModifier(new AttributeModifier(
                MAX_HP_REDUCTION_UUID, "SoulBow maxHP reduction", -total, AttributeModifier.Operation.ADDITION));
        if (target.getHealth() > target.getMaxHealth()) {
            target.setHealth(target.getMaxHealth());
        }
    }

    private static LivingEntity resolveLiving(Entity target) {
        return target instanceof LivingEntity living ? living : null;
    }

    /** 是否是神明吞噬者的部位（头 / 段 / 尾），用类名匹配，不依赖 dog 编译 */
    private static boolean isDevourerPart(Entity entity) {
        for (Class<?> c = entity.getClass(); c != null && c != Entity.class; c = c.getSuperclass()) {
            String name = c.getSimpleName();
            if (name.equals("DevourerSegmentEntity") || name.equals("DevourerHeadEntity")) {
                return true;
            }
        }
        return false;
    }

    /** 命中头返回自身；命中段/尾反射调用其 resolveHead() 拿到头本体；解析失败返回 null */
    private static Entity resolveDevourerHead(Entity part) {
        for (Class<?> c = part.getClass(); c != null && c != Entity.class; c = c.getSuperclass()) {
            if (c.getSimpleName().equals("DevourerHeadEntity")) {
                return part;
            }
        }
        try {
            Method m = findResolveHead(part.getClass());
            if (m != null) {
                Object head = m.invoke(part);
                return head instanceof Entity e ? e : null;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Method findResolveHead(Class<?> start) {
        String key = start.getName();
        Method cached = DEVOURER_RESOLVE_HEAD_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        for (Class<?> c = start; c != null && c != Entity.class; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod("resolveHead");
                m.setAccessible(true);
                DEVOURER_RESOLVE_HEAD_CACHE.put(key, m);
                return m;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    @Override
    protected void doPostHurtEffects(LivingEntity target) {
        super.doPostHurtEffects(target);
        if (this.spectral) {
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, SPECTRAL_GLOW_TICKS, 0));
        }
    }

    @Override
    protected ItemStack getPickupItem() {
        return this.pickupOverride.isEmpty() ? super.getPickupItem() : this.pickupOverride.copy();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("SoulBowSpectral", this.spectral);
        tag.putInt("SoulBowKnockback", this.storedKnockback);
        if (!this.pickupOverride.isEmpty()) {
            tag.put("SoulBowPickup", this.pickupOverride.save(new CompoundTag()));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.spectral = tag.getBoolean("SoulBowSpectral");
        this.storedKnockback = tag.contains("SoulBowKnockback")
                ? tag.getInt("SoulBowKnockback")
                : tag.getInt("knockback");
        if (tag.contains("SoulBowPickup")) {
            this.pickupOverride = ItemStack.of(tag.getCompound("SoulBowPickup"));
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
