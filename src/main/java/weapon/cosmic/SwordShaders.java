package weapon.cosmic;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RegisterShadersEvent;

public class SwordShaders {

    public static final float[] COSMIC_UVS = new float[40];
    public static TextureAtlasSprite[] COSMIC_SPRITES = new TextureAtlasSprite[10];

    public static ShaderInstance COSMIC_SHADER;
    public static Uniform cosmicTime;
    public static Uniform cosmicYaw;
    public static Uniform cosmicPitch;
    public static Uniform cosmicExternalScale;
    public static Uniform cosmicOpacity;
    public static Uniform cosmicUVs;

    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            ResourceLocation.fromNamespaceAndPath("malice_enhanced", "cosmic"),
                            DefaultVertexFormat.BLOCK),
                    shader -> {
                        COSMIC_SHADER = shader;
                        cosmicTime = COSMIC_SHADER.getUniform("time");
                        cosmicYaw = COSMIC_SHADER.getUniform("yaw");
                        cosmicPitch = COSMIC_SHADER.getUniform("pitch");
                        cosmicExternalScale = COSMIC_SHADER.getUniform("externalScale");
                        cosmicOpacity = COSMIC_SHADER.getUniform("opacity");
                        cosmicUVs = COSMIC_SHADER.getUniform("cosmicuvs");
                        System.err.println("[SoulSword] Cosmic shader loaded OK");
                    });
        } catch (Exception e) {
            System.err.println("[SoulSword] Failed to load cosmic shader:");
            e.printStackTrace();
        }
    }
}
