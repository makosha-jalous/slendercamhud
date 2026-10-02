package com.example.slendercam.client;

import com.example.slendercam.SlenderCamMod;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Random;

/**
 * Slender: The Arrival style camcorder HUD, laid out on a 739x415 reference grid
 * measured from the reference screenshot and scaled to the current screen height.
 */
@Mod.EventBusSubscriber(modid = SlenderCamMod.ID, value = Dist.CLIENT)
public class CameraHud {

    // ---- tweakables
    private static final float GRAIN_ALPHA = 0.20f;           // film grain strength (0..1)
    private static final int GRAIN_PIXEL_SIZE = 1;            // 1 = one grain dot per real screen pixel (finest)
    private static final int BATTERY_DRAIN_SECONDS = 600;     // full -> empty while filming
    private static final int BATTERY_RECHARGE_SECONDS = 1800; // empty -> full while put away
    private static final float TOP_BAR_LEVEL = 0.05f;         // filled part of the top bar (0..1)

    private static final int WHITE = 0xB4FFFFFF;
    private static final int FRAME = 0x70FFFFFF;
    private static final int RED = 0xFFE02020;

    private static final int REF_H = 415;
    private static final int TEX = 512;
    private static final Random RND = new Random();

    private static int recTicks = 0;
    private static float battery = 0.87f;
    private static DynamicTexture grain;
    private static ResourceLocation grainLoc;
    private static long lastNoise = 0;

