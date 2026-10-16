package wither_title.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import wither_title.WitherTitleHelper;

/**
 * 与未定义凋灵战斗时，把环境雾色渐染成魂蓝。
 *
 * 地狱维度不渲染天空盒，玩家抬头看到的"天"就是雾背景，
 * 因此改 ViewportEvent.ComputeFogColor 即可让基岩层上方整片天穹变蓝，
 * 主世界/末地战斗时同样生效（原版天空纹理不动，但雾与远景会被染色）。
 *
 * 检测方式与 UndefinedWitherBossBar.findUndefinedWither 一致：
 * 客户端每 SCAN_INTERVAL_TICKS 在玩家周围 SCAN_RANGE 格内扫一只
 * 带 undefined_wither 判定的 WitherBoss；不在场时渐变回原色。
 */
@Mod.EventBusSubscriber(modid = "malice_enhanced", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class WitherTitleSkyTint {

    /** 目标魂蓝，与血条流光青蓝 (0.55,0.90,1.00) 同色系但更饱和 */
    private static final float TARGET_R = 0.16F;
    private static final float TARGET_G = 0.52F;
    private static final float TARGET_B = 0.95F;

    /** 检测半径，与 BossBar 的血条贴图查找范围保持一致 */
    private static final double SCAN_RANGE = 128.0D;
    private static final int SCAN_INTERVAL_TICKS = 10;
    /** 每 tick 向目标逼近的比例，约 1 秒完成淡入/淡出 */
    private static final float FADE_RATE = 0.05F;

    /** 当前混色强度 0..1，只有客户端线程读写 */
    private static float blend = 0.0F;
    private static boolean bossNearby = false;

    /** 供穹顶渲染等其它客户端效果共用同一渐变强度（仅客户端线程读取） */
    public static float getBlend() {
        return blend;
    }

    private WitherTitleSkyTint() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            blend = 0.0F;
            bossNearby = false;
            return;
        }
        if (level.getGameTime() % SCAN_INTERVAL_TICKS == 0) {
            AABB area = mc.player.getBoundingBox().inflate(SCAN_RANGE);
            bossNearby = !level.getEntitiesOfClass(WitherBoss.class, area,
                    WitherTitleHelper::isUndefinedWither).isEmpty();
        }
        float target = bossNearby ? 1.0F : 0.0F;
        if (blend != target) {
            blend = Mth.clamp(blend + (target - blend > 0.0F ? FADE_RATE : -FADE_RATE), 0.0F, 1.0F);
        }
    }

    @SubscribeEvent
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        if (blend <= 0.004F) return;
        // 逐帧按部分插值，避免 10 tick 扫描周期造成跳变
        event.setRed(Mth.lerp(blend, event.getRed(), TARGET_R));
        event.setGreen(Mth.lerp(blend, event.getGreen(), TARGET_G));
        event.setBlue(Mth.lerp(blend, event.getBlue(), TARGET_B));
    }
}
