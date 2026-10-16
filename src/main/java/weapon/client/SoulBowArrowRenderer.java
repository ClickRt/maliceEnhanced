package weapon.client;

import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import weapon.SoulBowArrow;

public class SoulBowArrowRenderer extends ArrowRenderer<SoulBowArrow> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("malice_enhanced", "textures/entity/soul_bow_arrow.png");

    public SoulBowArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(SoulBowArrow entity) {
        return TEXTURE;
    }
}
