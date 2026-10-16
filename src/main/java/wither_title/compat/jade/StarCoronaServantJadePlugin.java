package wither_title.compat.jade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import weapon.starcrystal.StarCoronaServantAllay;

import java.util.UUID;

/** Jade integration for the owner line shown on a Star Corona servant. */
@WailaPlugin
public final class StarCoronaServantJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerEntityDataProvider(StarCoronaServantOwnerProvider.INSTANCE,
                StarCoronaServantAllay.class);
    }

    private enum StarCoronaServantOwnerProvider
            implements IServerDataProvider<EntityAccessor> {
        INSTANCE;

        private static final String OWNER_NAME = "OwnerName";
        private static final ResourceLocation UID =
                new ResourceLocation("malice_enhanced", "star_corona_servant_owner");

        @Override
        public void appendServerData(CompoundTag data, EntityAccessor accessor) {
            Entity entity = accessor.getEntity();
            if (!(entity instanceof StarCoronaServantAllay servant)) return;

            UUID ownerId = servant.getOwnerUUID();
            if (ownerId == null || !(accessor.getLevel() instanceof ServerLevel serverLevel)) return;

            String ownerName = null;
            ServerPlayer owner = serverLevel.getServer().getPlayerList().getPlayer(ownerId);
            if (owner != null) {
                ownerName = owner.getGameProfile().getName();
            } else {
                ownerName = serverLevel.getServer().getProfileCache()
                        .get(ownerId)
                        .map(profile -> profile.getName())
                        .orElse(null);
            }
            if (ownerName != null && !ownerName.isBlank()) {
                data.putString(OWNER_NAME, ownerName);
            }
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }
}
