package wither_title.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import wither_title.WitherTitleHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = "malice_enhanced", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class WitherChargeAfterimage {
    static final int LIFE = 4;
    private static final List<Ghost> GHOSTS = new ArrayList<>();
    private static final Map<Integer, Vec3> LAST = new HashMap<>();

    private WitherChargeAfterimage() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            GHOSTS.clear();
            LAST.clear();
            return;
        }
        AABB box = minecraft.player.getBoundingBox().inflate(80.0D);
        for (WitherBoss wither : minecraft.level.getEntitiesOfClass(WitherBoss.class, box,
                WitherTitleHelper::isUndefinedWither)) {
            Vec3 now = wither.position();
            Vec3 prev = LAST.put(wither.getId(), now);
            if (prev == null) continue;
            double distSq = prev.distanceToSqr(now);
            if (distSq < 1.44D) continue;
            int count = Mth.clamp((int) Math.ceil(Math.sqrt(distSq) / 1.5D), 3, 16);
            for (int i = 0; i < count; i++) {
                double t = i / (double) count;
                GHOSTS.add(new Ghost(wither.getId(),
                        Mth.lerp(t, prev.x, now.x),
                        Mth.lerp(t, prev.y, now.y),
                        Mth.lerp(t, prev.z, now.z),
                        wither.getYRot()));
            }
        }
        Iterator<Ghost> it = GHOSTS.iterator();
        while (it.hasNext()) {
            if (++it.next().age > LIFE) it.remove();
        }
    }

    public static List<Ghost> ghostsFor(int entityId) {
        List<Ghost> found = new ArrayList<>();
        for (Ghost ghost : GHOSTS) {
            if (ghost.entityId == entityId) found.add(ghost);
        }
        return found;
    }

    public static final class Ghost {
        public final int entityId;
        public final double x;
        public final double y;
        public final double z;
        public final float yRot;
        public int age;

        Ghost(int entityId, double x, double y, double z, float yRot) {
            this.entityId = entityId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yRot = yRot;
        }
    }
}
