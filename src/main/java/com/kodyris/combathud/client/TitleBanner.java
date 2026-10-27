package com.kodyris.combathud.client;

import com.kodyris.combathud.HudConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.ArrayDeque;
import java.util.Locale;

/**
 * Region title: orange "NOW ENTERING" line typed in between two squares, white area name typed under it,
 * flanking vertical lines and an expanding hairline (Division 2 flavour). Queue-based, one at a time.
 */
final class TitleBanner {
    private TitleBanner() {}

    private static final class Banner {
        final String label, name;
        final int rgb;
        long start;
        Banner(String label, String name, int rgb) { this.label = label; this.name = name; this.rgb = rgb; }
    }

    // timeline (seconds)
    private static final float HOLD_END = 3.6f, FADE = 0.5f, TOTAL = HOLD_END + FADE;

    private static final ArrayDeque<Banner> QUEUE = new ArrayDeque<>();
    private static Banner current;

    /** Accent colour per style. Anything unknown falls back to the default orange. */
    static int colorFor(String style) {
        return switch (style == null ? "" : style.toLowerCase(Locale.ROOT)) {
            case "complete" -> 0x3DDC6B;   // green
            case "fail" -> 0xFF4A3A;       // red
            case "team", "class" -> 0x2EA8FF;   // blue
            default -> 0xFF8A00;           // orange
        };
    }

    static void show(String label, String name) {
        show(label, name, "default");
    }

    static void show(String label, String name, String style) {
        String l = (label == null || label.isBlank()) ? "NOW ENTERING" : label.trim().toUpperCase(Locale.ROOT);
        String trimmed = name == null ? "" : name.trim();
        // class banners show the display name exactly as it was set; other titles are uppercased
        boolean keepCase = "class".equalsIgnoreCase(style) || "team".equalsIgnoreCase(style);
        String n = keepCase ? trimmed : trimmed.toUpperCase(Locale.ROOT);
        if (current != null && current.label.equals(l) && current.name.equals(n)) return;   // already showing
        Banner last = QUEUE.peekLast();
        if (last != null && last.label.equals(l) && last.name.equals(n)) return;            // already queued
        if (QUEUE.size() >= 4) return;
        QUEUE.add(new Banner(l, n, colorFor(style)));
    }

    static void render(ForgeGui gui, GuiGraphics g, float partialTick, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        long now = System.nanoTime();

        if (current == null) {
            current = QUEUE.poll();
            if (current == null) return;
            current.start = now;
            if (HudConfig.REGION_SOUND.get()) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_IN, 0.8f, 0.6f));
            }
        }

        float t = (now - current.start) / 1.0e9f;
        if (t >= TOTAL) {
            current = null;
            return;
        }

        boolean anim = HudState.animate();
        float alpha = t < 0.15f ? t / 0.15f : (t > HOLD_END ? 1f - (t - HOLD_END) / FADE : 1f);
        alpha = Mth.clamp(alpha, 0f, 1f);
        if (alpha <= 0.02f) return;
        int a = (int) (alpha * 255f);

        float p1 = anim ? Mth.clamp((t - 0.10f) / 0.55f, 0f, 1f) : 1f;            // label typing
        float p2 = anim ? Mth.clamp((t - 0.60f) / 0.50f, 0f, 1f) : 1f;            // name typing
        float pl = anim ? HudState.easeOut(Mth.clamp((t - 0.20f) / 0.60f, 0f, 1f)) : 1f;   // hairline

        Font font = mc.font;
        int cx = w / 2, y1 = (int) (h * 0.15f), y2 = y1 + 14;

        String l1 = current.label.substring(0, (int) Math.ceil(p1 * current.label.length()));
        String l2 = current.name.substring(0, (int) Math.ceil(p2 * current.name.length()));

        int orange = (a << 24) | current.rgb;
        int white  = (a << 24) | 0xE8E8E8;
        int grey   = (((int) (a * 0.5f)) << 24) | 0xC8C8C8;
        int orange60 = (((int) (a * 0.6f)) << 24) | current.rgb;

        // flanking verticals + hairline that grows from the centre
        final int half = 108;
        g.fill(cx - half, y1 - 4, cx - half + 1, y2 + 14, grey);
        g.fill(cx + half, y1 - 4, cx + half + 1, y2 + 14, grey);
        int hl = (int) (half * pl);
        g.fill(cx - hl, y2 + 14, cx + hl, y2 + 15, orange60);

        // line 1 with squares that follow the visible text
        if (!l1.isEmpty()) {
            int wd = drawSpaced(g, font, l1, cx, y1, orange, 1, 1.0f, true);
            int sy = y1 + 2;
            g.fill(cx - wd / 2 - 10, sy, cx - wd / 2 - 6, sy + 4, orange);
            g.fill(cx + wd / 2 + 6, sy, cx + wd / 2 + 10, sy + 4, orange);
        }
        if (!l2.isEmpty()) {
            drawSpaced(g, font, l2, cx, y2, white, 1, 1.0f, false);
        }
    }

    private static Component glyph(char c, boolean bold) {
        Component comp = Component.literal(String.valueOf(c));
        return bold ? ((net.minecraft.network.chat.MutableComponent) comp).withStyle(ChatFormatting.BOLD) : comp;
    }

    private static int spacedWidth(Font font, String s, int spacing, boolean bold) {
        int wd = 0;
        for (char c : s.toCharArray()) wd += font.width(glyph(c, bold)) + spacing;
        return Math.max(0, wd - spacing);
    }

    /** Draws letter-spaced text centred on cx; returns its on-screen width. */
    private static int drawSpaced(GuiGraphics g, Font font, String s, int cx, int y, int color,
                                  int spacing, float scale, boolean bold) {
        int raw = spacedWidth(font, s, spacing, bold);
        g.pose().pushPose();
        g.pose().translate(cx, y, 0f);
        g.pose().scale(scale, scale, 1f);
        int x = -raw / 2;
        for (char c : s.toCharArray()) {
            Component comp = glyph(c, bold);
            g.drawString(font, comp, x, 0, color, false);
            x += font.width(comp) + spacing;
        }
        g.pose().popPose();
        return (int) (raw * scale);
    }
}
