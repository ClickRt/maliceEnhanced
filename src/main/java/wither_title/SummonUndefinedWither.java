package wither_title;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;

/**
 * 祭坛召唤 ? 凋灵 ? 时的动画实体，类似 Goety 的 SummonApostle。
 * 在 10 秒内播放粒子旋转动画，结束后生成真正的未定义凋灵。
 */
public class SummonUndefinedWither extends Entity {

    private static final int TOTAL_TICKS = 200; // 10 秒

    public SummonUndefinedWither(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;

        ServerLevel serverLevel = (ServerLevel) this.level();
        float progress = Mth.clamp(this.tickCount / (float) TOTAL_TICKS, 0.0F, 1.0F);

        // ===== 粒子效果 =====
        // 粒子数量：从 8 逐渐增加到 20
        int particleCount = 8 + (int) (progress * 12);
        // 旋转半径：从 2.0 逐渐扩大到 3.5
        double radius = 2.0 + progress * 1.5;
        // 旋转速度：从 0.04 逐渐加快到 0.20 rad/tick（顺时针）
        double angularSpeed = 0.04 + progress * 0.16;
        // 基础角度，随时间增加
        double baseAngle = this.tickCount * angularSpeed;
        // 粒子的 Y 轴高度：从 0.3 逐渐上升到 1.0
        double yBase = 0.3 + progress * 0.7;

        // 每 2 tick 发送一次粒子，避免过于密集
        if (this.tickCount % 2 == 0) {
            for (int i = 0; i < particleCount; i++) {
                double angle = baseAngle + (2 * Math.PI * i / particleCount);
                double x = this.getX() + radius * Math.cos(angle);
                double z = this.getZ() + radius * Math.sin(angle);
                double y = this.getY() + yBase + Math.sin(angle * 2 + this.tickCount * 0.1) * 0.3;

                // 三种粒子轮流使用
                if (i % 3 == 0) {
                    // 灵魂沙粒子
                    serverLevel.sendParticles(ParticleTypes.SOUL, x, y, z, 1, 0, 0, 0, 0);
                } else if (i % 3 == 1) {
                    // 灵魂火把蓝色火焰粒子
                    serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y + 0.2, z, 1, 0, 0, 0, 0);
                } else {
                    // 哭泣黑曜石粒子
                    serverLevel.sendParticles(ParticleTypes.DRIPPING_OBSIDIAN_TEAR, x, y + 0.5, z, 1, 0, 0, 0, 0);
                }
            }
        }

        // 中心区域额外粒子（从第 5 秒开始出现）
        if (progress > 0.5 && this.tickCount % 4 == 0) {
            double centerX = this.getX() + (this.random.nextDouble() - 0.5) * 1.5;
            double centerZ = this.getZ() + (this.random.nextDouble() - 0.5) * 1.5;
            double centerY = this.getY() + 0.3 + this.random.nextDouble() * 0.8;
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, centerX, centerY, centerZ, 1, 0, 0, 0, 0);
        }

        // ===== 音效 =====
        if (this.tickCount == 1) {
            this.playSound(SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD.get(), 2.0F, 0.8F);
        }
        if (this.tickCount == 100) {
            this.playSound(SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD.get(), 2.0F, 1.2F);
        }
        if (this.tickCount == 180) {
            // 闪电预示凋灵即将出现
            this.playSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 2.0F, 1.0F);
        }

        // ===== 生成未定义凋灵 =====
        if (this.tickCount >= TOTAL_TICKS) {
            WitherBoss wither = EntityType.WITHER.create(this.level());
            if (wither != null) {
                wither.setPos(this.getX(), this.getY(), this.getZ());
                // 添加 undefined_wither 标签，EntityJoinLevelEvent 会处理后续初始化
                wither.addTag("undefined_wither");
                serverLevel.addFreshEntity(wither);
                // 炸裂效果
                for (int k = 0; k < 100; ++k) {
                    float f2 = this.random.nextFloat() * 4.0F;
                    float f1 = this.random.nextFloat() * ((float) Math.PI * 2F);
                    double d1 = Mth.cos(f1) * f2;
                    double d2 = 0.01D + this.random.nextDouble() * 0.5D;
                    double d3 = Mth.sin(f1) * f2;
                    serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            this.getX() + d1 * 0.1D, this.getY() + 0.3D, this.getZ() + d3 * 0.1D,
                            0, d1, d2, d3, 0.5F);
                }
                this.playSound(SoundEvents.WITHER_SPAWN, 1.0F, 1.0F);
            }
            this.discard();
        }
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}