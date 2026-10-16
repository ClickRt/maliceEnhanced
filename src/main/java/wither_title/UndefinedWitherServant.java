package wither_title;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 未定义凋灵的仆从（Goety {@code Owned}/{@code Summoned} 同思路）。
 * 继承 {@link WitherSkeleton} 以复用其凋灵骷髅模型与近战 AI，但把"归属"从
 * 事件层判定下沉到实体 AI 层：无论伤害如何归因（头颅直击 / 爆炸 / 溅射），
 * {@link #isAlliedTo}/{@link #canAttack}/{@link #setTarget} 三重拒绝把主人
 * 与同主仆从当成攻击目标，从根本上杜绝仆从反咬主人。
 *
 * 数据仍复用凋灵侧既有 NBT 键：{@link WitherTitleHelper#WITHER_OWNER_KEY}
 * （主人 UUID）与 {@link WitherTitleHelper#WITHER_SERVANT_EXPIRE_KEY}
 * （寿命到期 gameTime），因此 {@link WitherTitleHelper#isWitherServant}
 * 及既有扫描逻辑无需改动即可识别本实体。
 */
public class UndefinedWitherServant extends WitherSkeleton {

    public UndefinedWitherServant(EntityType<? extends UndefinedWitherServant> type, Level level) {
        super(type, level);
        // 仆从由凋灵召唤，需驻留；否则会被刷怪上限的自然清除提前移除
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return WitherSkeleton.createAttributes();
    }

    // ==================== 归属数据（从 persistent data 读取，无需额外存档逻辑） ====================

    @Nullable
    public UUID getOwnerUUID() {
        CompoundTag data = this.getPersistentData();
        return data.hasUUID(WitherTitleHelper.WITHER_OWNER_KEY) ? data.getUUID(WitherTitleHelper.WITHER_OWNER_KEY) : null;
    }

    @Nullable
    public WitherBoss getOwner() {
        UUID ownerUUID = getOwnerUUID();
        if (ownerUUID == null || !(this.level() instanceof ServerLevel serverLevel)) return null;
        return serverLevel.getEntity(ownerUUID) instanceof WitherBoss wither ? wither : null;
    }

    /** 寿命到期 gameTime；未设置（旧实体/永久配置）返回 Long.MAX_VALUE 表示永久。 */
    private long expireGameTime() {
        CompoundTag data = this.getPersistentData();
        return data.contains(WitherTitleHelper.WITHER_SERVANT_EXPIRE_KEY)
                ? data.getLong(WitherTitleHelper.WITHER_SERVANT_EXPIRE_KEY)
                : Long.MAX_VALUE;
    }

    // ==================== 归属判定核心 ====================

    /**
     * 该目标是否属于"不可攻击"：主人本体、任意凋灵、同主仆从。
     * 仅以 UUID 比较，不依赖主人实体是否加载，客户端/服务端一致。
     */
    private boolean isUnattackable(@Nullable LivingEntity target) {
        if (target == null) return false;
        UUID ownerUUID = getOwnerUUID();
        if (ownerUUID != null && target.getUUID().equals(ownerUUID)) return true;
        if (target instanceof WitherBoss) return true;
        // 同主人的其它仆从（含本类与旧版 WitherSkeleton 仆从）
        if (target instanceof UndefinedWitherServant other
                && other.getOwnerUUID() != null && other.getOwnerUUID().equals(ownerUUID)) return true;
        return target instanceof WitherSkeleton skeleton && WitherTitleHelper.isWitherServant(skeleton, ownerUUID);
    }

    /** 该实体是否为己方（主人/同主仆从）——供 isAlliedTo 使用。 */
    private boolean isFriendly(Entity entity) {
        if (entity == null) return false;
        UUID ownerUUID = getOwnerUUID();
        if (ownerUUID != null && entity.getUUID().equals(ownerUUID)) return true;
        if (entity instanceof WitherBoss) return true;
        if (entity instanceof UndefinedWitherServant other
                && other.getOwnerUUID() != null && other.getOwnerUUID().equals(ownerUUID)) return true;
        return entity instanceof WitherSkeleton skeleton && WitherTitleHelper.isWitherServant(skeleton, ownerUUID);
    }

    @Override
    public boolean isAlliedTo(Entity entityIn) {
        // 主人及同主仆从视为同盟：原版 TargetGoal.canAttack 会因此直接放弃锁定，
        // 与伤害来源归属无关——这是相对事件层兜底（onLivingAttack）的根治点。
        if (this.isFriendly(entityIn)) return true;
        return super.isAlliedTo(entityIn);
    }

    @Override
    public boolean canAttack(@Nullable LivingEntity target) {
        if (this.isUnattackable(target)) return false;
        return super.canAttack(target);
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        // 最后一道闸：任何路径（HurtByTargetGoal 反咬等）试图把主人设为目标，一律清空。
        if (this.isUnattackable(target)) target = null;
        super.setTarget(target);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;

        // 寿命到期自动消散（Goety setLimitedLife 同思路）
        if (this.level().getGameTime() >= this.expireGameTime()) {
            this.discard();
            return;
        }

        // 共享主人仇恨：自身无有效目标且主人正在战斗时，采纳主人当前目标
        if (this.tickCount % 10 == 0) {
            LivingEntity cur = this.getTarget();
            if (cur == null || !cur.isAlive() || this.isUnattackable(cur)) {
                WitherBoss owner = this.getOwner();
                if (owner != null && owner.isAlive()) {
                    LivingEntity ownerTarget = owner.getTarget();
                    if (ownerTarget != null && ownerTarget.isAlive() && !this.isUnattackable(ownerTarget)) {
                        this.setTarget(ownerTarget);
                    }
                }
            }
        }
    }
}
