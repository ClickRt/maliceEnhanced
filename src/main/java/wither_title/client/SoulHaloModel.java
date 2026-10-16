package wither_title.client;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * 未定义凋灵身后竖直神环所用的"平面部件"。
 * <p>
 * 只有一块 depth=0 的方形面（512x512 模型单位），贴满整张 {@code soul_hat.png}；
 * 渲染时再按需缩放、平移、绕法线自旋。参考 Goety 的 ApostleModel/UnholyHatModel 的 halo 部件做法。
 */
public final class SoulHaloModel {

    /** 神环贴图（已从 2048² 缩到 512²，放在正确的 resources 目录下）。 */
    public static final ResourceLocation HALO_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("malice_enhanced",
                    "textures/entity/wither/soul_hat2.png");

    /** 供 RegisterLayerDefinitions 注册、渲染器 bakeLayer 使用的模型层位置。 */
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath("malice_enhanced", "soul_halo"), "main");

    /** 平面部件在模型空间里的边长（单位），同时是 LayerDefinition 声明的纹理尺寸。 */
    public static final float PLANE_UNITS = 512.0F;

    /** 根部件名。 */
    public static final String HALO_PART = "halo";

    private SoulHaloModel() {
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        float half = PLANE_UNITS / 2.0F;
        // texOffs(0,0) + 512x512 面 + LayerDefinition(512,512) => UV 覆盖 0..1，铺满整张贴图
        root.addOrReplaceChild(HALO_PART,
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-half, -half, 0.0F, PLANE_UNITS, PLANE_UNITS, 0.0F,
                                new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, (int) PLANE_UNITS, (int) PLANE_UNITS);
    }
}
