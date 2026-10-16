package wither_title.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.WitherBossModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.WitherBossRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.WitherArmorLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import wither_title.WitherTitleHelper;

/**
 * 未定义凋灵的凋灵渲染器：充能护甲与召唤无敌期贴图换成幽蓝版本，其余凋灵维持原版。
 * <p>
 * 原版的护甲光效层（WitherArmorLayer → EnergySwirlLayer）把纹理写死在类里，
 * 无法按实体区分，所以这里整层替换成同结构、纹理随实体切换的版本；
 * 无敌期贴图则覆写 getTextureLocation。
 */
@OnlyIn(Dist.CLIENT)
public class UndefinedWitherRenderer extends WitherBossRenderer {

    private static final ResourceLocation WITHER_ARMOR =
            new ResourceLocation("textures/entity/wither/wither_armor.png");
    private static final ResourceLocation WITHER_INVULNERABLE =
            new ResourceLocation("textures/entity/wither/wither_invulnerable.png");
    private static final ResourceLocation UNDEFINED_BODY =
            ResourceLocation.fromNamespaceAndPath("malice_enhanced",
                    "textures/entity/wither/wither_undefined.png");
    private static final ResourceLocation UNDEFINED_ARMOR =
            ResourceLocation.fromNamespaceAndPath("malice_enhanced",
                    "textures/entity/wither/wither_armor_undefined.png");
    private static final ResourceLocation UNDEFINED_INVULNERABLE =
            ResourceLocation.fromNamespaceAndPath("malice_enhanced",
                    "textures/entity/wither/wither_invulnerable_undefined.png");

    private final WitherBossModel<WitherBoss> swirlModel;
    private final ModelPart haloRoot;

    public UndefinedWitherRenderer(EntityRendererProvider.Context context) {
        super(context);
        EntityModelSet modelSet = context.getModelSet();
        this.swirlModel = new WitherBossModel<>(modelSet.bakeLayer(ModelLayers.WITHER_ARMOR));
        this.haloRoot = context.bakeLayer(SoulHaloModel.LAYER);
        // 顶掉构造函数里注册的原版护甲层，换成按实体选纹理的同款
        this.layers.removeIf(layer -> layer.getClass() == WitherArmorLayer.class);
        this.addLayer(new ChargeAfterimageLayer(this));
        this.addLayer(new BlueArmorLayer(this));
        // 未定义凋灵身后竖直神环（其他凋灵不渲染）
        this.addLayer(new SoulHaloLayer(this));
    }

    @Override
    protected boolean isBodyVisible(WitherBoss wither) {
        if (WitherTitleHelper.isDiveGhost(wither)) return false;
        return super.isBodyVisible(wither);
    }

    @Override
    protected RenderType getRenderType(WitherBoss wither, boolean bodyVisible, boolean translucent, boolean glowing) {
        if (WitherTitleHelper.isDiveGhost(wither)) {
            return RenderType.entityTranslucent(getTextureLocation(wither));
        }
        return super.getRenderType(wither, bodyVisible, translucent, glowing);
    }

    @Override
    public ResourceLocation getTextureLocation(WitherBoss wither) {
        boolean undefined = WitherTitleHelper.isUndefinedWither(wither);
        int invul = wither.getInvulnerableTicks();
        boolean flashing = invul > 0 && (invul > 80 || invul / 5 % 2 != 1);
        if (flashing) {
            return undefined ? UNDEFINED_INVULNERABLE : WITHER_INVULNERABLE;
        }
        return undefined ? UNDEFINED_BODY : super.getTextureLocation(wither);
    }

