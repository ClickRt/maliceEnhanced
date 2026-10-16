package com.enchantmentSkill.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LightningBoltRenderer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LightningBolt;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wither_title.WitherTitleHelper;

@Mixin(LightningBoltRenderer.class)
public abstract class LightningBoltRendererMixin {

    @Inject(method = "render(Lnet/minecraft/world/entity/LightningBolt;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true)
    private void malice$tintUndefinedLightning(LightningBolt bolt, float yaw, float partialTick, PoseStack pose,
                                               MultiBufferSource buffers, int light, CallbackInfo ci) {
        int color = WitherTitleHelper.getLightningColor(bolt);
        if (color == 0) return;
        ci.cancel();
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        renderTinted(bolt, pose, buffers, r, g, b);
    }

    private static void renderTinted(LightningBolt bolt, PoseStack pose, MultiBufferSource buffers,
                                     float r, float g, float b) {
        float[] xs = new float[8];
        float[] zs = new float[8];
        float x = 0.0F;
        float z = 0.0F;
        RandomSource random = RandomSource.create(bolt.seed);
        for (int i = 7; i >= 0; --i) {
            xs[i] = x;
            zs[i] = z;
            x += random.nextInt(11) - 5;
            z += random.nextInt(11) - 5;
        }
        VertexConsumer consumer = buffers.getBuffer(RenderType.lightning());
        Matrix4f matrix = pose.last().pose();
        for (int pass = 0; pass < 4; ++pass) {
            RandomSource branch = RandomSource.create(bolt.seed);
            for (int layer = 0; layer < 3; ++layer) {
                int top = 7;
                int bottom = 0;
                if (layer > 0) {
                    top = 7 - layer;
                    bottom = top - 2;
                }
                float x1 = xs[top] - x;
                float z1 = zs[top] - z;
                for (int y = top; y >= bottom; --y) {
                    float x0 = x1;
                    float z0 = z1;
                    if (layer == 0) {
                        x1 += branch.nextInt(11) - 5;
                        z1 += branch.nextInt(11) - 5;
                    } else {
                        x1 += branch.nextInt(31) - 15;
                        z1 += branch.nextInt(31) - 15;
                    }
                    float w0 = 0.1F + pass * 0.2F;
                    float w1 = w0;
                    if (layer == 0) {
                        w0 *= y * 0.1F + 1.0F;
                        w1 *= (y - 1.0F) * 0.1F + 1.0F;
                    }
                    quad(matrix, consumer, x1, z1, y, x0, z0, r, g, b, w0, w1, false, false, true, false);
                    quad(matrix, consumer, x1, z1, y, x0, z0, r, g, b, w0, w1, true, false, true, true);
                    quad(matrix, consumer, x1, z1, y, x0, z0, r, g, b, w0, w1, true, true, false, true);
                    quad(matrix, consumer, x1, z1, y, x0, z0, r, g, b, w0, w1, false, true, false, false);
                }
            }
        }
    }

    private static void quad(Matrix4f matrix, VertexConsumer consumer, float x1, float z1, int y,
                             float x0, float z0, float r, float g, float b, float w0, float w1,
                             boolean a, boolean c, boolean d, boolean e) {
        consumer.vertex(matrix, x1 + (a ? w1 : -w1), y * 16.0F, z1 + (c ? w1 : -w1)).color(r, g, b, 0.3F).endVertex();
        consumer.vertex(matrix, x0 + (a ? w0 : -w0), (y + 1) * 16.0F, z0 + (c ? w0 : -w0)).color(r, g, b, 0.3F).endVertex();
        consumer.vertex(matrix, x0 + (d ? w0 : -w0), (y + 1) * 16.0F, z0 + (e ? w0 : -w0)).color(r, g, b, 0.3F).endVertex();
        consumer.vertex(matrix, x1 + (d ? w1 : -w1), y * 16.0F, z1 + (e ? w1 : -w1)).color(r, g, b, 0.3F).endVertex();
    }
}
