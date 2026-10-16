package wither_title.client;

import net.minecraft.world.entity.EntityType;
import net.minecraft.client.renderer.entity.WitherSkeletonRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import wither_title.WitherTitleEntities;

@Mod.EventBusSubscriber(modid = "malice_enhanced", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WitherTitleClient {
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(WitherTitleEntities.GRAVITY_SINGULARITY.get(), EmptyEntityRenderer::new);
        event.registerEntityRenderer(WitherTitleEntities.BALL_LIGHTNING.get(), EmptyEntityRenderer::new);
        event.registerEntityRenderer(WitherTitleEntities.SUMMON_UNDEFINED_WITHER.get(), EmptyEntityRenderer::new);
        event.registerEntityRenderer(WitherTitleEntities.STAR_CORONA_BEAM.get(), StarCoronaBeamRenderer::new);
        // 仆从复用原版凋灵骷髅渲染器（与改造前视觉一致）
        event.registerEntityRenderer(WitherTitleEntities.UNDEFINED_WITHER_SERVANT.get(), WitherSkeletonRenderer::new);
        // 悦灵仆从：外观同原版悦灵，但手持弓缩小渲染
        event.registerEntityRenderer(WitherTitleEntities.STAR_CORONA_SERVANT_ALLAY.get(), StarCoronaServantAllayRenderer::new);
        // 覆盖原版凋灵渲染器：未定义凋灵用幽蓝护甲/无敌期贴图，其余凋灵行为不变
        event.registerEntityRenderer(EntityType.WITHER, UndefinedWitherRenderer::new);
    }

    /** 注册身后竖直神环所用的平面部件模型层。 */
    @SubscribeEvent
    public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(SoulHaloModel.LAYER, SoulHaloModel::createLayer);
    }
}
