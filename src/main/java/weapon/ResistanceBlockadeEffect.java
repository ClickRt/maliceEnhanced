package weapon;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 抗性压制（假正面效果）：
 * <p>
 * 显示为正面效果（BENEFICIAL），但实际效果是让目标无法获得抗性提升（Resistance）。
 * 由 SoulSwordEventHandler 中的 onMobEffectApplicable 事件拦截实现。
 * <p>
 * 粒子颜色：无垠长魂之剑蓝（#55AAFF）
 */
public class ResistanceBlockadeEffect extends MobEffect {

    /** 无垠长魂之剑蓝粒子颜色 */
    private static final int SOUL_BLUE = 0x55AAFF;

    public ResistanceBlockadeEffect() {
        // BENEFICIAL = 假正面效果，显示为向上的箭头图标
        super(MobEffectCategory.BENEFICIAL, SOUL_BLUE);
    }

    /**
     * 此效果不需要每 tick 逻辑，因为"免疫抗性提升"由事件监听器实现。
     * 返回 false 表示没有需要每 tick 执行的效果（无属性修饰符、无持续伤害等）。
     */
    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return false;
    }
}