package weapon.client;

import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * 缓存世界渲染实际使用的 view / projection 矩阵和相机位置，供 {@link SoulBowAimOverlay}
 * 把世界坐标投影到屏幕。
 * <p>
 * 直接取渲染管线的矩阵而不自行推算 FOV：投影矩阵用的是帧缓冲宽高比，
 * 自行用 GUI 缩放后的宽高比换算会在非 1 倍 GUI 缩放下产生细微偏移。
 * 实体渲染坐标本身是"相对相机"的，故 view 矩阵只含旋转、不含平移；
 * 投影前需手动减去 {@link #cameraPos()}。
 */
@Mod.EventBusSubscriber(modid = "malice_enhanced", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SoulBowAimMatrices {

    private static Matrix4f view;
    private static Matrix4f projection;
    private static Vec3 cameraPos;

    private SoulBowAimMatrices() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        view = new Matrix4f(event.getPoseStack().last().pose());
        projection = new Matrix4f(event.getProjectionMatrix());
        cameraPos = event.getCamera().getPosition();
    }

    public static Matrix4f view() {
        return view;
    }

    public static Matrix4f projection() {
        return projection;
    }

    /** 当前帧相机世界坐标（用于将实体绝对坐标转为相对坐标） */
    public static Vec3 cameraPos() {
        return cameraPos;
    }
}
