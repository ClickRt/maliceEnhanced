package weapon.cosmic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ItemModelShaper;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class SwordCosmicBakedModel extends WrappedItemModel implements PerspectiveModel {

    private final List<ResourceLocation> maskSprites;
    private static boolean debugLoggedOnce;

    public SwordCosmicBakedModel(BakedModel wrapped, List<ResourceLocation> maskSprites) {
        super(wrapped);
        this.maskSprites = maskSprites;
        this.cosmic = true;
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable net.minecraft.world.level.block.state.BlockState state,
                                              @Nullable Direction side, @NotNull net.minecraft.util.RandomSource rand,
                                              @NotNull ModelData data, @Nullable RenderType renderType) {
        return this.wrapped.getQuads(state, side, rand, data, renderType);
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable net.minecraft.world.level.block.state.BlockState state,
                                              @Nullable Direction side, @NotNull net.minecraft.util.RandomSource rand) {
        return this.wrapped.getQuads(state, side, rand);
    }

    @Override
    public void renderItem(ItemStack stack, ItemDisplayContext transformType, PoseStack pStack,
                           MultiBufferSource source, int packedLight, int packedOverlay,
                           ItemModelShaper itemModelShaper, TextureManager textureManager) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel world = mc.level;
        if (world == null) return;

        // Pass 1: render base model
        this.renderWrapped(stack, pStack, source, packedLight, packedOverlay, true);

        if (source instanceof MultiBufferSource.BufferSource bs) {
            bs.endBatch();
        }

        if (!debugLoggedOnce) {
            debugLoggedOnce = true;
            System.err.println("[SoulSword] renderItem cosmic pass, shader=" + SwordShaders.COSMIC_SHADER + " cosmicUVs=" + SwordShaders.cosmicUVs);
        }

        // Pass 2: cosmic effect
        if (SwordShaders.COSMIC_SHADER == null) {
            System.err.println("[SoulSword] COSMIC_SHADER is null, bailing");
            return;
        }

        SwordShaders.cosmicTime.set(world.getGameTime() % Integer.MAX_VALUE);

        float yaw = 0.0f;
        float pitch = 0.0f;
        float scale = 1.0f;
        if (transformType == ItemDisplayContext.GUI) {
            scale = 100.0F;
        } else {
            yaw = (float) (mc.player.getYRot() * 2.0f * Math.PI / 360.0);
            pitch = -(float) (mc.player.getXRot() * 2.0f * Math.PI / 360.0);
        }
        SwordShaders.cosmicYaw.set(yaw);
        SwordShaders.cosmicPitch.set(pitch);
        SwordShaders.cosmicExternalScale.set(scale);
        SwordShaders.cosmicOpacity.set(1.0F);

        if (SwordShaders.cosmicUVs != null) {
            SwordShaders.cosmicUVs.set(SwordShaders.COSMIC_UVS);
        }

        VertexConsumer cons = source.getBuffer(SwordRenderTypes.COSMIC);
        List<TextureAtlasSprite> sprites = new ArrayList<>();
        for (ResourceLocation res : maskSprites) {
            sprites.add(mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(res));
        }
        mc.getItemRenderer().renderQuadList(pStack, cons, bakeItem(sprites), stack, packedLight, packedOverlay);
    }
}
