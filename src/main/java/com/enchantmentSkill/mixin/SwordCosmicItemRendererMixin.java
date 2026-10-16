package com.enchantmentSkill.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemModelShaper;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import weapon.cosmic.PerspectiveModel;

@OnlyIn(Dist.CLIENT)
@Mixin(ItemRenderer.class)
public abstract class SwordCosmicItemRendererMixin {

    @Shadow @Final private ItemModelShaper itemModelShaper;
    @Shadow @Final private TextureManager textureManager;

    private static boolean mixinDebugLogged;

    @Inject(method = "render", require = 0,
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", ordinal = 0))
    public void malice_enhanced$onRenderItem(ItemStack stack, ItemDisplayContext context,
            boolean leftHand, PoseStack mStack, MultiBufferSource buffers,
            int packedLight, int packedOverlay, BakedModel modelIn, CallbackInfo ci) {
        if (!mixinDebugLogged) {
            mixinDebugLogged = true;
            System.err.println("[SoulSword] Mixin INJECTED ok, stack=" + stack + " model=" + modelIn.getClass().getName());
        }
        if (modelIn instanceof PerspectiveModel model) {
            mStack.pushPose();
            model.applyTransform(context, mStack, leftHand);
            mStack.translate(-0.5D, -0.5D, -0.5D);
            model.renderItem(stack, context, mStack, buffers, packedLight, packedOverlay,
                    this.itemModelShaper, this.textureManager);
            mStack.popPose();
        }
    }
}
