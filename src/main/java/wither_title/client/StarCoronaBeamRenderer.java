package wither_title.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EnderDragonRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import wither_title.StarCoronaBeamEntity;

/** Renders a thin End Crystal-style beam directly between the two entities. */
public class StarCoronaBeamRenderer extends EntityRenderer<StarCoronaBeamEntity> {
    private static final ResourceLocation BEAM_TEXTURE = EnderDragonRenderer.CRYSTAL_BEAM_LOCATION;
    private static final RenderType BEAM_RENDER_TYPE = RenderType.entityCutoutNoCull(BEAM_TEXTURE);
    private static final float BEAM_RADIUS = 0.14F;

    public StarCoronaBeamRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(StarCoronaBeamEntity beam, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        Entity allay = beam.getAllay();
        Entity owner = beam.getOwner();
        if (allay == null || owner == null) return;

        Vec3 origin = lerp(beam, partialTick);
        Vec3 start = lerp(allay, partialTick).add(0.0D, allay.getBbHeight() * 0.55D, 0.0D)
                .subtract(origin);
        Vec3 end = lerp(owner, partialTick).add(0.0D, owner.getBbHeight() * 0.55D, 0.0D)
                .subtract(origin);
        Vec3 direction = end.subtract(start);
        double length = direction.length();
        if (length < 0.05D) return;

        Vec3 axis = direction.scale(1.0D / length);
        Vec3 side = axis.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() < 1.0E-4D) side = axis.cross(new Vec3(1.0D, 0.0D, 0.0D));
        side = side.normalize().scale(BEAM_RADIUS);

        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        Matrix3f normal = pose.normal();
        VertexConsumer consumer = bufferSource.getBuffer(BEAM_RENDER_TYPE);
        float vStart = -(beam.tickCount + partialTick) * 0.01F;
        float vEnd = (float) length / 32.0F + vStart;
        
        Vec3 previousSide = side;
        Vec3 previousOther = Vec3.ZERO;
        float previousU = 0.0F;
        for (int segment = 1; segment <= 8; segment++) {
            float angle = segment * Mth.TWO_PI / 8.0F;
            Vec3 nextSide = axis.cross(new Vec3(0.0D, 1.0D, 0.0D));
            if (nextSide.lengthSqr() < 1.0E-4D) nextSide = axis.cross(new Vec3(1.0D, 0.0D, 0.0D));
            nextSide = nextSide.normalize().scale((float) Math.cos(angle) * BEAM_RADIUS);
            Vec3 nextOther = axis.cross(nextSide).normalize().scale((float) Math.sin(angle) * BEAM_RADIUS);
            Vec3 previousRing = previousSide.add(previousOther);
            Vec3 nextRing = nextSide.add(nextOther);
            float nextU = segment / 8.0F;

            emit(consumer, matrix, normal, start.add(previousRing), previousU, vStart, packedLight);
            emit(consumer, matrix, normal, end.add(previousRing), previousU, vEnd, packedLight);
            emit(consumer, matrix, normal, end.add(nextRing), nextU, vEnd, packedLight);
            emit(consumer, matrix, normal, start.add(nextRing), nextU, vStart, packedLight);

            previousSide = nextSide;
            previousOther = nextOther;
            previousU = nextU;
        }
    }

    private static void emit(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal,
                             Vec3 position, float u, float v, int packedLight) {
        consumer.vertex(matrix, (float) position.x, (float) position.y, (float) position.z)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(normal, (float) -position.x, (float) -position.y, (float) -position.z)
                .endVertex();
    }

    private static Vec3 lerp(Entity entity, float partialTick) {
        return new Vec3(
                Mth.lerp(partialTick, entity.xOld, entity.getX()),
                Mth.lerp(partialTick, entity.yOld, entity.getY()),
                Mth.lerp(partialTick, entity.zOld, entity.getZ()));
    }

    @Override
    public boolean shouldRender(StarCoronaBeamEntity beam, Frustum frustum,
                                double cameraX, double cameraY, double cameraZ) {
        Entity allay = beam.getAllay();
        Entity owner = beam.getOwner();
        if (allay == null || owner == null) return false;
        AABB line = allay.getBoundingBox().minmax(owner.getBoundingBox()).inflate(BEAM_RADIUS + 0.25D);
        return frustum.isVisible(line);
    }

    @Override
    public ResourceLocation getTextureLocation(StarCoronaBeamEntity entity) {
        return BEAM_TEXTURE;
    }
}
