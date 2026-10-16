package wither_title.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.AllayModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.AllayRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 悦灵仆从渲染器：外观沿用原版悦灵，但把 {@link ItemInHandLayer}（手持物渲染层）
 * 换成缩放版，使悦灵手上的协助弓变小且仍留在手上。
 *
 * 关键：不能在整个层的 render() 里 scale（那会以模型原点缩放，把弓挪到头顶），
 * 必须复刻 renderArmWithItem，在 translateToHand 之后、渲染物品之前 scale，
 * 这样弓仍以手部为锚点，只是尺寸缩小。
 */
public class StarCoronaServantAllayRenderer extends AllayRenderer {
    /** 手持物品缩放比例（0.5 = 原大小的一半）。 */
    private static final float HELD_ITEM_SCALE = 0.5F;

    public StarCoronaServantAllayRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new StarCoronaServantAllayModel(context.bakeLayer(ModelLayers.ALLAY));
        this.layers.clear();
        this.addLayer(new ScaledItemInHandLayer(this, context.getItemInHandRenderer(), HELD_ITEM_SCALE));
    }

    /** 复刻原版手持层，仅在渲染前追加一次以手为锚点的缩放。 */
    private static class ScaledItemInHandLayer extends ItemInHandLayer<Allay, AllayModel> {
        private final ItemInHandRenderer renderer;
        private final float scale;

        ScaledItemInHandLayer(RenderLayerParent<Allay, AllayModel> parent,
                              ItemInHandRenderer renderer, float scale) {
            super(parent, renderer);
            this.renderer = renderer;
            this.scale = scale;
        }

        @Override
        protected void renderArmWithItem(LivingEntity entity, ItemStack stack, ItemDisplayContext displayContext,
                                         HumanoidArm arm, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
            if (stack.isEmpty()) return;
            poseStack.pushPose();
            this.getParentModel().translateToHand(arm, poseStack);
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            boolean leftHand = arm == HumanoidArm.LEFT;
            poseStack.translate((leftHand ? -1.0F : 1.0F) / 16.0F, 0.125F, -0.625F);
            poseStack.scale(this.scale, this.scale, this.scale);
            this.renderer.renderItem(entity, stack, displayContext, leftHand, poseStack, buffer, packedLight);
            poseStack.popPose();
        }
    }
}
