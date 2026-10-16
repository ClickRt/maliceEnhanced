package wither_title.client;

import net.minecraft.client.model.AllayModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.animal.allay.Allay;
import weapon.starcrystal.StarCoronaServantAllay;

/**
 * 悦灵仆从模型：沿用原版模型，但在服务端同步的使用物品状态下采用弓箭姿势。
 */
public class StarCoronaServantAllayModel extends AllayModel {
    private static final float HALF_PI = (float) (Math.PI / 2.0D);

    private final ModelPart rightArm;
    private final ModelPart leftArm;

    public StarCoronaServantAllayModel(ModelPart root) {
        super(root);
        ModelPart body = root().getChild("body");
        this.rightArm = body.getChild("right_arm");
        this.leftArm = body.getChild("left_arm");
    }

    @Override
    public void setupAnim(Allay entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        if (!(entity instanceof StarCoronaServantAllay servant) || !servant.isUsingItem()) return;

        float headYaw = netHeadYaw * ((float) Math.PI / 180.0F);
        float headPitchRadians = headPitch * ((float) Math.PI / 180.0F);

        // Same arm arrangement as HumanoidModel's BOW_AND_ARROW pose.
        this.rightArm.xRot = -HALF_PI + headPitchRadians;
        this.leftArm.xRot = -HALF_PI + headPitchRadians;
        this.rightArm.yRot = -0.1F + headYaw;
        this.leftArm.yRot = 0.1F + headYaw;
        this.rightArm.zRot = 0.0F;
        this.leftArm.zRot = 0.0F;
    }
}
