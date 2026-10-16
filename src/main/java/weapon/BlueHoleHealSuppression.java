package weapon;

import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 蓝色黑洞治疗压制：被蓝色黑洞造成伤害的实体，在短暂时间窗口内
 * 受到的治疗效果（脱战回血、吸血、再生等走 heal() 的恢复）降低 90%。
 */
public final class BlueHoleHealSuppression {

    /** 压制窗口：5 秒（100 tick）。黑洞每 10 tick 打一次，实体在洞内会持续刷新标记 */
    private static final long WINDOW_TICKS = 100L;
    private static final Map<UUID, Long> MARKED = new HashMap<>();

    private BlueHoleHealSuppression() {
    }

    public static void mark(LivingEntity entity) {
        if (entity.level().isClientSide) return;
        long now = entity.level().getGameTime();
        MARKED.put(entity.getUUID(), now);
        if (MARKED.size() > 256) {
            MARKED.entrySet().removeIf(e -> now - e.getValue() > WINDOW_TICKS * 2L);
        }
    }

    public static boolean isSuppressed(LivingEntity entity) {
        if (entity.level().isClientSide) return false;
        Long t = MARKED.get(entity.getUUID());
        if (t == null) return false;
        long now = entity.level().getGameTime();
        if (now - t > WINDOW_TICKS) {
            MARKED.remove(entity.getUUID());
            return false;
        }
        return true;
    }
}
