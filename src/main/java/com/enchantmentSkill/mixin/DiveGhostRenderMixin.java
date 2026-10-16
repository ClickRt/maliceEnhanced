package com.enchantmentSkill.mixin;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wither_title.WitherTitleHelper;

@Mixin(LivingEntityRenderer.class)
public abstract class DiveGhostRenderMixin<T extends LivingEntity> {

    @Unique
    private static final ThreadLocal<LivingEntity> malice$rendering = new ThreadLocal<>();

    @Shadow
    public abstract ResourceLocation getTextureLocation(T entity);

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"))
    private void malice$beginGhost(T entity, float yaw, float partial, com.mojang.blaze3d.vertex.PoseStack pose,
                                   net.minecraft.client.renderer.MultiBufferSource buffers, int light, CallbackInfo ci) {
        malice$rendering.set(entity);
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN"))
    private void malice$endGhost(T entity, float yaw, float partial, com.mojang.blaze3d.vertex.PoseStack pose,
                                 net.minecraft.client.renderer.MultiBufferSource buffers, int light, CallbackInfo ci) {
        malice$rendering.remove();
    }

    @Inject(method = "getRenderType", at = @At("RETURN"), cancellable = true)
    private void malice$ghostRenderType(T entity, boolean bodyVisible, boolean translucent, boolean glowing,
                                        CallbackInfoReturnable<RenderType> cir) {
        if (!(entity instanceof WitherBoss wither) || !WitherTitleHelper.isDiveGhost(wither)) return;
        cir.setReturnValue(RenderType.entityTranslucent(this.getTextureLocation(entity)));
    }

    @Redirect(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V"))
    private void malice$ghostAlpha(net.minecraft.client.model.EntityModel<?> model,
                                   com.mojang.blaze3d.vertex.PoseStack pose,
                                   com.mojang.blaze3d.vertex.VertexConsumer buffer,
                                   int packedLight, int packedOverlay,
                                   float red, float green, float blue, float alpha) {
        LivingEntity entity = malice$rendering.get();
        if (entity instanceof WitherBoss wither && WitherTitleHelper.isDiveGhost(wither)) {
            alpha = 0.1F;
        }
        model.renderToBuffer(pose, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
