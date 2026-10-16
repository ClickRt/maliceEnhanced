package wither_title;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.UUID;

public class BallLightningEntity extends Entity {
    public static final float DAMAGE = 10.0F;

    private int lifetime = 200;
    private UUID ownerUUID;
    private float damage = DAMAGE;
    private int damageInterval = 1;

    public BallLightningEntity(EntityType<? extends BallLightningEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public BallLightningEntity(Level level, double x, double y, double z, UUID owner) {
        this(level, x, y, z, owner, DAMAGE, 200);
    }

    public BallLightningEntity(Level level, double x, double y, double z, UUID owner, float damage, int lifetime) {
        this(level, x, y, z, owner, damage, lifetime, 1);
    }

    public BallLightningEntity(Level level, double x, double y, double z, UUID owner,
                               float damage, int lifetime, int damageInterval) {
        this(WitherTitleEntities.BALL_LIGHTNING.get(), level);
        this.setPos(x, y, z);
        this.ownerUUID = owner;
        this.damage = damage;
        this.lifetime = lifetime;
        this.damageInterval = Math.max(1, damageInterval);
    }

    public UUID getOwnerUUID() {
        return this.ownerUUID;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;
        if (--this.lifetime <= 0) {
            this.discard();
            return;
        }
        ServerLevel level = (ServerLevel) this.level();
        AABB touchArea = this.getBoundingBox().inflate(0.25D);
        List<LivingEntity> touching = level.getEntitiesOfClass(LivingEntity.class, touchArea, this::canHurt);
        for (LivingEntity living : touching) {
            if (living.hurtTime > 0 || this.tickCount % this.damageInterval != 0) continue;
            WitherBoss owner = null;
            if (this.ownerUUID != null) {
                Entity ownerEntity = level.getEntity(this.ownerUUID);
                if (ownerEntity instanceof WitherBoss wither
                        && WitherTitleHelper.isUndefinedWither(wither)) {
                    owner = wither;
                }
            }
            if (owner != null) {
                WitherTitleEventHandler.attackUndefinedHurt(owner, living, this.damage,
                        this.damageSources().lightningBolt());
            } else {
                living.hurt(this.damageSources().lightningBolt(), this.damage);
            }
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    living.getX(), living.getY() + living.getBbHeight() * 0.5D, living.getZ(),
                    12, 0.3D, 0.3D, 0.3D, 0.2D);
        }
        if (this.tickCount % 2 == 0) {
            WitherTitleHelper.spawnSphereParticles(level, this, ParticleTypes.ELECTRIC_SPARK, 1.0D, 16, 0.0D);
        }
        if (this.tickCount % 5 == 0) {
            level.sendParticles(ParticleTypes.END_ROD,
                    this.getX(), this.getY() + this.getBbHeight() / 2.0D, this.getZ(),
                    2, 0.15D, 0.15D, 0.15D, 0.01D);
        }
    }

    private boolean canHurt(LivingEntity living) {
        if (living instanceof WitherBoss) return false;
        if (living.isSpectator()) return false;
        if (this.ownerUUID != null && this.ownerUUID.equals(living.getUUID())) return false;
        if (living instanceof Player player && (player.isCreative() || player.isSpectator())) return false;
        return true;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Lifetime", this.lifetime);
        tag.putFloat("Damage", this.damage);
        tag.putInt("DamageInterval", this.damageInterval);
        if (this.ownerUUID != null) {
            tag.putUUID("Owner", this.ownerUUID);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.lifetime = tag.getInt("Lifetime");
        if (tag.contains("Damage")) this.damage = tag.getFloat("Damage");
        if (tag.contains("DamageInterval")) this.damageInterval = Math.max(1, tag.getInt("DamageInterval"));
        if (tag.hasUUID("Owner")) {
            this.ownerUUID = tag.getUUID("Owner");
        }
    }
}
