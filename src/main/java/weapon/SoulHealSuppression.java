package weapon;

import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 无垠长魂之剑普通攻击附带的治疗压制（-50%）。
 * 与蓝色黑洞的 90% 压制（BlueHoleHealSuppression）相互独立，可叠加。
 * 机制同样是 map 标记窗口，走 LivingEntity.heal() 的 mixin 生效，
 * 不依赖药水效果，因此对 Boss 的状态免疫也有效。
 */
public final class SoulHealSuppression {

    /** 压制窗口：5 秒（100 tick），普通攻击每刀刷新 */
    private static final long WINDOW_TICKS = 100L;
    private static final Map<UUID, Long> MARKED = new HashMap<>();
    private static final Map<UUID, Float> FACTORS = new HashMap<>();

    private SoulHealSuppression() {
    }

    public static void mark(LivingEntity entity, float factor) {
        if (entity.level().isClientSide) return;
        UUID id = entity.getUUID();
        long now = entity.level().getGameTime();
        MARKED.put(id, now);
        FACTORS.put(id, factor);
        if (MARKED.size() > 256) {
            long cutoff = now - WINDOW_TICKS * 2L;
            Iterator<Map.Entry<UUID, Long>> it = MARKED.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Long> e = it.next();
                if (e.getValue() < cutoff) {
                    it.remove();
                    FACTORS.remove(e.getKey());
                }
            }
        }
    }

    /** 返回当前压制系数（1.0 = 不压制，0.5 = 治疗减半） */
    public static float getFactor(LivingEntity entity) {
        if (entity.level().isClientSide) return 1.0F;
        UUID id = entity.getUUID();
        Long t = MARKED.get(id);
        if (t == null) return 1.0F;
        long now = entity.level().getGameTime();
        if (now - t > WINDOW_TICKS) {
            MARKED.remove(id);
            FACTORS.remove(id);
            return 1.0F;
        }
        return FACTORS.getOrDefault(id, 1.0F);
    }
}
