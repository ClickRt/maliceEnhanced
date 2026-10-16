package weapon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.NotNull;

public enum SoulSwordClientExtensions implements IClientItemExtensions {
    INSTANCE;

    @Override
    public boolean applyForgeHandTransform(PoseStack poseStack, LocalPlayer player, HumanoidArm arm,
            ItemStack itemInHand, float partialTick, float equipProcess, float swingProcess) {
        return SoulSwordBlock.applyForgeHandTransform(poseStack, player, arm, itemInHand,
                partialTick, equipProcess, swingProcess);
    }

    @Override
    public @NotNull HumanoidModel.ArmPose getArmPose(LivingEntity entityLiving, InteractionHand hand,
            ItemStack itemStack) {
        return entityLiving.isUsingItem() && entityLiving.getUseItem() == itemStack
                ? SoulSwordBlock.SWORD_BLOCK
                : HumanoidModel.ArmPose.ITEM;
    }
}
