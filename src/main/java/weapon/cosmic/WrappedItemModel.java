package weapon.cosmic;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class WrappedItemModel implements BakedModel {

    private static final ItemModelGenerator ITEM_MODEL_GENERATOR = new ItemModelGenerator();
    private static final FaceBakery FACE_BAKERY = new FaceBakery();

    protected final BakedModel wrapped;
    protected final ItemOverrides overrideList;
    protected boolean cosmic;
    @Nullable protected LivingEntity entity;
    @Nullable protected ClientLevel world;

    public WrappedItemModel(BakedModel wrapped) {
        this.wrapped = wrapped;
        this.overrideList = new ItemOverrides() {
            @Override
            public BakedModel resolve(@NotNull BakedModel originalModel, @NotNull ItemStack stack,
                                       @Nullable ClientLevel world, @Nullable LivingEntity entity, int seed) {
                WrappedItemModel.this.entity = entity;
                WrappedItemModel.this.world = world != null ? world : (entity != null ? (ClientLevel) entity.level() : null);
                if (WrappedItemModel.this.cosmic) {
                    return WrappedItemModel.this.wrapped.getOverrides().resolve(originalModel, stack, world, entity, seed);
                }
                return originalModel;
            }
        };
    }

    public static List<BakedQuad> bakeItem(List<TextureAtlasSprite> sprites) {
        LinkedList<BakedQuad> quads = new LinkedList<>();
        for (TextureAtlasSprite sprite : sprites) {
            List<BlockElement> unbaked = ITEM_MODEL_GENERATOR.processFrames(0, "layer0", sprite.contents());
            for (BlockElement element : unbaked) {
                for (Map.Entry<Direction, BlockElementFace> entry : element.faces.entrySet()) {
                    quads.add(FACE_BAKERY.bakeQuad(element.from, element.to, entry.getValue(),
                            sprite, entry.getKey(), BlockModelRotation.X0_Y0,
                            element.rotation, element.shade,
                            ResourceLocation.fromNamespaceAndPath("malice_enhanced", "dynamic")));
                }
            }
        }
        return quads;
    }

    protected void renderWrapped(ItemStack stack, PoseStack pStack, MultiBufferSource buffers,
                                  int packedLight, int packedOverlay, boolean fabulous) {
        BakedModel model = this.wrapped.getOverrides().resolve(this.wrapped, stack, this.world, this.entity, 0);
        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        for (BakedModel bakedModel : model.getRenderPasses(stack, fabulous)) {
            for (RenderType rendertype : bakedModel.getRenderTypes(stack, fabulous)) {
                itemRenderer.renderModelLists(bakedModel, stack, packedLight, packedOverlay, pStack,
                        buffers.getBuffer(rendertype));
            }
        }
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

    @Override public @NotNull ItemOverrides getOverrides() { return this.overrideList; }
    @Override public boolean useAmbientOcclusion() { return this.wrapped.useAmbientOcclusion(); }
    @Override public boolean isGui3d() { return this.wrapped.isGui3d(); }
    @Override public boolean usesBlockLight() { return this.wrapped.usesBlockLight(); }
    @Override public boolean isCustomRenderer() { return false; }
    @Override public @NotNull TextureAtlasSprite getParticleIcon() { return this.wrapped.getParticleIcon(); }
    @Override public @NotNull TextureAtlasSprite getParticleIcon(@NotNull ModelData data) { return this.wrapped.getParticleIcon(data); }
    @Override public @NotNull ItemTransforms getTransforms() { return this.wrapped.getTransforms(); }
}
