package wither_title.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * 与未定义凋灵战斗时在天穹绘制缓旋的幽蓝六边形穹顶。
 *
 * 不使用事件自带的 pose：Sodium/Iris 会把相机平移写进去，
 * 相对坐标一乘就会钉在世界原点。这里把 ModelView 置单位矩阵，
 * 只用摄像机旋转，顶点为方向 × 半径。
 * 有天空的维度走 AFTER_SKY（画在地形后面）；
 * 地狱等无天空维度走 AFTER_WEATHER 并关深度，避免被基岩顶盖住。
 */
@Mod.EventBusSubscriber(modid = "malice_enhanced", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class WitherTitleHexDome {

    private static final float DOME_RADIUS = 100.0F;
    private static final float HEX_ANGULAR_DEG = 3.6F;
    private static final float SPIN_SPEED = 0.02F;
    private static final float BASE_ALPHA = 0.32F;

    private static final Vec3 WORLD_UP = new Vec3(0.0D, 1.0D, 0.0D);

    private static final float[] HEX_AZIMUTH;
    private static final float[] HEX_ELEVATION;
    private static final float[] HEX_SCALE;
    private static final float[] HEX_PHASE;

    static {
        java.util.List<float[]> cells = new java.util.ArrayList<>();
        RandomSource random = RandomSource.create(20260924L);
        float ringStep = Mth.DEG_TO_RAD * 6.5F;
        float hexAng = HEX_ANGULAR_DEG * Mth.DEG_TO_RAD;
        int ring = 0;
        for (float el = Mth.DEG_TO_RAD * 8.0F; el < Mth.DEG_TO_RAD * 84.0F; el += ringStep, ring++) {
            float ringRadius = Mth.cos(el);
            float spacing = (2.0F * hexAng * 1.06F) / Math.max(0.25F, ringRadius);
            int count = Math.max(6, (int) (Mth.TWO_PI / spacing));
            float offset = (ring % 2) * spacing * 0.5F;
            for (int i = 0; i < count; i++) {
                float az = offset + i * Mth.TWO_PI / count;
                cells.add(new float[]{az % Mth.TWO_PI, el,
                        0.82F + random.nextFloat() * 0.36F,
                        random.nextFloat() * Mth.TWO_PI});
            }
        }
        int n = cells.size();
        HEX_AZIMUTH = new float[n];
        HEX_ELEVATION = new float[n];
        HEX_SCALE = new float[n];
        HEX_PHASE = new float[n];
        for (int i = 0; i < n; i++) {
            float[] c = cells.get(i);
            HEX_AZIMUTH[i] = c[0];
            HEX_ELEVATION[i] = c[1];
            HEX_SCALE[i] = c[2];
            HEX_PHASE[i] = c[3];
        }
    }

    private WitherTitleHexDome() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        float blend = WitherTitleSkyTint.getBlend();
        if (blend <= 0.02F) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        boolean noSky = mc.level.effects().skyType() == DimensionSpecialEffects.SkyType.NONE;
        RenderLevelStageEvent.Stage stage = event.getStage();
        if (noSky) {
            if (stage != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
        } else if (stage != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }

        float time = (mc.level.getGameTime() + event.getPartialTick()) / 20.0F;
        float spinYaw = time * SPIN_SPEED;
        Camera camera = event.getCamera();

        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        RenderSystem.applyModelViewMatrix();

        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));
        poseStack.mulPose(Axis.YP.rotationDegrees(camera.getYRot() + 180.0F));
        Matrix4f pose = poseStack.last().pose();

        float fogStart = RenderSystem.getShaderFogStart();
        float fogEnd = RenderSystem.getShaderFogEnd();
        FogShape fogShape = RenderSystem.getShaderFogShape();
        FogRenderer.setupNoFog();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        // 有天空：AFTER_SKY 在地形之前，关深度当天空罩。
        // 地狱：AFTER_WEATHER 在地形之后，必须开深度，否则会透块。
        if (noSky) {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);
        } else {
            RenderSystem.disableDepthTest();
        }
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < HEX_AZIMUTH.length; i++) {
            drawHexagon(builder, pose, time, spinYaw, blend, i);
        }
        tesselator.end();

        RenderSystem.setShaderFogStart(fogStart);
        RenderSystem.setShaderFogEnd(fogEnd);
        RenderSystem.setShaderFogShape(fogShape);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();

        modelView.popPose();
        RenderSystem.applyModelViewMatrix();
    }

    private static void drawHexagon(BufferBuilder builder, Matrix4f pose,
                                    float time, float yaw, float blend, int i) {
        float el = HEX_ELEVATION[i];
        float az = HEX_AZIMUTH[i] + yaw;
        float cosEl = Mth.cos(el);
        Vec3 dir = new Vec3(cosEl * Mth.sin(az), Mth.sin(el), cosEl * Mth.cos(az));
        Vec3 t1 = dir.cross(WORLD_UP).normalize();
        Vec3 t2 = dir.cross(t1).normalize();

        float pulse = 0.55F + 0.45F * Mth.sin(time * 1.6F + HEX_PHASE[i]);
        float wave = 0.72F + 0.28F * Mth.sin(az * 3.0F - time * 0.9F);
        float horizonFade = Mth.clamp(Mth.sin(el) * 1.6F, 0.3F, 1.0F);
        float alpha = BASE_ALPHA * blend * pulse * wave * horizonFade;
        if (alpha <= 0.004F) return;

        float tint = 0.5F + 0.5F * Mth.sin(time * 0.5F + HEX_PHASE[i]);
        float r = Mth.lerp(tint, 0.07F, 0.30F);
        float g = Mth.lerp(tint, 0.22F, 0.65F);
        float b = Mth.lerp(tint, 0.55F, 1.00F);

        float hr = HEX_ANGULAR_DEG * Mth.DEG_TO_RAD * HEX_SCALE[i];
        float sinH = Mth.sin(hr);
        float cosH = Mth.cos(hr);
        float spin = HEX_PHASE[i] + time * 0.1F;
        Vec3 center = dir.scale(DOME_RADIUS);
        for (int k = 0; k < 6; k++) {
            float a0 = spin + k * Mth.TWO_PI / 6.0F;
            float a1 = spin + (k + 1) * Mth.TWO_PI / 6.0F;
            Vec3 v0 = vertexOnSphere(dir, t1, t2, cosH, sinH, a0);
            Vec3 v1 = vertexOnSphere(dir, t1, t2, cosH, sinH, a1);
            builder.vertex(pose, (float) center.x, (float) center.y, (float) center.z)
                    .color(r, g, b, alpha).endVertex();
            builder.vertex(pose, (float) v0.x, (float) v0.y, (float) v0.z)
                    .color(r, g, b, alpha).endVertex();
            builder.vertex(pose, (float) v1.x, (float) v1.y, (float) v1.z)
                    .color(r, g, b, alpha).endVertex();
        }
    }

    private static Vec3 vertexOnSphere(Vec3 dir, Vec3 t1, Vec3 t2, float cosH, float sinH, float angle) {
        Vec3 p = dir.scale(cosH)
                .add(t1.scale(sinH * Mth.cos(angle)))
                .add(t2.scale(sinH * Mth.sin(angle)));
        return p.normalize().scale(DOME_RADIUS);
    }
}
