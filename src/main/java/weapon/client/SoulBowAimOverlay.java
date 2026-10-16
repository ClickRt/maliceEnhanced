package weapon.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import weapon.SoulBowItem;

import java.util.List;

/**
 * 浩瀚星魂之弓的瞄准辅助：蓄力时准星处浮现一个渐变小圆环，
 * 处于圆环内、且视线可达的可瞄准实体上叠出标记。
 * 圆环整环同色，颜色沿五段调色板（深空蓝 → 魂火青）往返推移，不留跳变。
 */
@Mod.EventBusSubscriber(modid = "malice_enhanced", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SoulBowAimOverlay implements IGuiOverlay {

    private static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath("malice_enhanced", "soul_bow_aim");
    private static final ResourceLocation MARKER =
            ResourceLocation.fromNamespaceAndPath("malice_enhanced", "textures/item/biaoji.png");

    /** #06121F → #0B2A45 → #125B7A → #18A0C9 → #A5F3FC */
    private static final int[] PALETTE = {0x06121F, 0x0B2A45, 0x125B7A, 0x18A0C9, 0xA5F3FC};
    /** 调色板单程走完的秒数（去 + 回共两倍） */
    private static final float CYCLE_SECONDS = 1.6F;
    /** 圆环半径（GUI 像素） */
    private static final float RING_RADIUS = 32.0F;
    /** 近距离标记边长（GUI 像素） */
    private static final int MARKER_SIZE_NEAR = 16;
    /** 远距离标记边长，避免挡住目标 */
    private static final int MARKER_SIZE_FAR = 6;
    /** 标记固定不透明度 */
    private static final float MARKER_ALPHA = 0.8F;
    /** 可瞄准实体的最大距离 */
    private static final double AIM_RANGE = 100.0D;

    private static final Vector4f SCRATCH = new Vector4f();

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll(ID.getPath(), new SoulBowAimOverlay());
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        if (mc.options.hideGui || mc.screen != null) return;

        ItemStack stack = player.getUseItem();
        if (!player.isUsingItem() || !(stack.getItem() instanceof SoulBowItem)) return;

        Matrix4f view = SoulBowAimMatrices.view();
        Matrix4f projection = SoulBowAimMatrices.projection();
        if (view == null || projection == null) return;

        float time = (mc.level.getGameTime() + partialTick) / 20.0F;
        float centerX = width / 2.0F;
        float centerY = height / 2.0F;

        drawRing(graphics, centerX, centerY, time);
        drawMarkers(mc, player, graphics, view, projection, centerX, centerY, width, height);
    }

    /** 准星处的渐变圆环：整环同一时刻同色，颜色往返推移 */
    private void drawRing(GuiGraphics graphics, float cx, float cy, float time) {
        int steps = Math.max(96, (int) (RING_RADIUS * Mth.TWO_PI * 2.0F));
        int argb = paletteColor(time, 0.9F);
        for (int i = 0; i < steps; i++) {
            float angle = i * Mth.TWO_PI / steps;
            int x = Math.round(cx + Mth.cos(angle) * RING_RADIUS);
            int y = Math.round(cy + Mth.sin(angle) * RING_RADIUS);
            graphics.fill(x, y, x + 1, y + 1, argb);
        }
    }

    /** 圆环内、视线可达的可瞄准实体上叠出标记 */
    private void drawMarkers(Minecraft mc, LocalPlayer player, GuiGraphics graphics,
                             Matrix4f view, Matrix4f projection,
                             float cx, float cy, int width, int height) {
        Vec3 camPos = SoulBowAimMatrices.cameraPos();
        if (camPos == null) return;

        AABB area = player.getBoundingBox().inflate(AIM_RANGE);
        List<Entity> candidates = mc.level.getEntities(player, area, e -> isAimable(player, e));
        float radiusSq = RING_RADIUS * RING_RADIUS;

        for (Entity entity : candidates) {
            if (!hasLineOfSight(player, entity)) continue;

            SCRATCH.set(
                    (float) (entity.getX() - camPos.x),
                    (float) (entity.getY() + entity.getBbHeight() * 0.5D - camPos.y),
                    (float) (entity.getZ() - camPos.z), 1.0F);
            view.transform(SCRATCH);
            if (SCRATCH.w <= 0.0F) continue;
            projection.transform(SCRATCH);
            if (SCRATCH.w <= 0.0F) continue;

            float screenX = (SCRATCH.x / SCRATCH.w * 0.5F + 0.5F) * width;
            float screenY = (0.5F - SCRATCH.y / SCRATCH.w * 0.5F) * height;

            float dx = screenX - cx;
            float dy = screenY - cy;
            if (dx * dx + dy * dy > radiusSq) continue;

            float dist = (float) player.distanceTo(entity);
            float far = Mth.clamp(dist / (float) AIM_RANGE, 0.0F, 1.0F);
            int size = Math.max(MARKER_SIZE_FAR, Math.round(Mth.lerp(far, MARKER_SIZE_NEAR, MARKER_SIZE_FAR)));
            blitMarker(graphics, Math.round(screenX) - size / 2, Math.round(screenY) - size / 2, size);
        }
    }

    /** POSITION_COLOR_TEX 走顶点 alpha；GuiGraphics.blit 的 POSITION_TEX 吃不到 setColor */
    private static void blitMarker(GuiGraphics graphics, int x, int y, int size) {
        graphics.flush();
        RenderSystem.setShaderTexture(0, MARKER);
        RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Matrix4f pose = graphics.pose().last().pose();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        buffer.vertex(pose, x, y + size, 0.0F).color(1.0F, 1.0F, 1.0F, MARKER_ALPHA).uv(0.0F, 1.0F).endVertex();
        buffer.vertex(pose, x + size, y + size, 0.0F).color(1.0F, 1.0F, 1.0F, MARKER_ALPHA).uv(1.0F, 1.0F).endVertex();
        buffer.vertex(pose, x + size, y, 0.0F).color(1.0F, 1.0F, 1.0F, MARKER_ALPHA).uv(1.0F, 0.0F).endVertex();
        buffer.vertex(pose, x, y, 0.0F).color(1.0F, 1.0F, 1.0F, MARKER_ALPHA).uv(0.0F, 0.0F).endVertex();
        tesselator.end();
        RenderSystem.disableBlend();
    }

    /** 视线遮挡检测：超过射程或视线被方块挡住则不可瞄准 */
    private static boolean hasLineOfSight(LocalPlayer player, Entity entity) {
        Vec3 center = new Vec3(entity.getX(),
                entity.getY() + entity.getBbHeight() * 0.5D,
                entity.getZ());
        if (player.getEyePosition().distanceToSqr(center) > AIM_RANGE * AIM_RANGE) return false;
        return player.hasLineOfSight(entity);
    }

    private static boolean isAimable(LocalPlayer player, Entity entity) {
        if (!(entity instanceof LivingEntity living)) return false;
        if (entity == player) return false;
        if (entity instanceof Player other && (other.isCreative() || other.isSpectator())) return false;
        if (!living.isAlive() || living.isRemoved()) return false;
        return !living.isInvisibleTo(player);
    }

    /**
     * 按时间在五段调色板间往返插值：单程 {@link #CYCLE_SECONDS} 秒，
     * 走到最深色后原路倒放回最暗，避免最亮直接跳回最暗。
     */
    private static int paletteColor(float time, float alpha) {
        float u = (time / CYCLE_SECONDS) % 2.0F;
        if (u < 0.0F) u += 2.0F;
        float t = u <= 1.0F ? u : 2.0F - u;
        float scaled = t * (PALETTE.length - 1);
        int index = Mth.clamp((int) scaled, 0, PALETTE.length - 2);
        int rgb = lerpRgb(PALETTE[index], PALETTE[index + 1], scaled - index);
        int a = Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        return (a << 24) | rgb;
    }

    private static int lerpRgb(int from, int to, float f) {
        int r = (int) Mth.lerp(f, (from >> 16) & 0xFF, (to >> 16) & 0xFF);
        int g = (int) Mth.lerp(f, (from >> 8) & 0xFF, (to >> 8) & 0xFF);
        int b = (int) Mth.lerp(f, from & 0xFF, to & 0xFF);
        return (r << 16) | (g << 8) | b;
    }
}
