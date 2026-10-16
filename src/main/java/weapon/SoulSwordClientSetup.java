package weapon;

import net.minecraft.client.renderer.item.ItemProperties;
import weapon.client.SoulBowArrowRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import weapon.starcrystal.StarCoronaCrystalScreen;
import weapon.cosmic.SwordCosmicModelLoader;
import weapon.cosmic.SwordShaders;

@Mod.EventBusSubscriber(modid = "malice_enhanced", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class SoulSwordClientSetup {
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(WeaponEntities.SOUL_BOW_ARROW.get(), SoulBowArrowRenderer::new);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(WeaponItems.SOUL_SWORD.get(),
                    ResourceLocation.withDefaultNamespace("blocking"),
                    (stack, level, entity, seed) ->
                            entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F);
            ItemProperties.register(WeaponItems.SOUL_BOW.get(),
                    ResourceLocation.withDefaultNamespace("pull"),
                    (stack, level, entity, seed) -> {
                        if (entity == null) {
                            return 0.0F;
                        }
                        return entity.getUseItem() != stack
                                ? 0.0F
                                : (stack.getUseDuration() - entity.getUseItemRemainingTicks()) / 20.0F;
                    });
            ItemProperties.register(WeaponItems.SOUL_BOW.get(),
                    ResourceLocation.withDefaultNamespace("pulling"),
                    (stack, level, entity, seed) ->
                            entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F);
            MenuScreens.register(WeaponRegistries.STAR_CORONA_CRYSTAL.get(), StarCoronaCrystalScreen::new);
        });
    }

    @SubscribeEvent
    public static void onRegisterGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register("cosmic", SwordCosmicModelLoader.INSTANCE);
        System.err.println("[SoulSword] Registered cosmic model loader");
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        SwordShaders.onRegisterShaders(event);
    }

    @SubscribeEvent
    public static void onTextureStitch(TextureStitchEvent event) {
        for (int i = 0; i < SwordShaders.COSMIC_SPRITES.length; i++) {
            SwordShaders.COSMIC_SPRITES[i] = event.getAtlas().getSprite(
                    ResourceLocation.fromNamespaceAndPath("malice_enhanced", "misc/cosmic/cosmic_" + i));
            SwordShaders.COSMIC_UVS[i * 4] = SwordShaders.COSMIC_SPRITES[i].getU0();
            SwordShaders.COSMIC_UVS[i * 4 + 1] = SwordShaders.COSMIC_SPRITES[i].getV0();
            SwordShaders.COSMIC_UVS[i * 4 + 2] = SwordShaders.COSMIC_SPRITES[i].getU1();
            SwordShaders.COSMIC_UVS[i * 4 + 3] = SwordShaders.COSMIC_SPRITES[i].getV1();
        }
        System.err.println("[SoulSword] Texture stitched, cosmic sprite[0]=" + SwordShaders.COSMIC_SPRITES[0].contents().name());
    }
}
