package weapon.client;

import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weapon.IronSpellsCompat;

@Mod.EventBusSubscriber(modid = "malice_enhanced", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SoulSwordBlackHoleClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("SoulSwordBlackHole");

    private SoulSwordBlackHoleClient() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        if (!IronSpellsCompat.isLoaded()) return;
        try {
            Class<?> entityRegistry = Class.forName("io.redspace.ironsspellbooks.registries.EntityRegistry");
            Object holder = entityRegistry.getField("BLACK_HOLE").get(null);
            @SuppressWarnings({"rawtypes", "unchecked"})
            EntityType entityType = (EntityType) holder.getClass().getMethod("get").invoke(holder);
            event.registerEntityRenderer(entityType, SoulSwordBlackHoleRenderer::new);
            LOGGER.info("[SoulSwordBlackHole] custom black hole renderer registered");
        } catch (Exception e) {
            LOGGER.warn("[SoulSwordBlackHole] failed to register black hole renderer: {}", e.toString());
        }
    }
}
