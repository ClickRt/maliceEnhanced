package com.enchantmentSkill.Curse.mockingCurse;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MockingCurseEventHandler {

    /** 记录每个观察者上次被强制转向的时间(tick)，避免每 tick teleport 造成抖动 */
    private static final Map<ServerPlayer, Long> LAST_SNAP_TICK = new WeakHashMap<>();
    private static final long SNAP_COOLDOWN = 10; // 每 10 tick (0.5s) 转向一次

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        for (ServerPlayer cursed : event.getServer().getPlayerList().getPlayers()) {
            if (!cursed.isShiftKeyDown()) continue;

            ItemStack chest = cursed.getItemBySlot(EquipmentSlot.CHEST);
            int level = chest.getEnchantmentLevel(MockingCurseEnchantments.MOCKING_CURSE.get());
            if (level <= 0) continue;

            double range = Math.pow(2, level); // 2, 4, 8
            double rangeSq = range * range;
            Vec3 cursedPos = cursed.position();

            for (ServerPlayer observer : cursed.serverLevel().players()) {
                if (observer == cursed) continue;
                if (observer.distanceToSqr(cursedPos) > rangeSq) continue;

                Vec3 eyePos = observer.getEyePosition();
                Vec3 toCursed = cursedPos.subtract(eyePos).normalize();
                Vec3 lookVec = observer.getViewVector(1.0F);

                double dot = toCursed.dot(lookVec);
                // 观察者视线与诅咒玩家方向的夹角阈值：
                // 近距离(≤2格)宽锥 ~60°, 远距离窄锥 ~30°
                double dist = Math.sqrt(observer.distanceToSqr(cursedPos));
                double threshold = 0.5 + (1.0 - Math.min(dist / range, 1.0)) * 0.4;

                if (dot < threshold) continue;

                // ── 强制转向 ──
                long tick = cursed.serverLevel().getGameTime();
                Long lastSnap = LAST_SNAP_TICK.get(observer);
                if (lastSnap == null || tick - lastSnap >= SNAP_COOLDOWN) {
                    double dx = cursedPos.x - observer.getX();
                    double dy = (cursed.getEyeY() - observer.getEyeY());
                    double dz = cursedPos.z - observer.getZ();
                    double yaw = Math.toDegrees(Math.atan2(-dx, dz));
                    double horizDist = Math.sqrt(dx * dx + dz * dz);
                    double pitch = Math.toDegrees(-Math.atan2(dy, horizDist));

                    observer.connection.teleport(
                            observer.getX(), observer.getY(), observer.getZ(),
                            (float) yaw, (float) pitch);
                    LAST_SNAP_TICK.put(observer, tick);
                }

                // ── 强制攻击 ──
                observer.attack(cursed);
            }
        }
    }
}
