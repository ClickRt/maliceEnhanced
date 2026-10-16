package wither_title.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import wither_title.WitherTitleHelper;

/**
 * 未定义凋灵自定义 BOSS 血条。
 *
 * 新贴图（v12 造型，256x94 里只占 y30..62）：细长横条，中间开一条镂空血管槽，
 * 血管槽被中心装饰断成左右两段，两端各有一块实心端块。
 * 血管画在框下面，靠框自身的镂空窗口露出来，所以不会盖住中心装饰和端块。
 * 血条画面按 SCALE 缩放，名字不缩放、画在血条上方。
 */
@Mod.EventBusSubscriber(modid = "malice_enhanced", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class UndefinedWitherBossBar {

    private static final ResourceLocation FRAME =
            new ResourceLocation("malice_enhanced", "textures/gui/boss_bar/undefined_wither.png");
    private static final ResourceLocation FILL =
            new ResourceLocation("malice_enhanced", "textures/gui/boss_bar/undefined_wither_fill.png");

    /**
     * 贴图是 2× 导出（512×188），血条本体占 y60..125。
     * 目的：1× 时 2048 宽的设计稿要一次缩到 256，1px 的亮边被采样糊掉；
     * 2× 只缩一半，游戏里再按 SCALE 缩到同一屏幕尺寸，边缘明显利落。
     */
    private static final int TEX_W = 512;
    private static final int TEX_H = 188;

    /**
     * 裁切线：贴图内容只在 y60..125，上下留白不画。
     * oy 已经减掉 CROP_TOP，所以贴图 y=CROP_TOP 正好落在分配位置 top 上。
     */
    private static final int CROP_TOP = 60;
    private static final int CROP_BOTTOM = 126;

    /** 血条画面整体缩放，名字不跟着缩放。SCALE_Y 只用来跟 SCALE_X 一起还原竖向比例 */
    private static final float SCALE_X = 0.45F;
    private static final float SCALE_Y = 0.45F;

    /** 血条整体水平偏移，负数 = 向左。名字跟着血条一起走；0 = 和原版血条一样严格居中 */
    private static final int SHIFT_X = 0;

    /**
     * 名字与血条之间的间距。基准是贴图 y=NAME_BASE_Y 那个最高的装饰尖，
     * 不是贴图裁切顶边——y60..87 那段只有装饰尖角，按它算名字会浮得老高。
     */
    private static final int NAME_GAP = 3;

    /** 字体行高，用来给名字留出占位高度 */
    private static final int NAME_H = 9;

    /**
     * 名字的基线参考高度：中心装饰的尖顶（贴图 y72）。
     * 血条本体上沿在 y88，中心装饰比它高出 16px，名字按本体排会压到装饰尖上。
     */
    private static final int NAME_BASE_Y = 72;

    /** 名字顶边高出 top 的屏幕像素数（名字画在 top 上方，所以是正数） */
    private static final int NAME_RISE =
            NAME_H + NAME_GAP - Math.round((NAME_BASE_Y - CROP_TOP) * SCALE_Y);

    /**
     * 本血条占用的纵向高度：名字 + 裁切后的血条本体。
     */
    private static final int BAR_INCREMENT =
            NAME_RISE + Math.round((CROP_BOTTOM - CROP_TOP) * SCALE_Y);

    /**
     * 镂空血管窗口：框上真正透空的凹槽在贴图 y88..97。
     * 凹槽被中心装饰整块封住 x242..269，于是分成左右两段：
     * 左段 x102..241，右段 x270..421。两端以外是实心端块，不参与血管。
     */
    private static final int VEIN_X = 102;
    private static final int VEIN_W = 320;
    private static final int VEIN_Y = 88;
    private static final int VEIN_H = 10;

    /** 左段 */
    private static final int BAND_L_X = VEIN_X;
    private static final int BAND_L_W = 140;

    /** 右段 */
    private static final int BAND_R_X = 270;
    private static final int BAND_R_W = 152;

    /** 左右两段血管槽，{起点 x, 宽度} */
    private static final int[][] BANDS = {{BAND_L_X, BAND_L_W}, {BAND_R_X, BAND_R_W}};

    /**
     * 常态流光（非受击）：「亮头 + 拖尾」的光带沿已点亮的血管循环扫过。
     * 光带条数固定，位置按当前血量比例把整段铺满——血少时几条挤在一起，满血时铺得很开。
     * 受击那套是独立的 blitHurtVein，走 hurtTime 曲线，跟这里无关。
     */
    private static final int FLOW_BANDS = 5;              // 整段里的光带条数
    private static final long FLOW_CYCLE_MS = 1300L;      // 相位扫过一整格的毫秒数
    private static final float FLOW_HEAD_FRAC = 0.16F;    // 亮头宽度占单格的比例
    private static final float FLOW_TAIL_FRAC = 0.40F;    // 拖尾长度占单格的比例
    private static final int FLOW_TAIL_CHUNKS = 5;        // 拖尾分段数

    /** 亮头：接近纯白，压过管子的青蓝底 */
    private static final float FLOW_HEAD_R = 0.85F;
    private static final float FLOW_HEAD_G = 1.00F;
    private static final float FLOW_HEAD_B = 1.00F;
    private static final float FLOW_HEAD_A = 0.85F;

    /** 拖尾：偏青，最靠近亮头那段最亮，往后线性变淡 */
    private static final float FLOW_TAIL_R = 0.55F;
    private static final float FLOW_TAIL_G = 0.90F;
    private static final float FLOW_TAIL_B = 1.00F;
    private static final float FLOW_TAIL_A = 0.55F;

    private static final float EMPTY_R = 0.16F;
    private static final float EMPTY_G = 0.18F;
    private static final float EMPTY_B = 0.30F;

    /** 受击极亮 #FF00FF，hurtTime 10..7 */
    private static final float HURT_PEAK_R = 1.00F;
    private static final float HURT_PEAK_G = 0.00F;
    private static final float HURT_PEAK_B = 1.00F;
    private static final float HURT_PEAK_A = 1.00F;
    private static final float HURT_HOLD_A = 0.90F;

    /** 受击余辉 #E040FB，hurtTime 6..1 */
    private static final float HURT_GLOW_R = 224.0F / 255.0F;
    private static final float HURT_GLOW_G = 64.0F / 255.0F;
    private static final float HURT_GLOW_B = 251.0F / 255.0F;

    private UndefinedWitherBossBar() {
    }

    /**
     * HIGHEST + receiveCanceled：即使 enhanced_boss_bars 这类 mod 抢先取消事件
     * （守卫 Mixin 缺席/对方更新出新的接管路径时），我们的血条也一定渲染，
     * 且绘制发生在它之后，直接压在它的贴图上面。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        if (!isUndefinedWitherBar(event.getBossEvent().getName())) return;

        event.setCanceled(true);

        Minecraft mc = Minecraft.getInstance();
        GuiGraphics graphics = event.getGuiGraphics();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int centerX = screenW / 2 + SHIFT_X;
        // 血条尽量靠上：顶边直接落在分配位置，名字画在血条纵向范围内
        int top = event.getY();

        float progress = event.getBossEvent().getProgress();
        int filled = progress <= 0.0F ? 0 : Math.max(1, (int) (progress * VEIN_W));

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.scale(SCALE_X, SCALE_Y, 1.0F);

        int px = Math.round(centerX / SCALE_X) - TEX_W / 2;
        int oy = Math.round(top / SCALE_Y) - CROP_TOP;
        int cropH = CROP_BOTTOM - CROP_TOP;

        // 血管整层画在框下面：左右两段各由框自己的镂空窗口裁出来，中心装饰和端块自然盖在上层
        blitVein(graphics, px, oy, filled, BAND_L_X, BAND_L_W);
        blitVein(graphics, px, oy, filled, BAND_R_X, BAND_R_W);
        blitFlowAnimated(graphics, px, oy, filled);
        blitHurtVein(graphics, px, oy, filled, findUndefinedWither(mc));
        // 完整框作为静态底图，盖在血管上
        graphics.blit(FRAME, px, oy + CROP_TOP, 0.0F, (float) CROP_TOP,
                TEX_W, cropH, TEX_W, TEX_H);

        pose.popPose();
        RenderSystem.disableBlend();

        Component name = event.getBossEvent().getName();
        int nameW = mc.font.width(name);
        // 名字画在血条上方，按中心装饰尖顶(NAME_BASE_Y)留 NAME_GAP 的间隔
        int nameY = top - NAME_RISE;
        graphics.drawString(mc.font, name, centerX - nameW / 2, nameY, 0xFFFFFF);

        event.setIncrement(BAR_INCREMENT);
    }

    private static void blitHurtVein(GuiGraphics graphics, int px, int oy, int filled,
                                     WitherBoss wither) {
        if (wither == null || wither.hurtTime <= 0 || filled <= 0) return;

        float r;
        float g;
        float b;
        float a;
        if (wither.hurtTime >= 7) {
            float peakT = (wither.hurtTime - 7) / 3.0F;
            r = HURT_PEAK_R;
            g = HURT_PEAK_G;
            b = HURT_PEAK_B;
            a = HURT_HOLD_A + (HURT_PEAK_A - HURT_HOLD_A) * peakT;
        } else {
            float k = wither.hurtTime / 6.0F;
            r = HURT_GLOW_R + (HURT_PEAK_R - HURT_GLOW_R) * k;
            g = HURT_GLOW_G + (HURT_PEAK_G - HURT_GLOW_G) * k;
            b = HURT_GLOW_B + (HURT_PEAK_B - HURT_GLOW_B) * k;
            a = HURT_HOLD_A * k;
        }

        RenderSystem.setShaderColor(r, g, b, a);
        blitVeinBand(graphics, px, oy, filled, BAND_L_X, BAND_L_W);
        blitVeinBand(graphics, px, oy, filled, BAND_R_X, BAND_R_W);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** 把一段血管槽里已点亮的宽度直接贴满，不做亮/暗处理（受击用）。 */
    private static void blitVeinBand(GuiGraphics graphics, int px, int oy, int filled,
                                     int bandX, int bandW) {
        int lit = clamp(filled - (bandX - VEIN_X), 0, bandW);
        if (lit <= 0) return;
        graphics.blit(FILL, px + bandX, oy + VEIN_Y,
                (float) bandX, (float) VEIN_Y,
                lit, VEIN_H, TEX_W, TEX_H);
    }

    private static WitherBoss findUndefinedWither(Minecraft mc) {
        if (mc.level == null) return null;
        for (WitherBoss wither : mc.level.getEntitiesOfClass(WitherBoss.class,
                mc.player == null ? new net.minecraft.world.phys.AABB(0, 0, 0, 0, 0, 0)
                        : mc.player.getBoundingBox().inflate(128.0D),
                WitherTitleHelper::isUndefinedWither)) {
            return wither;
        }
        return null;
    }

    /**
     * 常态流光：在已点亮的血管上铺几条循环扫过的光带，突出「血在流」。
     * 光带条数固定，位置按当前血量比例把整段铺满，所以血越少光带挤得越紧。
     * 完全独立于受击高亮——受击那套 blitHurtVein 画在它上面。
     */
    private static void blitFlowAnimated(GuiGraphics graphics, int px, int oy, int filled) {
        int flowLength = clamp(filled, 0, VEIN_W);
        if (flowLength <= 0) return;

        float step = flowLength / (float) FLOW_BANDS;                 // 一格宽度（按血量比例）
        float headW = Math.max(2.0F, step * FLOW_HEAD_FRAC);
        float tailLen = Math.max(4.0F, step * FLOW_TAIL_FRAC);
        float phase = (System.currentTimeMillis() % FLOW_CYCLE_MS) / (float) FLOW_CYCLE_MS;

        for (int b = 0; b < FLOW_BANDS; b++) {
            // 第 b 条光带的亮头位置：整段按比例摊开，再加一个 0..1 格的行进相位
            float head = VEIN_X + (b + phase) * step;
            float tailStep = tailLen / FLOW_TAIL_CHUNKS;

            for (int[] band : BANDS) {
                int bandX = band[0];
                int bandW = band[1];
                int lit = clamp(filled - (bandX - VEIN_X), 0, bandW);
                if (lit <= 0) continue;
                int lo = bandX;
                int hi = bandX + lit;

                // 拖尾：从远到近逐段画，越靠近亮头越亮
                for (int c = 0; c < FLOW_TAIL_CHUNKS; c++) {
                    int a0 = Math.round(head - tailLen + c * tailStep);
                    int a1 = Math.round(head - tailLen + (c + 1) * tailStep);
                    float k = (c + 1) / (float) FLOW_TAIL_CHUNKS;
                    RenderSystem.setShaderColor(FLOW_TAIL_R, FLOW_TAIL_G, FLOW_TAIL_B, FLOW_TAIL_A * k * k);
                    blitClipped(graphics, px, oy, a0, a1, lo, hi);
                }

                RenderSystem.setShaderColor(FLOW_HEAD_R, FLOW_HEAD_G, FLOW_HEAD_B, FLOW_HEAD_A);
                blitClipped(graphics, px, oy, Math.round(head), Math.round(head + headW), lo, hi);
            }
        }
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** 把 [from,to) 与 [lo,hi) 的交集贴出去；落在槽外直接跳过。 */
    private static void blitClipped(GuiGraphics graphics, int px, int oy,
                                    int from, int to, int lo, int hi) {
        int f = Math.max(from, lo);
        int t = Math.min(to, hi);
        if (t <= f) return;
        graphics.blit(FILL, px + f, oy + VEIN_Y,
                (float) f, (float) VEIN_Y,
                t - f, VEIN_H, TEX_W, TEX_H);
    }

    /**
     * 在 [bandX, bandX+bandW) 里按全局 filled 切出亮/暗两段血管。
     * filled 是整条血管（VEIN_X 起算）已点亮的宽度。
     */
    private static void blitVein(GuiGraphics graphics, int px, int oy, int filled,
                                 int bandX, int bandW) {
        int localFilled = clamp(filled - (bandX - VEIN_X), 0, bandW);
        int localEmpty = bandW - localFilled;
        if (localFilled > 0) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            graphics.blit(FILL, px + bandX, oy + VEIN_Y,
                    (float) bandX, (float) VEIN_Y,
                    localFilled, VEIN_H, TEX_W, TEX_H);
        }
        if (localEmpty > 0) {
            RenderSystem.setShaderColor(EMPTY_R, EMPTY_G, EMPTY_B, 1.0F);
            graphics.blit(FILL, px + bandX + localFilled, oy + VEIN_Y,
                    (float) (bandX + localFilled), (float) VEIN_Y,
                    localEmpty, VEIN_H, TEX_W, TEX_H);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static boolean isUndefinedWitherBar(Component name) {
        return WitherTitleHelper.isUndefinedBarName(name);
    }
}