    static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null
                && mc.options.getCameraType().isFirstPerson()
                && mc.player.getMainHandItem().is(SlenderCamMod.CAMERA.get());
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (active()) {
            recTicks++;
            battery = Math.max(0f, battery - 1f / (20f * BATTERY_DRAIN_SECONDS));
        } else {
            battery = Math.min(1f, battery + 1f / (20f * BATTERY_RECHARGE_SECONDS));
        }
    }

    @SubscribeEvent
    public static void hideCrosshair(RenderGuiOverlayEvent.Pre e) {
        if (active() && e.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id())) {
            e.setCanceled(true);
        }
    }

    public static void render(ForgeGui gui, PoseStack ps, float partialTick, int w, int h) {
        if (!active()) return;
        Minecraft mc = Minecraft.getInstance();

        drawGrain(ps, w, h);
        drawVignette(ps, w, h);

        // everything below is drawn in reference units (739x415 at 16:9)
        float s = h / (float) REF_H;
        int refW = Math.round(w / s);
        boolean blinkOn = (System.currentTimeMillis() / 500) % 2 == 0;

        ps.pushPose();
        ps.scale(s, s, 1f);

        // thin border frame
        int fl = 19, ft = 21, fr = refW - 24, fb = 383;
        GuiComponent.fill(ps, fl, ft, fr, ft + 1, FRAME);
        GuiComponent.fill(ps, fl, fb, fr, fb + 1, FRAME);
        GuiComponent.fill(ps, fl, ft, fl + 1, fb, FRAME);
        GuiComponent.fill(ps, fr, ft, fr + 1, fb + 1, FRAME);

        drawBattery(ps, blinkOn);
        drawTopBar(ps, refW);

        // REC (big, red) with blinking dot, right side
        int right = refW - 34;
        float recSx = 2.4f, recSy = 2.7f;
        float recW = mc.font.width("REC") * recSx;
        float recX = right - recW;
        ps.pushPose();
        ps.translate(recX, 34, 0);
        ps.scale(recSx, recSy, 1f);
        mc.font.draw(ps, "REC", 0, 0, RED);
        ps.popPose();
        if (blinkOn) {
            int cx = Math.round(recX) - 19, cy = 43;
            for (int dy = -7; dy <= 7; dy++) {
                int dx = (int) Math.round(Math.sqrt(7.5 * 7.5 - dy * dy));
                GuiComponent.fill(ps, cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, RED);
            }
        }

        // timer under REC
        int secs = recTicks / 20;
        String time = String.format("%02d:%02d:%02d", secs / 3600, (secs / 60) % 60, secs % 60);
        float tSx = 1.35f, tSy = 1.7f;
        float tW = mc.font.width(time) * tSx;
        ps.pushPose();
        ps.translate(right - tW, 59, 0);
        ps.scale(tSx, tSy, 1f);
        mc.font.draw(ps, time, 0, 0, 0xFFDCDCDC);
        ps.popPose();

        // faint thin center cross
        int cx = refW / 2, cy = REF_H / 2;
        int c = 0x60FFFFFF;
        GuiComponent.fill(ps, cx - 15, cy, cx + 16, cy + 1, c);
        GuiComponent.fill(ps, cx, cy - 15, cx + 1, cy + 16, c);

        ps.popPose();
    }

    /** Horizontal battery, checkerboard fill, nub on the right. */
    private static void drawBattery(PoseStack ps, boolean blinkOn) {
        int x = 32, y = 33, bw = 58, bh = 24;
        int col = (battery < 0.25f && blinkOn) ? RED : WHITE;
        GuiComponent.fill(ps, x, y, x + bw, y + 1, col);
        GuiComponent.fill(ps, x, y + bh - 1, x + bw, y + bh, col);
        GuiComponent.fill(ps, x, y, x + 1, y + bh, col);
        GuiComponent.fill(ps, x + bw - 1, y, x + bw, y + bh, col);
        GuiComponent.fill(ps, x + bw, y + 7, x + bw + 4, y + 17, col); // nub

        int inner = bw - 6;           // usable inner width
        int fillW = Math.round(inner * battery);
        int cell = 3;
        int cols = (fillW + cell - 1) / cell;
        for (int i = 0; i < cols; i++) {
            for (int j = 0; j < 6; j++) {
                if (((i + j) & 1) != 0) continue;
                int cx0 = x + 3 + i * cell;
                int cy0 = y + 3 + j * cell + 1;
                int cx1 = Math.min(cx0 + cell, x + 3 + fillW);
                GuiComponent.fill(ps, cx0, cy0, cx1, cy0 + cell, col);
            }
        }
    }

    /** Long thin dotted bar at the top center with a small solid start segment. */
    private static void drawTopBar(PoseStack ps, int refW) {
        int cx = refW / 2;
        int x0 = cx - 80, x1 = cx + 80, y0 = 33, y1 = 42;
        for (int x = x0; x < x1; x += 2) {
            GuiComponent.fill(ps, x, y0, x + 1, y0 + 1, WHITE);
            GuiComponent.fill(ps, x, y1, x + 1, y1 + 1, WHITE);
        }
        for (int y = y0; y <= y1; y += 2) {
            GuiComponent.fill(ps, x0, y, x0 + 1, y + 1, WHITE);
            GuiComponent.fill(ps, x1, y, x1 + 1, y + 1, WHITE);
        }
        int fillEnd = x0 + Math.max(8, Math.round(160 * TOP_BAR_LEVEL));
        GuiComponent.fill(ps, x0, y0, fillEnd, y0 + 1, WHITE);
        GuiComponent.fill(ps, x0, y1, fillEnd, y1 + 1, WHITE);
        GuiComponent.fill(ps, x0, y0, x0 + 1, y1 + 1, WHITE);
        GuiComponent.fill(ps, fillEnd - 1, y0, fillEnd, y1 + 1, WHITE);
    }

    private static void drawVignette(PoseStack ps, int w, int h) {
        gradient(ps, w, h, (int) (h * 0.16f), 0x78, true);
        gradient(ps, w, h, (int) (h * 0.30f), 0xB4, false);
    }

    private static void gradient(PoseStack ps, int w, int h, int band, int maxAlpha, boolean top) {
        int steps = 20;
        int step = Math.max(1, band / steps);
        for (int i = 0; i < steps; i++) {
            int a = (int) (maxAlpha * (1f - i / (float) steps));
            int col = a << 24;
            if (top) GuiComponent.fill(ps, 0, i * step, w, (i + 1) * step, col);
            else GuiComponent.fill(ps, 0, h - (i + 1) * step, w, h - i * step, col);
        }
    }

    /**
     * Fine film grain: a 512x512 noise texture is re-rolled ~8x a second and, every frame,
     * sampled at a random offset so 1 grain dot = GRAIN_PIXEL_SIZE real screen pixels.
     */
    private static void drawGrain(PoseStack ps, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (grain == null) {
            grain = new DynamicTexture(TEX, TEX, false);
            grain.setFilter(false, false);
            grainLoc = mc.getTextureManager().register("slendercam_grain", grain);
        }
        long now = System.currentTimeMillis();
        if (now - lastNoise > 120) {
            lastNoise = now;
            NativeImage img = grain.getPixels();
            if (img != null) {
                for (int y = 0; y < TEX; y++) {
                    for (int x = 0; x < TEX; x++) {
                        int v = RND.nextInt(256);
                        int a = RND.nextInt(256);
                        img.setPixelRGBA(x, y, (a << 24) | (v << 16) | (v << 8) | v);
                    }
                }
                grain.upload();
            }
        }
        double g = mc.getWindow().getGuiScale();
        int regionW = Math.max(1, (int) Math.round(w * g / GRAIN_PIXEL_SIZE));
        int regionH = Math.max(1, (int) Math.round(h * g / GRAIN_PIXEL_SIZE));

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, GRAIN_ALPHA);
        RenderSystem.setShaderTexture(0, grainLoc);
        GuiComponent.blit(ps, 0, 0, w, h, RND.nextInt(TEX), RND.nextInt(TEX),
                regionW, regionH, TEX, TEX);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }
}
