package com.enchantmentSkill.mixin;

import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.entity.spells.black_hole.BlackHole;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weapon.IronSpellsCompat;
import weapon.BlueHoleHealSuppression;
import weapon.SoulSwordEventHandler;
import weapon.SoulSwordBlackHoleAccessor;
import wither_title.WitherTitleEventHandler;
import wither_title.WitherTitleHelper;

@Mixin(BlackHole.class)
public abstract class BlackHoleMixin implements SoulSwordBlackHoleAccessor {
    private static final Logger LOGGER = LoggerFactory.getLogger("SoulSwordBlackHole");

    @Shadow(remap = false)
    private float damage;

    private static final int NO_COLOR = 0;
    private static final int SOUL_SWORD_BLUE = 0x0412FC;

    /** 锚定位置（非 null 时每 tick 强制回位，防止黑洞上飘） */
    @Unique
    private net.minecraft.world.phys.Vec3 witherTitle$anchor = null;

    @Override
    public void witherTitle$setAnchor(net.minecraft.world.phys.Vec3 pos) {
        this.witherTitle$anchor = pos;
    }

    /**
     * 锚定黑洞：每 tick 开头强制回到锚点并清零速度
     * method 双名兼容：开发环境 "tick"，生产环境 BlackHole 重写 vanilla 方法后为 "m_8119_"
     * （不能 remap=false：mod 类中重写 vanilla 方法的名字需要 refmap 映射）
     */
    @Inject(method = {"tick", "m_8119_"}, at = @At("HEAD"), require = 0)
    private void witherTitle$anchorTick(CallbackInfo ci) {
        if (witherTitle$anchor != null) {
            Entity self = (Entity) (Object) this;
            self.setPos(witherTitle$anchor.x, witherTitle$anchor.y, witherTitle$anchor.z);
            self.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        }
    }

    @Unique
    private static final EntityDataAccessor<Integer> SOUL_SWORD_COLOR =
            SynchedEntityData.defineId(BlackHole.class, EntityDataSerializers.INT);

    @Unique
    private int soulSword$colorLocal = NO_COLOR;

    /** 蓝色黑洞专用的最大生命削减（独立 UUID，避免与无垠长魂之剑近战 -10 的修饰器互相覆盖） */
    @Unique
    private static final UUID SHATTER$MAX_HP_REDUCTION_UUID =
            UUID.fromString("d1e2f3a4-b5c6-4890-defa-123456789abc");

