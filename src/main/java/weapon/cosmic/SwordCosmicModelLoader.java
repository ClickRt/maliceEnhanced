package weapon.cosmic;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;

import java.util.List;
import java.util.function.Function;

public class SwordCosmicModelLoader extends BaseModelLoader<SwordCosmicModelLoader.CosmicGeometry> {
    public static final SwordCosmicModelLoader INSTANCE = new SwordCosmicModelLoader();

    @Override
    public CosmicGeometry read(JsonObject modelContents, JsonDeserializationContext deserializationContext)
            throws JsonParseException {
        List<ResourceLocation> masks = getMasks(modelContents, "cosmic");
        BlockModel baseModel = deserializationContext.deserialize(
                clear(modelContents, "cosmic"), BlockModel.class);
        return new CosmicGeometry(baseModel, masks);
    }

    public static class CosmicGeometry extends BaseGeometry<CosmicGeometry> {
        private final List<ResourceLocation> maskTextures;

        public CosmicGeometry(BlockModel baseModel, List<ResourceLocation> maskTextures) {
            super(baseModel);
            this.maskTextures = maskTextures;
        }

        @Override
        public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
                               Function<Material, TextureAtlasSprite> spriteGetter,
                               ModelState modelState, ItemOverrides overrides,
                               ResourceLocation modelLocation) {
            BakedModel baseBaked = this.baseModel.bake(baker, this.baseModel, spriteGetter,
                    modelState, modelLocation, true);
            return new SwordCosmicBakedModel(baseBaked, maskTextures);
        }
    }
}
