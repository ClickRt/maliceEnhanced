package wither_title.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/**
 * 未定义凋灵神环等特效使用的加法发光渲染类型（参考 Goety 的 ModRenderType.wraith）。
 * <p>
 * 特点：EYES 着色器 + 加法混合（SRC_ALPHA, ONE）+ 双面不剔除，
 * 配合满亮度光照可让半透明线条贴图呈现"自发光/泛晕"效果，透明处不贡献颜色。
 */
public final class WitherTitleRenderTypes {

    private WitherTitleRenderTypes() {
    }

    private static final RenderStateShard.ShaderStateShard EYES_SHADER =
            new RenderStateShard.ShaderStateShard(GameRenderer::getRendertypeEyesShader);

    private static final RenderStateShard.TransparencyStateShard ADDITIVE_TRANSPARENCY =
            new RenderStateShard.TransparencyStateShard("soul_halo_additive_transparency", () -> {
                RenderSystem.enableBlend();
                RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            }, () -> {
                RenderSystem.disableBlend();
                RenderSystem.defaultBlendFunc();
            });

    private static final RenderStateShard.CullStateShard NO_CULL =
            new RenderStateShard.CullStateShard(false);

    private static final RenderStateShard.WriteMaskStateShard COLOR_WRITE =
            new RenderStateShard.WriteMaskStateShard(true, false);

    private static final Function<ResourceLocation, RenderType> GLOW = Util.memoize(texture ->
            RenderType.create("malice_enhanced:soul_halo_glow",
                    DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, true, false,
                    RenderType.CompositeState.builder()
                            .setShaderState(EYES_SHADER)
                            .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                            .setTransparencyState(ADDITIVE_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(false)));

    /** 取一张贴图的加法发光渲染类型（同贴图复用同一实例）。 */
    public static RenderType glow(ResourceLocation texture) {
        return GLOW.apply(texture);
    }
}
