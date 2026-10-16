package wither_title;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

public class GravitySingularityEntity extends Entity {
    public static final double DEFAULT_PULL_RADIUS = 5.0D;
    private static final double BASE_STRENGTH = 0.12D;

    private double pullRadius = DEFAULT_PULL_RADIUS;
    private int lifetime = 100;
    private UUID ownerUUID;
    private boolean blueMode = false;
    private boolean skipPlayers = false;

    public GravitySingularityEntity(EntityType<? extends GravitySingularityEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public GravitySingularityEntity(Level level, double x, double y, double z, UUID owner) {
        this(WitherTitleEntities.GRAVITY_SINGULARITY.get(), level);
        this.setPos(x, y, z);
        this.ownerUUID = owner;
    }

    public void setPullRadius(double radius) {
        this.pullRadius = radius;
    }

    public void setLifetime(int lifetime) {
        this.lifetime = lifetime;
    }

    public void setBlueMode(boolean blue) {
        this.blueMode = blue;
    }

    public void setSkipPlayers(boolean skipPlayers) {
        this.skipPlayers = skipPlayers;
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
        Vec3 center = this.position().add(0, this.getBbHeight() / 2.0D, 0);
        AABB range = this.getBoundingBox().inflate(this.pullRadius);
        List<Entity> entities = level.getEntities(this, range, this::canPull);
        for (Entity entity : entities) {
            Vec3 dir = center.subtract(entity.position().add(0, entity.getBbHeight() * 0.5D, 0));
            double dist = dir.length();
            if (dist < 0.75D) {
                entity.setDeltaMovement(entity.getDeltaMovement().scale(0.5D));
            } else {
                double strength = BASE_STRENGTH * (1.0D - Math.min(dist, this.pullRadius) / this.pullRadius);
                entity.setDeltaMovement(entity.getDeltaMovement().add(dir.normalize().scale(strength)));
            }
            entity.fallDistance = 0.0F;
            if (entity instanceof ServerPlayer player) {
                player.connection.send(new ClientboundSetEntityMotionPacket(player));
            }
            if (entity instanceof LivingEntity living) {
                WitherTitleHelper.markSingularity(living, 20);
            }
        }
        if (this.tickCount % 3 == 0) {
            WitherTitleHelper.spawnSphereParticles(level, this, this.blueMode ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.PORTAL, 1.0D, 24, 0.0D);
        }
        if (this.tickCount % 20 == 0) {
            level.sendParticles(this.blueMode ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.PORTAL, center.x, center.y, center.z, 16, 0.25D, 0.25D, 0.25D, 0.4D);
        }
    }

    private boolean canPull(Entity entity) {
        if (entity instanceof WitherBoss) return false;
        if (entity instanceof GravitySingularityEntity || entity instanceof BallLightningEntity) return false;
        if (entity.isSpectator()) return false;
        if (entity instanceof Player player) {
            if (this.skipPlayers || player.isCreative()) return false;
        }
        if (WitherTitleHelper.isWitherServant(entity, this.ownerUUID)) return false;
        return true;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putDouble("PullRadius", this.pullRadius);
        tag.putBoolean("BlueMode", this.blueMode);
        tag.putBoolean("SkipPlayers", this.skipPlayers);
        tag.putInt("Lifetime", this.lifetime);
        if (this.ownerUUID != null) {
            tag.putUUID("Owner", this.ownerUUID);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.pullRadius = tag.getDouble("PullRadius");
        this.blueMode = tag.getBoolean("BlueMode");
        this.skipPlayers = tag.getBoolean("SkipPlayers");
        this.lifetime = tag.getInt("Lifetime");
        if (tag.hasUUID("Owner")) {
            this.ownerUUID = tag.getUUID("Owner");
        }
    }
}