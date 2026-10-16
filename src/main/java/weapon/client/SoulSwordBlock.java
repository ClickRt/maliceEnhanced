package weapon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

public final class SoulSwordBlock {
    public static final HumanoidModel.ArmPose SWORD_BLOCK = HumanoidModel.ArmPose.create(
            "malice_enhanced:soul_sword_block", true, (model, entity, arm) -> {});

    private SoulSwordBlock() {
    }

    public static void renderArm(boolean rightArm, ModelPart arm) {
        arm.xRot = arm.xRot * 0.5F - 0.9424778F;
        arm.yRot = (rightArm ? 1 : -1) * -0.5235988F;
    }

    public static void applyItemArmTransform(PoseStack poseStack, HumanoidArm arm, float equipProcess) {
        int i = arm == HumanoidArm.RIGHT ? 1 : -1;
        poseStack.translate(i * 0.56F, -0.52F + equipProcess * -0.6F, -0.72F);
    }

    public static boolean applyForgeHandTransform(PoseStack poseStack, LocalPlayer player, HumanoidArm arm,
            ItemStack itemInHand, float partialTick, float equipProcess, float swingProcess) {
        if (!player.isUsingItem() || player.getUseItem() != itemInHand) {
            return false;
        }
        applyItemArmTransform(poseStack, arm, equipProcess);
        int horizontal = arm == HumanoidArm.RIGHT ? 1 : -1;
        poseStack.translate(horizontal * -0.14142136F, 0.08F, 0.14142136F);
        poseStack.translate(horizontal * -0.20F, 0.10F, 0.04F);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(horizontal * 8.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(horizontal * 80.0F));
        poseStack.translate(horizontal * 0.02F, 0.10F, 0.04F);
        return true;
    }
}
