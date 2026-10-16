package weapon.cosmic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.ItemModelShaper;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public interface PerspectiveModel extends BakedModel {

    void renderItem(ItemStack stack, ItemDisplayContext ctx, PoseStack mStack,
                    MultiBufferSource source, int packedLight, int packedOverlay,
                    ItemModelShaper itemModelShaper, TextureManager textureManager);

    @Override
    default boolean isCustomRenderer() {
        return true;
    }

    @Override
    default @NotNull BakedModel applyTransform(@NotNull ItemDisplayContext context,
                                                @NotNull PoseStack pStack, boolean leftFlip) {
        if (this.getTransforms().hasTransform(context)) {
            var transform = this.getTransforms().getTransform(context);
            pStack.translate(transform.translation.x(), transform.translation.y(), transform.translation.z());
            pStack.mulPose(Axis.XP.rotationDegrees(transform.rotation.x()));
            pStack.mulPose(Axis.YP.rotationDegrees(transform.rotation.y()));
            pStack.mulPose(Axis.ZP.rotationDegrees(transform.rotation.z()));
            pStack.scale(transform.scale.x(), transform.scale.y(), transform.scale.z());
            if (leftFlip) {
                pStack.mulPose(Axis.YN.rotationDegrees(180));
            }
        }
        return this;
    }
}