    @Inject(method = {"defineSynchedData", "m_8097_"}, at = @At("RETURN"), require = 0)
    private void soulSword$defineColor(CallbackInfo ci) {
        try {
            SynchedEntityData data = ((Entity) (Object) this).getEntityData();
            if (!data.hasItem(SOUL_SWORD_COLOR)) {
                data.define(SOUL_SWORD_COLOR, this.soulSword$colorLocal);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public int soulSword$getColor() {
        try {
            return ((Entity) (Object) this).getEntityData().get(SOUL_SWORD_COLOR);
        } catch (Throwable t) {
            return this.soulSword$colorLocal;
        }
    }

    @Override
    public void soulSword$setColor(int color) {
        this.soulSword$colorLocal = color;
        try {
            ((Entity) (Object) this).getEntityData().set(SOUL_SWORD_COLOR, color);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public float soulSword$getDamage() {
        return this.damage;
    }

    /**
     * 蓝色黑洞半径调整（原 BlackHoleSpellMixin 的逻辑，迁移到这里）。
     * 不能再用 mixin 打 BlackHoleSpell / AbstractSpell——那会在 mixin prepare 阶段
     * 提前加载 AbstractSpell，导致梦幻终焉的 AbstractSpellMixin 崩溃。
     * 无垠长魂之剑以 6 级施放黑洞，这里在 setRadius 时按同一公式收小半径：
     * radius = (2*6+4) + 3*(floor(法术强度/2)-3)
     */
    @ModifyVariable(method = "setRadius", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private float shatter$blueHoleRadius(float pRadius) {
        if (soulSword$getColor() == NO_COLOR) return pRadius;
        Entity owner = ((Projectile) (Object) this).getOwner();
        if (!(owner instanceof Player player)) return pRadius;
        try {
            double power = player.getAttributeValue(AttributeRegistry.SPELL_POWER.get());
            return (float) (2 * 6 + 4) + 3.0F * ((float) Math.floor(power / 2.0D) - 3.0F);
        } catch (Throwable t) {
            return pRadius;
        }
    }

    /**
     * 黑洞吸力范围/力度基于包围盒宽度（= 2 × 生成半径）。
     * 蓝色黑洞的半径被公式调小后，吸力也随之衰减。
     * 区分来源：
     * - 无垠长魂之剑释放：吸力半径92格（原版补偿）
     * - 未定义凋灵亡语释放：吸力半径20格
     */
    @ModifyVariable(method = "handleGravity(Lnet/minecraft/world/phys/Vec3;FZ)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false, require = 0)
    private float shatter$boostBlueHolePull(float radius) {
        if (soulSword$getColor() == NO_COLOR) return radius;

        // owner 绑定后仍保持尾杀黑洞原先的 92 格吸力范围。
        // 绑定 owner 只用于攻击归属；不能让黑洞从旧的 92 格退回到
        // BlackHole 实体实际半径（setRadius(20) 时约 40 格）。
        Entity owner = ((Projectile) (Object) this).getOwner();
        if (owner instanceof net.minecraft.world.entity.boss.wither.WitherBoss) {
            return Math.max(radius, 92.0F);
        }

        // 无垠长魂之剑释放：吸力半径92格（原版补偿）
        return Math.max(radius, 92.0F);
    }

    /**
     * Iron's gravity loop uses the projectile owner for friendly-fire checks.
     * A tail-kill black hole is an area attack, so binding its Wither owner
     * must not make the whole area count as allied and skip gravity entirely.
     * The owner is still retained for the generic attack/delta channel below.
     */
    @Redirect(method = "handleGravity(Lnet/minecraft/world/phys/Vec3;FZ)V",
            at = @At(value = "INVOKE", target =
                    "Lio/redspace/ironsspellbooks/damage/DamageSources;isFriendlyFireBetween(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;)Z"),
            remap = false, require = 0)
    private boolean shatter$allowOwnedBlackHoleTargets(Entity attacker, Entity target) {
        if (soulSword$getColor() != NO_COLOR
                && attacker instanceof net.minecraft.world.entity.boss.wither.WitherBoss) {
            return false;
        }
        return DamageSources.isFriendlyFireBetween(attacker, target);
    }

    /**
     * Projectile.canHitEntity also considers the projectile owner. A black hole
     * owned by a Wither must still hit hostile living entities even when their
     * faction logic considers them allied to that owner. Keep the generic
     * projectile checks for all other black holes and targets.
     */
    @Redirect(method = "handleGravity(Lnet/minecraft/world/phys/Vec3;FZ)V",
            at = @At(value = "INVOKE", target =
                    "Lio/redspace/ironsspellbooks/entity/spells/black_hole/BlackHole;m_5603_(Lnet/minecraft/world/entity/Entity;)Z"),
            remap = false, require = 0)
    private boolean shatter$allowOwnedBlackHoleDamage(BlackHole blackHole, Entity target) {
        Entity owner = blackHole.getOwner();
        if (soulSword$getColor() != NO_COLOR
                && owner instanceof net.minecraft.world.entity.boss.wither.WitherBoss
                && target instanceof LivingEntity living) {
            return target != owner && living.isAlive() && !living.isSpectator();
        }
        return shatter$vanillaCanHit(blackHole, target);
    }

    @Unique
    private static boolean shatter$vanillaCanHit(BlackHole blackHole, Entity target) {
        for (String name : new String[]{"canHitEntity", "m_5603_"}) {
            try {
                java.lang.reflect.Method method = Projectile.class.getDeclaredMethod(name, Entity.class);
                method.setAccessible(true);
                return (Boolean) method.invoke(blackHole, target);
            } catch (NoSuchMethodException ignored) {
            } catch (Throwable ignored) {
                return false;
            }
        }
        return target != blackHole.getOwner() && target.isPickable() && !target.isSpectator();
    }

    /**
     * 原版黑洞的伤害判定半径只有 dmgRadius = min(2, radius/5)，即中心周围约 4 格，
     * 实体必须被吸到中心才会吃到伤害（并刷新治疗压制标记）。
     * 对蓝色黑洞（无垠长魂之剑/未定义凋灵亡语通用）：把伤害半径扩大到整个黑洞体积
     * （视觉半径 = 包围盒宽度的一半），只要在洞内就受到伤害。
     */
    @Redirect(method = {
                    "handleGravity(Lnet/minecraft/world/phys/Vec3;FZ)V",
                    "tick",
                    "m_8119_"
            },
            at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(DD)D"),
            remap = false, require = 0)
    private double shatter$widenBlueHoleDamageRadius(double a, double b) {
        if (soulSword$getColor() == NO_COLOR) {
            return Math.min(a, b);
        }
        return ((Entity) (Object) this).getBoundingBox().getXsize() / 2.0D;
    }

    /**
     * 蓝色黑洞伤害增强（无垠长魂之剑 / 未定义凋灵亡语 通用，同款机制）：
     * 1) 泛化有效生命差值通道（未定义凋灵来源），绕开 hurt() 的阶段取消/单次限伤；
     * 2) 直写伤害（普通来源）或原版伤害；
     * 3) 额外虚空伤害：目标最大生命 1% + 5（仅玩家来源时，soul_sword 类型
     *    无视无敌帧、不吃魔法类伤害抗性）；
     * 4) 永久削减最大生命：每次命中 -1 + 0.5%最大生命；
     * 5) 治疗压制：5秒内治疗-90%；
     * 6) 受伤无敌帧减半，保证黑洞每 10 tick 的伤害都能命中。
     */
    @Redirect(method = {
                    "handleGravity(Lnet/minecraft/world/phys/Vec3;FZ)V",
                    "tick",
                    "m_8119_"
            },
            at = @At(value = "INVOKE",
                    target = "Lio/redspace/ironsspellbooks/damage/DamageSources;applyDamage(Lnet/minecraft/world/entity/Entity;FLnet/minecraft/world/damagesource/DamageSource;)Z"),
            remap = false, require = 0)
    private boolean shatter$blueHoleDamage(Entity target, float amount, DamageSource source) {
        if (!(target instanceof LivingEntity living)) {
            return DamageSources.applyDamage(target, amount, source);
        }
        if (soulSword$getColor() == NO_COLOR) {
            return DamageSources.applyDamage(target, amount, source);
        }

        if (living.getMaxHealth() >= 300.0F) {
            LOGGER.info("[SoulSwordBlackHole] damage target={} class={} amount={} hp={}/{} owner={}",
                    living.getName().getString(), living.getClass().getName(), amount,
                    living.getHealth(), living.getMaxHealth(),
                    ((Projectile) (Object) this).getOwner() == null
                            ? "null" : ((Projectile) (Object) this).getOwner().getClass().getName());
        }
        // 已死亡/濒死/死亡结界态（如 Apollyon 被杀死后展开的结界）：
        // 停止伤害与反馈结算（只保留吸引）——否则黑洞每 10 tick 反复 hurt 结界态目标
        // 会触发大量受击音效（杂音），持续到黑洞或结界消失
        if (!living.isAlive() || living.isDeadOrDying() || living.getHealth() <= 0.0F) {
            return false;
        }

        living.invulnerableTime = 0;

        Entity owner = ((Projectile) (Object) this).getOwner();
        Entity causing = source.getEntity();
        WitherBoss undefinedOwner = owner instanceof WitherBoss wither
                && WitherTitleHelper.isUndefinedWither(wither) ? wither
                : (causing instanceof WitherBoss wither
                && WitherTitleHelper.isUndefinedWither(wither) ? wither : null);

        // 主伤害：直写（无 hurt 调用，无 Apollyon "无来源虚空伤害"判定风险）优先；
        // 直写失败时，兜底伤害源必须以"活的 LivingEntity"为 direct/causing——
        // 铁魔法原版法术伤害源的 directEntity 是黑洞（投射物，非 LivingEntity），
        // Apollyon 这类 BOSS 会判定为"无来源虚空伤害"而 discard 清除自身，故改用 owner。
        // 尾杀黑洞由未定义凋灵拥有时，走与头颅/音波相同的完整攻击链：
        // delta -> 隐藏血探针 -> hurt 回落 -> 限伤追击。
        boolean result = undefinedOwner != null
                ? WitherTitleEventHandler.attackUndefinedSoul(undefinedOwner, living, amount)
                : SoulSwordEventHandler.tryDirectWriteDamage(living, causing, amount);
        if (!result && undefinedOwner == null) {
            LivingEntity livingOwner = owner instanceof LivingEntity ownerLiving
                    ? ownerLiving : undefinedOwner;
            if (livingOwner != null) {
                DamageSource ownerSource = SoulSwordEventHandler.soulSwordVoidSource(
                        living.level(), livingOwner, livingOwner);
                result = living.hurt(ownerSource, amount);
            } else {
                result = DamageSources.applyDamage(target, amount, source);
            }
        }

        // 额外虚空伤害：目标最大生命 1% + 5，无视魔法抗性与无敌帧
        // 注意：direct 必须传"发起者"而非黑洞本身——
        // 否则 Goety 的 Apostle.hurt 会因 source.getDirectEntity() 不是 Player/LivingEntity
        // 而将伤害判定为"无来源虚空伤害"，触发 discard() 清除自身。
        // 玩家来源（无垠长魂之剑黑洞）：来源玩家；凋灵来源（尾杀亡语黑洞）：来源该未定义凋灵。
        float bonus = living.getMaxHealth() * 0.01f + 5.0f;
        if (undefinedOwner != null) {
            // 尾杀黑洞的额外虚空伤害也纳入完整未定义凋灵攻击链，
            // 避免只走 directWriteDamage 而绕过 delta/行为验证。
            WitherTitleEventHandler.attackUndefinedSoul(undefinedOwner, living, bonus);
        } else if (causing instanceof Player player) {
            DamageSource voidSource = SoulSwordEventHandler.soulSwordVoidSource(
                    living.level(), player, player);
            if (!SoulSwordEventHandler.tryDirectWriteDamage(living, player, bonus)) {
                living.hurt(voidSource, bonus);
            }
        } else if (owner instanceof LivingEntity livingOwner) {
            DamageSource voidSource = SoulSwordEventHandler.soulSwordVoidSource(
                    living.level(), livingOwner, livingOwner);
            if (!SoulSwordEventHandler.tryDirectWriteDamage(living, livingOwner, bonus)) {
                living.hurt(voidSource, bonus);
            }
        }

        // 每次黑洞攻击：目标最大生命 -1（永久累积）
        shatter$reduceMaxHp(living);
        // 治疗压制：被蓝色黑洞打中的实体 5 秒内治疗 -90%
        BlueHoleHealSuppression.mark(living);

        // 受伤无敌帧减半
        living.invulnerableTime = Math.max(1, living.invulnerableTime / 2);

        return result;
    }

    /** 每次命中给目标永久削减最大生命：-1 再额外扣当前最大生命的 0.5%，累积叠加。
     *  累积值直接读实体属性上的现有修饰器：随 NBT 持久化（重进服务器不丢），
     *  死亡重生时新实体属性重置，削减自动清零（玩家死亡即洗白）。 */
    @Unique
    private static void shatter$reduceMaxHp(LivingEntity target) {
        if (target.level().isClientSide) return;
        AttributeInstance maxHp = target.getAttribute(Attributes.MAX_HEALTH);
        if (maxHp == null) return;
        AttributeModifier existing = maxHp.getModifier(SHATTER$MAX_HP_REDUCTION_UUID);
        float current = existing == null ? 0.0F : (float) -existing.getAmount();
        float reduction = current + 1.0F + target.getMaxHealth() * 0.005F;
        maxHp.removeModifier(SHATTER$MAX_HP_REDUCTION_UUID);
        maxHp.addPermanentModifier(new AttributeModifier(SHATTER$MAX_HP_REDUCTION_UUID,
                "SoulSword black hole maxHP reduction",
                -reduction, AttributeModifier.Operation.ADDITION));
    }

    /**
     * 黑洞伤害在 onCast（释放瞬间）由施法者的当前法术强度快照而来。
     * 若无垠长魂之剑发起施法但释放瞬间剑已不在主/副手，快照会丢失 +1000% 加成，
     * 这里在 setDamage 时按"补回持剑加成后的法术强度"修正伤害，
     * 保证黑洞强度在释放瞬间锁定，之后换手不再影响。
     * 剑仍在手时属性已含加成，跳过避免重复计算。
     */
    @Inject(method = "setDamage", at = @At("TAIL"), require = 0, remap = false)
    private void soulSword$ensureSoulSwordPower(float damage, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self.level().isClientSide) return;
        if (!(self instanceof Projectile projectile)) return;
        if (!(projectile.getOwner() instanceof Player player)) return;
        if (!IronSpellsCompat.isSoulSwordCast(player)) return;
        if (SoulSwordEventHandler.hasSoulSwordInHands(player)) return;

        float scale = IronSpellsCompat.getSoulSwordSpellPowerScale(player);
        if (scale > 1.001F) {
            this.damage = damage * scale;
            LOGGER.info("[SoulSwordBlackHole] soul sword out of hand at cast, boosted damage x{}",
                    Math.round(scale * 100.0F) / 100.0F);
        }
    }

    @Inject(method = "<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)V",
            at = @At("TAIL"), require = 0)
    private void soulSword$markFromSoulSword(Level level, LivingEntity owner, CallbackInfo ci) {
        if (!level.isClientSide && owner instanceof Player player) {
            boolean soulSword = IronSpellsCompat.isSoulSwordCast(player)
                    || SoulSwordEventHandler.hasSoulSwordInHands(player);
            LOGGER.info("[SoulSwordBlackHole] black hole spawn soulSword={} slot={} main={} off={}",
                    soulSword, IronSpellsCompat.getCastingSlot(player),
                    player.getMainHandItem().getItem(), player.getOffhandItem().getItem());
            if (soulSword) {
                soulSword$setColor(SOUL_SWORD_BLUE);
                LOGGER.info("[SoulSwordBlackHole] marked blue");
            }
        }
    }

    @Inject(method = {"addAdditionalSaveData", "m_7380_"}, at = @At("TAIL"), require = 0)
    private void soulSword$saveColor(CompoundTag tag, CallbackInfo ci) {
        int color = soulSword$getColor();
        if (color != NO_COLOR) {
            tag.putInt("soul_sword_color", color);
        }
    }

    @Inject(method = {"readAdditionalSaveData", "m_7378_"}, at = @At("TAIL"), require = 0)
    private void soulSword$loadColor(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains("soul_sword_color")) {
            soulSword$setColor(tag.getInt("soul_sword_color"));
        }
    }
}
