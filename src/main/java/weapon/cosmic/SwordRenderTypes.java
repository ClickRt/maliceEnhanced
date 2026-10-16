package weapon.cosmic;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

public class SwordRenderTypes {

    private static final RenderStateShard.TransparencyStateShard COSMIC_TRANSPARENCY =
            new RenderStateShard.TransparencyStateShard("cosmic_transparency",
                    () -> {
                        RenderSystem.enableBlend();
                        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
                    },
                    () -> {
                        RenderSystem.disableBlend();
                        RenderSystem.defaultBlendFunc();
                    });

    private static final RenderStateShard.LightmapStateShard COSMIC_LIGHTMAP =
            new RenderStateShard.LightmapStateShard(true);

    private static final RenderStateShard.DepthTestStateShard COSMIC_DEPTH =
            new RenderStateShard.DepthTestStateShard("==", 514);

    private static final RenderStateShard.TextureStateShard COSMIC_TEXTURE =
            new RenderStateShard.TextureStateShard(InventoryMenu.BLOCK_ATLAS, false, false);

    public static final RenderType COSMIC = RenderType.create(
            ResourceLocation.fromNamespaceAndPath("malice_enhanced", "cosmic").toString(),
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            2097152,
            true,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(() -> SwordShaders.COSMIC_SHADER))
                    .setDepthTestState(COSMIC_DEPTH)
                    .setLightmapState(COSMIC_LIGHTMAP)
                    .setTransparencyState(COSMIC_TRANSPARENCY)
                    .setTextureState(COSMIC_TEXTURE)
                    .createCompositeState(true)
    );
}
