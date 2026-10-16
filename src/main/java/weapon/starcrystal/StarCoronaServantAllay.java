package weapon.starcrystal;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * 星冕宝晶头衔 6 召唤的悦灵仆从（自定义实体，替代原版 {@link Allay}）。
 *
 * 与凋灵侧 {@link wither_title.UndefinedWitherServant} 同思路：把"归属"下沉到
 * 实体自身，保证行为稳定且可被既有扫描逻辑（按 owner NBT 识别）继续复用。
 *
 * 相比原版悦灵的定制：
 * 1. 最大生命固定 30、护甲 10（在 {@link #createAttributes()} 内建立，无需运行时属性修饰符）；
 * 2. 防接管——{@link #mobInteract} 只放行主人，其它玩家无法给它物品 / 用护符使其跟随；
 * 3. 独立远程攻击——悦灵自身寻找附近的怪物，使用手中的原版弓拉弦并射出
 *    等效力量 V 的箭矢（无限箭，不掉落、不可拾取）；玩家攻击目标时会优先跟随攻击。
 *
 * owner UUID 沿用事件侧同一 NBT 键 {@link #OWNER_KEY}
 * （= {@code malice_enhanced:star_allay_owner}），因此既有召唤/维护/清理逻辑无需改动。
 */
public class StarCoronaServantAllay extends Allay {

    /** 与 {@code StarCoronaCrystalEvents.SERVANT_OWNER} 保持一致的 owner NBT 键。 */
    public static final String OWNER_KEY = "malice_enhanced:star_allay_owner";
    private static final double MAX_HEALTH_VALUE = 30.0D;
    private static final double ARMOR_VALUE = 10.0D;

    // ===== 独立远程攻击参数 =====
    /** 仆从弓和箭矢的固定力量等级（力量 V）。 */
    private static final int REQUIRED_POWER_LEVEL = 5;
    /** 以悦灵自身为中心的索敌半径（格）。 */
    private static final double ASSIST_RANGE = 20.0D;
    /** 两次射击间隔（刻），约 0.75 秒一箭。 */
    private static final int SHOOT_INTERVAL_TICKS = 15;
    /** 拉弓蓄力时长（刻）；期间播放弓拉弦动画并转向目标，蓄满后放箭。 */
    private static final int DRAW_TICKS = 12;
    /** 箭矢初速，接近满弓拉弦。 */
    private static final float ARROW_VELOCITY = 3.0F;
    /** 散布，较小以保证命中。 */
    private static final float ARROW_INACCURACY = 0.2F;
    /**
     * 箭矢基础伤害：原版箭基础 2.0 + 力量加成 {@code level*0.5 + 0.5}（同 BowItem 公式），
     * 力量 V 即 2.0 + 5*0.5 + 0.5 = 5.0。
     */
    private static final double ARROW_BASE_DAMAGE =
            2.0D + REQUIRED_POWER_LEVEL * 0.5D + 0.5D;

    /** 射击冷却计时（服务端）。 */
    private int shootCooldown = 0;
    /** 当前蓄力剩余刻（>0 表示正在拉弓）。 */
    private int drawTicks = 0;
    /** 蓄力锁定的目标 UUID。 */
    @Nullable
    private UUID currentTargetId = null;
    /** 玩家最近主动攻击的目标；只在当前实体存活期间保留。 */
    @Nullable
    private UUID commandedTargetId = null;

    public StarCoronaServantAllay(EntityType<? extends StarCoronaServantAllay> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        // Allay.createAttributes() 原生 MAX_HEALTH=3，此处 add 覆盖为 30，并补 10 护甲
        return Allay.createAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH_VALUE)
                .add(Attributes.ARMOR, ARMOR_VALUE);
    }

    @Override
    public boolean isPersistenceRequired() {
        return true;
    }

    @Nullable
    public UUID getOwnerUUID() {
        CompoundTag data = this.getPersistentData();
        return data.hasUUID(OWNER_KEY) ? data.getUUID(OWNER_KEY) : null;
    }

    /**
     * 通知该玩家所属的悦灵跟随攻击目标。目标不限定为 Monster，
     * 因此铁傀儡等生物也能响应玩家的攻击。
     */
    public static void notifyOwnerAttack(Player owner, LivingEntity target) {
        if (owner == null || target == null || owner.level().isClientSide
                || !(owner.level() instanceof ServerLevel level)
                || target.level() != level || !target.isAlive() || target == owner) return;

        AABB box = owner.getBoundingBox().inflate(128.0D);
        for (StarCoronaServantAllay servant : level.getEntitiesOfClass(StarCoronaServantAllay.class, box,
                candidate -> owner.getUUID().equals(candidate.getOwnerUUID()))) {
            servant.setCommandedTarget(target);
        }
    }

    private void setCommandedTarget(LivingEntity target) {
        if (!isValidCommandedTarget(target)) return;
        this.commandedTargetId = target.getUUID();
        if (this.drawTicks > 0 && !target.getUUID().equals(this.currentTargetId)) {
            abortDraw();
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        // 防接管：服务端仅主人可交互（给物品 / 护符跟随）；非主人一律 PASS，不触发原版 Allay 的收取逻辑
        if (!this.level().isClientSide) {
            UUID ownerUUID = getOwnerUUID();
            if (ownerUUID == null || !ownerUUID.equals(player.getUUID())) {
                return InteractionResult.PASS;
            }
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;
        // 保证主手始终是真实的弓：它会被 startUsingItem 使用，从而播放拉弓动画。
        // 若主人交互或悦灵拾取了其它物品，则恢复为仆从专用弓。
        if (this.getItemBySlot(EquipmentSlot.MAINHAND).getItem() != Items.BOW) {
            this.setItemSlot(EquipmentSlot.MAINHAND, createAssistBow());
        }
        if (!(this.level() instanceof ServerLevel level)) return;

        if (this.drawTicks > 0) {
            advanceDraw(level);
        } else {
            if (this.shootCooldown > 0) this.shootCooldown--;
            // 每 5 刻检查一次索敌条件，降低每 tick 遍历开销
            if (this.shootCooldown <= 0 && this.tickCount % 5 == 0) {
                tryStartDraw(level);
            }
        }
    }

    /** 由悦灵自身索敌并锁定目标，进入蓄力（拉弓）状态。 */
    private void tryStartDraw(ServerLevel level) {
        if (!isHoldingBow()) return;
        LivingEntity target = findNearestHostile(level);
        if (target == null) return;

        this.currentTargetId = target.getUUID();
        this.drawTicks = DRAW_TICKS;
        aimAt(target);
        // 进入"使用物品"状态：原版据此播放弓的拉弦动画（bow_pulling_*）
        this.startUsingItem(InteractionHand.MAIN_HAND);
    }

    /** 蓄力期间：持续转向目标，蓄满后放箭并结束拉弓。 */
    private void advanceDraw(ServerLevel level) {
        Entity entity = this.currentTargetId == null ? null : level.getEntity(this.currentTargetId);
        if (!isHoldingBow() || !(entity instanceof LivingEntity target) || !target.isAlive()) {
            abortDraw();
            return;
        }
        if (!this.hasLineOfSight(target)) {
            abortDraw();
            return;
        }
        aimAt(target);
        this.drawTicks--;
        if (this.drawTicks <= 0) {
            aimAt(target);
            shootArrow(level, target);
            this.stopUsingItem();
            this.currentTargetId = null;
            this.shootCooldown = SHOOT_INTERVAL_TICKS;
        }
    }

    private void abortDraw() {
        this.drawTicks = 0;
        this.currentTargetId = null;
        this.stopUsingItem();
    }

    /** 生成一支等效力量 V 的箭矢；有主人时归属主人，便于击杀归因。 */
    private void shootArrow(ServerLevel level, LivingEntity target) {
        Arrow arrow = new Arrow(level, this);
        arrow.setPos(this.getX(), this.getEyeY() - 0.1D, this.getZ());
        Player owner = getOwnerPlayer(level);
        if (owner != null) arrow.setOwner(owner);
        double dx = target.getX() - this.getX();
        double dy = target.getEyeY() - this.getEyeY();
        double dz = target.getZ() - this.getZ();
        arrow.shoot(dx, dy, dz, ARROW_VELOCITY, ARROW_INACCURACY);
        arrow.setBaseDamage(ARROW_BASE_DAMAGE);
        arrow.setCritArrow(true);
        // 无限箭：不掉落、不可拾取，避免刷屏与捡取
        arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        level.addFreshEntity(arrow);
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0F, 1.2F);
    }

    /** 原版悦灵会绕过普通掉落概率直接丢出主手物品，这里改为 20% 概率。 */
    @Override
    protected void dropEquipment() {
        ItemStack bow = this.getItemBySlot(EquipmentSlot.MAINHAND).copy();
        this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        super.dropEquipment();
        if (!bow.isEmpty() && this.random.nextFloat() < 0.20F) {
            this.spawnAtLocation(bow);
        }
        this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }

    /** 立即同步实体的头部、身体和视线方向，避免发箭时仍朝向上一帧。 */
    private void aimAt(LivingEntity target) {
        double dx = target.getX() - this.getX();
        double dy = target.getEyeY() - this.getEyeY();
        double dz = target.getZ() - this.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        float pitch = (float) (-(Math.atan2(dy, horizontal) * (180.0D / Math.PI)));
        pitch = Mth.clamp(pitch, -90.0F, 90.0F);

        this.setYRot(yaw);
        this.setYHeadRot(yaw);
        this.setYBodyRot(yaw);
        this.setXRot(pitch);
        this.yRotO = yaw;
        this.yHeadRotO = yaw;
        this.yBodyRotO = yaw;
        this.xRotO = pitch;
    }

    @Nullable
    private Player getOwnerPlayer(ServerLevel level) {
        UUID ownerUUID = getOwnerUUID();
        if (ownerUUID == null) return null;
        return level.getEntity(ownerUUID) instanceof Player player
                && player.isAlive() && !player.isRemoved() ? player : null;
    }

    /** 悦灵主手是否仍持有可用于播放拉弓动画的原版弓。 */
    private boolean isHoldingBow() {
        return this.getItemBySlot(EquipmentSlot.MAINHAND).getItem() == Items.BOW;
    }

    /** 供悦灵手持的外观弓：原版弓 + 力量 V 附魔（带附魔闪光，与协助射击机制呼应）。 */
    public static ItemStack createAssistBow() {
        ItemStack bow = new ItemStack(Items.BOW);
        bow.enchant(Enchantments.POWER_ARROWS, REQUIRED_POWER_LEVEL);
        return bow;
    }

    /** 取悦灵附近 {@link #ASSIST_RANGE} 格内最近的敌对怪物。 */
    @Nullable
    private LivingEntity findNearestHostile(ServerLevel level) {
        AABB box = this.getBoundingBox().inflate(ASSIST_RANGE);
        Player owner = getOwnerPlayer(level);
        LivingEntity commandedTarget = getCommandedTarget(level);
        if (commandedTarget != null) return commandedTarget;
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != this && e.isAlive() && e instanceof Monster
                        && !e.isAlliedTo(this)
                        && (owner == null || (e != owner && !e.isAlliedTo(owner)))
                        && this.hasLineOfSight(e));
        LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity candidate : candidates) {
            double distSqr = candidate.distanceToSqr(this);
            if (distSqr < best) {
                best = distSqr;
                nearest = candidate;
            }
        }
        return nearest;
    }

    @Nullable
    private LivingEntity getCommandedTarget(ServerLevel level) {
        if (this.commandedTargetId == null) return null;
        Entity entity = level.getEntity(this.commandedTargetId);
        if (!(entity instanceof LivingEntity target) || !isValidCommandedTarget(target)) {
            this.commandedTargetId = null;
            return null;
        }
        if (target.distanceToSqr(this) > ASSIST_RANGE * ASSIST_RANGE || !this.hasLineOfSight(target)) {
            return null;
        }
        return target;
    }

    private boolean isValidCommandedTarget(LivingEntity target) {
        if (target == this || target.isRemoved() || !target.isAlive()) return false;
        if (target instanceof StarCoronaServantAllay) return false;
        Player owner = this.level() instanceof ServerLevel level ? getOwnerPlayer(level) : null;
        return target != owner && !target.isAlliedTo(this)
                && (owner == null || !target.isAlliedTo(owner));
    }
}
