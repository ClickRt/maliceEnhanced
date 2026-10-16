package weapon;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 魂蓝弓的服务端目标选取：沿发射者视线做锥形扫描，挑出与客户端瞄准环
 * （{@code weapon.client.SoulBowAimOverlay}）语义一致的可锁定目标。
 * <p>
 * 瞄准环是纯客户端的屏幕投影，服务端拿不到"谁被框进环里"，因此在放箭瞬间
 * 用"视线方向 + 夹角阈值 + 视线可达"在服务器上复现同一套筛选：夹角阈值
 * 近似对应屏幕上的圆环半径。
 */
public final class SoulBowHoming {

    private SoulBowHoming() {
    }

    /** 可锁定目标的最大距离，与瞄准环 {@code AIM_RANGE} 保持一致 */
    public static final double AIM_RANGE = 100.0D;
    /** 视线锥形半角的余弦阈值（约 10°），收紧以贴合小瞄准环，只锁正对准星的目标 */
    public static final double AIM_CONE_COS = 0.97D;

    /**
     * 从 {@code origin} 沿 {@code forward} 方向，在 {@code range} 距离、
     * {@code coneCos} 夹角内，按"离准星中心由近及远"挑选最多 {@code maxCount} 个可锁定目标。
     *
     * @param shooter 发射者（用于排除自身、判断创造/旁观与可见性）
     */
    public static List<LivingEntity> pickAlongLook(Level level, LivingEntity shooter, Vec3 origin,
                                                   Vec3 forward, double range, double coneCos, int maxCount) {
        List<LivingEntity> best = new ArrayList<>();
        List<Double> bestCos = new ArrayList<>();
        if (shooter == null || maxCount <= 0 || forward.lengthSqr() < 1.0E-6D) {
            return best;
        }
        Vec3 fwd = forward.normalize();
        AABB area = new AABB(origin, origin).inflate(range);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area, e -> isAimable(shooter, e))) {
            Vec3 center = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
            Vec3 to = center.subtract(origin);
            double len = to.length();
            if (len < 1.0E-2D || len > range) {
                continue;
            }
            double dot = fwd.dot(to) / len;
            if (dot < coneCos) {
                continue;
            }
            if (!hasLineOfSight(level, shooter, origin, target)) {
                continue;
            }
            // 插入排序，保持按夹角由小到大（越准心越靠前），仅保留前 maxCount 个
            int pos = 0;
            while (pos < best.size() && bestCos.get(pos) >= dot) {
                pos++;
            }
            best.add(pos, target);
            bestCos.add(pos, dot);
            if (best.size() > maxCount) {
                best.remove(best.size() - 1);
                bestCos.remove(bestCos.size() - 1);
            }
        }
        return best;
    }

    /** 与瞄准环 {@code isAimable} 对齐：活着的、非自身、非创造/旁观、对射击者可见的活体 */
    private static boolean isAimable(LivingEntity shooter, LivingEntity target) {
        if (target == shooter) {
            return false;
        }
        if (!target.isAlive() || target.isRemoved()) {
            return false;
        }
        if (target instanceof Player other && (other.isCreative() || other.isSpectator())) {
            return false;
        }
        if (shooter instanceof Player player) {
            if (target instanceof Player foe && !player.canHarmPlayer(foe)) {
                return false;
            }
            if (target.isInvisibleTo(player)) {
                return false;
            }
        }
        return true;
    }

    /** 视线可达检测：从 {@code from} 到目标眼位被方块遮挡则不可锁定 */
    private static boolean hasLineOfSight(Level level, LivingEntity shooter, Vec3 from, LivingEntity target) {
        Vec3 to = target.getEyePosition();
        HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
        return hit.getType() == HitResult.Type.MISS;
    }
}
