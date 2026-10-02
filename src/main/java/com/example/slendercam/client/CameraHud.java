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

/** Slender: The Arrival style camcorder HUD. Shown while the camera item is in the main hand. */
@Mod.EventBusSubscriber(modid = SlenderCamMod.ID, value = Dist.CLIENT)
public class CameraHud {

    // ---- tweakables
    private static final float GRAIN_ALPHA = 0.22f;          // film grain strength (0..1)
    private static final int BATTERY_DRAIN_SECONDS = 600;    // full -> empty while filming
    private static final int BATTERY_RECHARGE_SECONDS = 1800; // empty -> full while put away
    private static final int WHITE = 0xCCFFFFFF;
    private static final int RED = 0xFFDD2222;

    private static final int GW = 320, GH = 180;
    private static final Random RND = new Random();

    private static int recTicks = 0;
    private static float battery = 1f;
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

        // border frame
        int m = Math.max(6, (int) (Math.min(w, h) * 0.04f));
        GuiComponent.fill(ps, m, m, w - m, m + 1, WHITE);
        GuiComponent.fill(ps, m, h - m - 1, w - m, h - m, WHITE);
        GuiComponent.fill(ps, m, m, m + 1, h - m, WHITE);
        GuiComponent.fill(ps, w - m - 1, m, w - m, h - m, WHITE);

        boolean blinkOn = (System.currentTimeMillis() / 500) % 2 == 0;

        // battery, top-left
        ps.pushPose();
        ps.translate(m + 8, m + 8, 0);
        ps.scale(1.5f, 1.5f, 1f);
        int bc = (battery < 0.25f && blinkOn) ? RED : WHITE;
        GuiComponent.fill(ps, 0, 0, 22, 1, bc);
        GuiComponent.fill(ps, 0, 8, 22, 9, bc);
        GuiComponent.fill(ps, 0, 0, 1, 9, bc);
        GuiComponent.fill(ps, 21, 0, 22, 9, bc);
        GuiComponent.fill(ps, 22, 3, 24, 6, bc);
        int segs = (int) Math.ceil(battery * 4f);
        for (int i = 0; i < segs; i++) {
            GuiComponent.fill(ps, 2 + i * 5, 2, 6 + i * 5, 7, bc);
        }
        ps.popPose();

        // REC + time, top-right
        int tw = mc.font.width("00:00:00");
        ps.pushPose();
        ps.translate(w - m - 8 - tw * 1.5f, m + 8, 0);
        ps.scale(1.5f, 1.5f, 1f);
        int recX = tw - mc.font.width("REC");
        mc.font.drawShadow(ps, "REC", recX, 0, RED);
        if (blinkOn) {
            int cx = recX - 6, cy = 4;
            for (int dy = -3; dy <= 3; dy++) {
                int dx = (int) Math.round(Math.sqrt(9.5 - dy * dy));
                GuiComponent.fill(ps, cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, RED);
            }
        }
        int secs = recTicks / 20;
        String time = String.format("%02d:%02d:%02d", secs / 3600, (secs / 60) % 60, secs % 60);
        mc.font.drawShadow(ps, time, 0, 12, 0xFFFFFFFF);
        ps.popPose();

        // center cross
        int cx = w / 2, cy = h / 2;
        GuiComponent.fill(ps, cx - 4, cy, cx + 5, cy + 1, WHITE);
        GuiComponent.fill(ps, cx, cy - 4, cx + 1, cy + 5, WHITE);
    }

    private static void drawVignette(PoseStack ps, int w, int h) {
        int band = (int) (h * 0.22f);
        int steps = 16;
        int step = Math.max(1, band / steps);
        for (int i = 0; i < steps; i++) {
            int a = (int) (0x99 * (1f - i / (float) steps));
            int col = a << 24;
            GuiComponent.fill(ps, 0, i * step, w, (i + 1) * step, col);
            GuiComponent.fill(ps, 0, h - (i + 1) * step, w, h - i * step, col);
        }
    }

    private static void drawGrain(PoseStack ps, int w, int h) {
        if (grain == null) {
            grain = new DynamicTexture(GW, GH, false);
            grainLoc = Minecraft.getInstance().getTextureManager().register("slendercam_grain", grain);
        }
        long now = System.currentTimeMillis();
        if (now - lastNoise > 40) { // new noise ~25 times a second
            lastNoise = now;
            NativeImage img = grain.getPixels();
            if (img != null) {
                for (int y = 0; y < GH; y++) {
                    for (int x = 0; x < GW; x++) {
                        int v = RND.nextInt(256);
                        int a = RND.nextInt(256);
                        img.setPixelRGBA(x, y, (a << 24) | (v << 16) | (v << 8) | v);
                    }
                }
                grain.upload();
            }
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, GRAIN_ALPHA);
        RenderSystem.setShaderTexture(0, grainLoc);
        GuiComponent.blit(ps, 0, 0, w, h, 0, 0, GW, GH, GW, GH);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }
}
