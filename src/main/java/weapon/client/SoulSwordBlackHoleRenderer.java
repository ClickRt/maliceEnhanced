package weapon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import weapon.SoulSwordBlackHoleAccessor;

public class SoulSwordBlackHoleRenderer extends EntityRenderer<Entity> {
    private static final ResourceLocation CENTER_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "textures/entity/black_hole/black_hole.png");
    private static final ResourceLocation BEAM_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "textures/entity/black_hole/beam.png");
    private static final ResourceLocation DEFAULT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "textures/entity/icicle_projectile.png");
    private static final float HALF_SQRT_3 = (float) (Math.sqrt(3.0D) / 2.0D);

    public SoulSwordBlackHoleRenderer(EntityRendererProvider.Context pContext) {
        super(pContext);
    }

    @Override
    public void render(Entity entity, float pEntityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource bufferSource, int pPackedLight) {
        poseStack.pushPose();
        poseStack.translate(0, entity.getBoundingBox().getYsize() / 2, 0);

        float entityScale = entity.getBbWidth() * .025f;
        PoseStack.Pose pose = poseStack.last();
        Matrix4f poseMatrix = pose.pose();
        Matrix3f normalMatrix = pose.normal();
        poseStack.scale(.5f * entityScale, .5f * entityScale, .5f * entityScale);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Axis.YP.rotationDegrees(90f));
        poseStack.translate(5, 0, 0);

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(CENTER_TEXTURE));
        consumer.vertex(poseMatrix, 0, -8, -8).color(255, 255, 255, 255).uv(0f, 1f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(normalMatrix, 0f, 1f, 0f).endVertex();
        consumer.vertex(poseMatrix, 0, 8, -8).color(255, 255, 255, 255).uv(0f, 0f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(normalMatrix, 0f, 1f, 0f).endVertex();
        consumer.vertex(poseMatrix, 0, 8, 8).color(255, 255, 255, 255).uv(1f, 0f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(normalMatrix, 0f, 1f, 0f).endVertex();
        consumer.vertex(poseMatrix, 0, -8, 8).color(255, 255, 255, 255).uv(1f, 1f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(normalMatrix, 0f, 1f, 0f).endVertex();
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.translate(0, entity.getBoundingBox().getYsize() / 2, 0);
        float animationProgress = (entity.tickCount + partialTicks) / 200.0F;
        float fadeProgress = .5f;
        int colorOverride = 0;
        if (entity instanceof SoulSwordBlackHoleAccessor accessor) {
            try {
                colorOverride = accessor.soulSword$getColor();
            } catch (Throwable ignored) {
            }
        }
        RandomSource randomSource = RandomSource.create(432L);
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.energySwirl(BEAM_TEXTURE, 0, 0));

        float segments = Math.min(animationProgress, .8f);
        for (int i = 0; (float) i < (segments + segments * segments) / 2.0F * 60.0F; ++i) {
            poseStack.mulPose(Axis.XP.rotationDegrees(randomSource.nextFloat() * 360.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(randomSource.nextFloat() * 360.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(randomSource.nextFloat() * 360.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(randomSource.nextFloat() * 360.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(randomSource.nextFloat() * 360.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(randomSource.nextFloat() * 360.0F + animationProgress * 90.0F));
            float size1 = (randomSource.nextFloat() * 10.0F + 5.0F + fadeProgress * 5.0F) * entityScale * .4f;
            Matrix4f matrix = poseStack.last().pose();
            Matrix3f normalMatrix2 = poseStack.last().normal();
            drawTriangle(vertexConsumer, matrix, normalMatrix2, size1, colorOverride);
        }

        poseStack.popPose();
        super.render(entity, pEntityYaw, partialTicks, poseStack, bufferSource, pPackedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(Entity pEntity) {
        return DEFAULT_TEXTURE;
    }

    private static void drawTriangle(VertexConsumer consumer, Matrix4f poseMatrix, Matrix3f normalMatrix,
                                     float size, int colorOverride) {
        int r = 255;
        int g = 0;
        int b = 255;
        if (colorOverride != 0) {
            r = (colorOverride >> 16) & 0xFF;
            g = (colorOverride >> 8) & 0xFF;
            b = colorOverride & 0xFF;
        }
        consumer.vertex(poseMatrix, 0, 0, 0).color(r, g, b, 255).uv(0f, 1f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(normalMatrix, 0f, 1f, 0f).endVertex();
        consumer.vertex(poseMatrix, 0, 3 * size, -1 * size).color(0, 0, 0, 0).uv(0f, 0f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(normalMatrix, 0f, 1f, 0f).endVertex();
        consumer.vertex(poseMatrix, 0, 3 * size, 1 * size).color(0, 0, 0, 0).uv(1f, 0f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(normalMatrix, 0f, 1f, 0f).endVertex();
        consumer.vertex(poseMatrix, 0, 0, 0).color(r, g, b, 255).uv(1f, 1f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(normalMatrix, 0f, 1f, 0f).endVertex();
    }
}
