package wither_title;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.Optional;
import java.util.UUID;

/**
 * Server-side link for the title 6 beam. The client renderer uses the two synced
 * entity UUIDs to draw the beam directly from the allay to its owner.
 */
public class StarCoronaBeamEntity extends Entity {
    private static final EntityDataAccessor<Optional<UUID>> ALLAY_ID =
            SynchedEntityData.defineId(StarCoronaBeamEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> OWNER_ID =
            SynchedEntityData.defineId(StarCoronaBeamEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    private int lifetime = 900;

    public StarCoronaBeamEntity(EntityType<? extends StarCoronaBeamEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public StarCoronaBeamEntity(Level level, UUID allayId, UUID ownerId) {
        this(WitherTitleEntities.STAR_CORONA_BEAM.get(), level);
        this.entityData.set(ALLAY_ID, Optional.ofNullable(allayId));
        this.entityData.set(OWNER_ID, Optional.ofNullable(ownerId));
    }

    public UUID getAllayUUID() {
        return this.entityData.get(ALLAY_ID).orElse(null);
    }

    public UUID getOwnerUUID() {
        return this.entityData.get(OWNER_ID).orElse(null);
    }

    public Entity getAllay() {
        return findEntity(getAllayUUID());
    }

    public Entity getOwner() {
        return findEntity(getOwnerUUID());
    }

    private Entity findEntity(UUID id) {
        if (id == null) return null;
        if (level() instanceof ServerLevel serverLevel) return serverLevel.getEntity(id);
        AABB search = this.getBoundingBox().inflate(128.0D);
        return level().getEntities(this, search, entity -> id.equals(entity.getUUID()))
                .stream().findFirst().orElse(null);
    }

    public boolean isExpired() {
        return lifetime <= 0;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(ALLAY_ID, Optional.empty());
        this.entityData.define(OWNER_ID, Optional.empty());
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;
        if (--this.lifetime <= 0) {
            this.discard();
            return;
        }

        Entity allay = getAllay();
        Entity owner = getOwner();
        if (!(allay instanceof Allay) || owner == null || !owner.isAlive()) {
            this.discard();
            return;
        }
        this.setPos(allay.getX(), allay.getY(), allay.getZ());
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        UUID allay = getAllayUUID();
        UUID owner = getOwnerUUID();
        if (allay != null) tag.putUUID("Allay", allay);
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putInt("Lifetime", lifetime);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Allay")) this.entityData.set(ALLAY_ID, Optional.of(tag.getUUID("Allay")));
        if (tag.hasUUID("Owner")) this.entityData.set(OWNER_ID, Optional.of(tag.getUUID("Owner")));
        this.lifetime = tag.getInt("Lifetime");
    }
}
