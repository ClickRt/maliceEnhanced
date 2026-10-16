package weapon;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 星翼幻想演出哈迪斯（{@code HadesEntity}，非仆从）击杀后按亚小妹 UUID 禁补刷。
 */
public final class StarFantasyHadesBan {
    private static final String HADES_CLASS = "com.starfantasy.goety.entity.HadesEntity";
    private static final String APOLLYON_CLASS = "com.starfantasy.goety.entity.ApollyonEntity";
    private static final Set<UUID> BANNED_OWNERS = ConcurrentHashMap.newKeySet();
    private static Method ownerUuidMethod;

    private StarFantasyHadesBan() {
    }

    public static boolean isPageantHades(Entity entity) {
        return entity != null && HADES_CLASS.equals(entity.getClass().getName());
    }

    public static boolean isApollyonBoss(Entity entity) {
        return entity != null && APOLLYON_CLASS.equals(entity.getClass().getName());
    }

    public static boolean isOwnerBanned(UUID ownerId) {
        return ownerId != null && BANNED_OWNERS.contains(ownerId);
    }

    public static boolean isOwnerBanned(Entity owner) {
        return owner != null && isOwnerBanned(owner.getUUID());
    }

    public static UUID ownerUuid(Entity hades) {
        if (!isPageantHades(hades)) {
            return null;
        }
        try {
            if (ownerUuidMethod == null) {
                ownerUuidMethod = hades.getClass().getMethod("ownerUuid");
            }
            Object value = ownerUuidMethod.invoke(hades);
            return value instanceof UUID uuid ? uuid : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean tryBanAndDiscard(Entity entity) {
        if (entity == null || entity.level().isClientSide() || !isPageantHades(entity)) {
            return false;
        }
        UUID owner = ownerUuid(entity);
        if (owner != null) {
            BANNED_OWNERS.add(owner);
        }
        entity.discard();
        return true;
    }

    public static boolean shouldAcceptHit(DamageSource source) {
        if (source == null) {
            return false;
        }
        Entity direct = source.getDirectEntity();
        Entity causing = source.getEntity();
        return direct instanceof Player
                || causing instanceof Player
                || direct instanceof SoulBowArrow
                || causing instanceof SoulBowArrow;
    }

    public static void onEntityLeave(Entity entity, Entity.RemovalReason reason) {
        if (!isApollyonBoss(entity)) {
            return;
        }
        if (reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED) {
            BANNED_OWNERS.remove(entity.getUUID());
        }
    }
}