    /** 冲撞和平面瞬移留下的半透明身形。 */
    private final class ChargeAfterimageLayer
            extends RenderLayer<WitherBoss, WitherBossModel<WitherBoss>> {

        ChargeAfterimageLayer(RenderLayerParent<WitherBoss, WitherBossModel<WitherBoss>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int packedLight, WitherBoss wither,
                           float limbSwing, float limbSwingAmount, float partialTick,
                           float ageInTicks, float netHeadYaw, float headPitch) {
            if (!WitherTitleHelper.isUndefinedWither(wither)) return;
            var ghosts = WitherChargeAfterimage.ghostsFor(wither.getId());
            if (ghosts.isEmpty()) return;
            WitherBossModel<WitherBoss> model = this.getParentModel();
            VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucent(getTextureLocation(wither)));
            Vec3 renderPos = wither.getPosition(partialTick);
            float bodyYaw = Mth.rotLerp(partialTick, wither.yBodyRotO, wither.yBodyRot);
            float scale = 2.0F;
            int invul = wither.getInvulnerableTicks();
            if (invul > 0) {
                scale -= ((float) invul - partialTick) / 220.0F * 0.5F;
            }
            for (WitherChargeAfterimage.Ghost ghost : ghosts) {
                float fade = 1.0F - ghost.age / (float) WitherChargeAfterimage.LIFE;
                fade *= fade;
                float dx = (float) (ghost.x - renderPos.x);
                float dy = (float) (ghost.y - renderPos.y);
                float dz = (float) (ghost.z - renderPos.z);
                pose.pushPose();
                pose.translate(0.0F, 1.501F, 0.0F);
                pose.scale(-1.0F / scale, -1.0F / scale, 1.0F / scale);
                pose.mulPose(Axis.YP.rotationDegrees(-(180.0F - bodyYaw)));
                pose.translate(dx, dy, dz);
                pose.mulPose(Axis.YP.rotationDegrees(180.0F - ghost.yRot));
                pose.scale(-scale, -scale, scale);
                pose.translate(0.0F, -1.501F, 0.0F);
                model.renderToBuffer(pose, consumer, packedLight, OverlayTexture.NO_OVERLAY,
                        0.35F, 0.75F, 0.95F, 0.35F * fade);
                pose.popPose();
            }
        }
    }

    /** 与原版 WitherArmorLayer 同结构，仅纹理随实体切换 */
    private final class BlueArmorLayer
            extends RenderLayer<WitherBoss, WitherBossModel<WitherBoss>> {

        BlueArmorLayer(RenderLayerParent<WitherBoss, WitherBossModel<WitherBoss>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int packedLight, WitherBoss wither,
                           float limbSwing, float limbSwingAmount, float partialTick,
                           float ageInTicks, float netHeadYaw, float headPitch) {
            if (WitherTitleHelper.isDiveGhost(wither)) return;
            if (!wither.isPowered()) return;

            float f = (float) wither.tickCount + partialTick;
            swirlModel.prepareMobModel(wither, limbSwing, limbSwingAmount, partialTick);
            this.getParentModel().copyPropertiesTo(swirlModel);

            ResourceLocation texture = WitherTitleHelper.isUndefinedWither(wither)
                    ? UNDEFINED_ARMOR : WITHER_ARMOR;
            VertexConsumer consumer = buffers.getBuffer(RenderType.energySwirl(
                    texture, Mth.cos(f * 0.02F) * 3.0F % 1.0F, f * 0.01F % 1.0F));

            swirlModel.setupAnim(wither, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            swirlModel.renderToBuffer(pose, consumer, packedLight, OverlayTexture.NO_OVERLAY,
                    0.5F, 0.5F, 0.5F, 1.0F);
        }
    }

    /**
     * 未定义凋灵身后的竖直神环，摆法和终末环一样：贴在上半身背后的竖直圆盘。
     * <p>
     * 图层 pose 已经过 {@code scale(-1,-1,1)} 和凋灵 2 倍缩放，translate/scale 用世界格，
     * 再除掉 2 倍缩放到 pose 空间。不要再乘 16——那是 ModelPart 内部的事。
     */
    private final class SoulHaloLayer
            extends RenderLayer<WitherBoss, WitherBossModel<WitherBoss>> {

        private static final float WITHER_SCALE = 2.0F;
        /** 神环边长（世界格）。终末环约 1.1；凋灵三头约 3 格宽，用 4 格略大于躯干。 */
        private static final float SIZE_BLOCKS = 4.0F;
        /** 身后距离（世界格）。终末环大约 0.6~1。 */
        private static final float BEHIND_BLOCKS = 1.0F;
        /** 相对头中心的抬升（世界格）。 */
        private static final float LIFT_BLOCKS = 0.25F;
        private static final float SPIN_SPEED = 0.6F;
        private static final float GLOW_ALPHA = 0.9F;

        private final ModelPart halo;

        SoulHaloLayer(RenderLayerParent<WitherBoss, WitherBossModel<WitherBoss>> parent) {
            super(parent);
            this.halo = UndefinedWitherRenderer.this.haloRoot.getChild(SoulHaloModel.HALO_PART);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int packedLight, WitherBoss wither,
                           float limbSwing, float limbSwingAmount, float partialTick,
                           float ageInTicks, float netHeadYaw, float headPitch) {
            if (!WitherTitleHelper.isUndefinedWither(wither)) return;
            if (wither.isRemoved() || wither.isInvisible() || WitherTitleHelper.isDiveGhost(wither)) return;

            float size = SIZE_BLOCKS / WITHER_SCALE;
            float lift = LIFT_BLOCKS / WITHER_SCALE;
            float behind = BEHIND_BLOCKS / WITHER_SCALE;
            float scale = (size * 16.0F) / SoulHaloModel.PLANE_UNITS;
            float spin = ((float) wither.tickCount + partialTick) * SPIN_SPEED;

            pose.pushPose();
            pose.translate(0.0F, -lift, behind);
            pose.scale(scale, scale, scale);
            pose.mulPose(Axis.ZP.rotationDegrees(spin));

            VertexConsumer consumer = buffers.getBuffer(
                    WitherTitleRenderTypes.glow(SoulHaloModel.HALO_TEXTURE));
            this.halo.render(pose, consumer, 0xF000F0, OverlayTexture.NO_OVERLAY,
                    1.0F, 1.0F, 1.0F, GLOW_ALPHA);
            pose.popPose();
        }
    }
}
