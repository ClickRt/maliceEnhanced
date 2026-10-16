package com.enchantmentSkill.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import weapon.client.SoulSwordBlock;

@Mixin(value = HumanoidModel.class, priority = 800)
public abstract class SoulSwordHumanoidModelMixin<T extends LivingEntity> {
    @Shadow
    public HumanoidModel.ArmPose rightArmPose;
    @Shadow
    public HumanoidModel.ArmPose leftArmPose;
    @Shadow
    @Final
    public ModelPart rightArm;
    @Shadow
    @Final
    public ModelPart leftArm;

    @Inject(method = "poseRightArm", at = @At("HEAD"), cancellable = true)
    private void malice_enhanced$poseRightArm(T entity, CallbackInfo ci) {
        if (this.rightArmPose == SoulSwordBlock.SWORD_BLOCK) {
            SoulSwordBlock.renderArm(true, this.rightArm);
            ci.cancel();
        }
    }

    @Inject(method = "poseLeftArm", at = @At("HEAD"), cancellable = true)
    private void malice_enhanced$poseLeftArm(T entity, CallbackInfo ci) {
        if (this.leftArmPose == SoulSwordBlock.SWORD_BLOCK) {
            SoulSwordBlock.renderArm(false, this.leftArm);
            ci.cancel();
        }
    }

    @Inject(method = "setupAttackAnimation", at = @At("HEAD"), cancellable = true)
    private void malice_enhanced$cancelSwingWhileBlocking(T entity, float ageInTicks, CallbackInfo ci) {
        if (entity.getMainArm() == HumanoidArm.RIGHT && this.rightArmPose == SoulSwordBlock.SWORD_BLOCK) {
            ci.cancel();
        }
        if (entity.getMainArm() == HumanoidArm.LEFT && this.leftArmPose == SoulSwordBlock.SWORD_BLOCK) {
            ci.cancel();
        }
    }
}
