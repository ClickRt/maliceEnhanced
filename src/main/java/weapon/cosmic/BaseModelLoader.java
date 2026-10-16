package weapon.cosmic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraftforge.client.model.geometry.IGeometryLoader;
import net.minecraftforge.client.model.geometry.IUnbakedGeometry;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseModelLoader<T extends IUnbakedGeometry<T>> implements IGeometryLoader<T> {

    protected JsonObject clear(JsonObject modelContents, String... types) {
        JsonObject clean = modelContents.deepCopy();
        clean.remove("loader");
        for (String type : types) {
            clean.remove(type);
        }
        return clean;
    }

    protected List<ResourceLocation> getMasks(JsonObject modelContents, String type) {
        JsonObject cosmic = modelContents.getAsJsonObject(type);
        if (cosmic == null) {
            throw new IllegalStateException("Missing " + type + " object.");
        }
        List<ResourceLocation> maskTexture = new ArrayList<>();
        if (cosmic.has("mask") && cosmic.get("mask").isJsonArray()) {
            JsonArray masks = cosmic.getAsJsonArray("mask");
            for (int i = 0; i < masks.size(); i++) {
                maskTexture.add(new ResourceLocation(masks.get(i).getAsString()));
            }
        } else {
            maskTexture.add(new ResourceLocation(GsonHelper.getAsString(cosmic, "mask")));
        }
        return maskTexture;
    }
}
