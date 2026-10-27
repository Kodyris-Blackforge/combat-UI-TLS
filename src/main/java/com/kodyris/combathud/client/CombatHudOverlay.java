package com.kodyris.combathud.client;

import com.kodyris.combathud.HudConfig;
import com.kodyris.combathud.contract.ContractView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.fml.ModList;

import java.util.List;

/**
 * Warzone-style HUD: info bar, compass, own player block, contracts, hotbar, weapon panel.
 * Squad lives in the inventory menu (SQUAD tab). Not yet: minimap, cash, pickup prompt.
 */
public final class CombatHudOverlay {
    private CombatHudOverlay() {}

    // palette
    static final int PANEL      = 0xB0101418;
    static final int PANEL_DARK = 0xD0090B0E;
    static final int TEXT       = 0xFFE8E8E8;
    static final int DIM        = 0xFF8A9096;
    static final int ACCENT     = 0xFFFFB400;   // Warzone yellow
    static final int HEALTH     = 0xFF3DDC6B;   // green
    static final int ARMOR      = 0xFFEFEFEF;
    static final int FOOD       = 0xFFFF8A2E;
    static final int[] SQUAD    = {0xFFFF9A2E, 0xFF2EC8FF, 0xFFE6D84C, 0xFFE05CFF};

    // fps counter
    private static int frames, fps;
    private static long lastSecond = System.currentTimeMillis();

    public static boolean isActive() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null || mc.options.hideGui) return false;
        GameType t = mc.gameMode.getPlayerMode();
        return t == GameType.SURVIVAL || t == GameType.ADVENTURE;
    }

    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int w, int h) {
        if (!isActive()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        Font font = mc.font;

        HudState.update(p);
        drawVignette(g, p, w, h);

        tickFps();
        drawInfoBar(g, font, mc, p);
        if (hasItem(p, Items.COMPASS)) drawCompass(g, font, p, w);
        drawSelf(g, font, p, h);
        drawContracts(g, font, h);
        drawHotbar(g, font, p, w, h);
        drawWeapon(g, font, p, w, h);
    }

    // ---------------------------------------------------------------- effects
    /** Red screen-edge flash on damage plus a slow pulse at low health. */
    private static void drawVignette(GuiGraphics g, Player p, int w, int h) {
        if (!HudConfig.DAMAGE_VIGNETTE.get() || !HudState.animate()) return;
        float a = HudState.damageFlash * 0.55f;
        float frac = p.getHealth() / Math.max(1f, p.getMaxHealth());
        if (p.isAlive() && frac <= 0.3f) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(System.nanoTime() / 1.0e9 * 5.0);
            a = Math.max(a, (0.08f + 0.14f * pulse) * (1f - 0.5f * frac / 0.3f));
        }
        vignette(g, w, h, 0xD01010, a);
    }

    private static void vignette(GuiGraphics g, int w, int h, int rgb, float alpha) {
        if (alpha <= 0.01f) return;
        final int steps = 16;
        int st = Math.max(1, (int) (Math.min(w, h) * 0.16f / steps));
        for (int i = 0; i < steps; i++) {
            float k = 1f - i / (float) steps;
            int col = ((int) (alpha * k * k * 255f) << 24) | rgb;
            int o = i * st;
            g.fill(o, o, w - o, o + st, col);                       // top
            g.fill(o, h - o - st, w - o, h - o, col);               // bottom
            g.fill(o, o + st, o + st, h - o - st, col);             // left
            g.fill(w - o - st, o + st, w - o, h - o - st, col);     // right
        }
    }

    // ---------------------------------------------------------------- info bar
    private static void tickFps() {
        frames++;
        long now = System.currentTimeMillis();
        if (now - lastSecond >= 1000) {
            fps = frames;
            frames = 0;
            lastSecond = now;
        }
    }

    private static void drawInfoBar(GuiGraphics g, Font font, Minecraft mc, LocalPlayer p) {
        int ping = 0;
        ClientPacketListener conn = mc.getConnection();
        if (conn != null) {
            PlayerInfo info = conn.getPlayerInfo(p.getUUID());
            if (info != null) ping = info.getLatency();
        }
        String s = String.format("FPS: %d   LATENCY: %d MS", fps, ping);
        if (hasItem(p, Items.CLOCK)) {
            long t = mc.level.getDayTime() % 24000L;
            int hour = (int) ((t / 1000L + 6L) % 24L);
            int min = (int) ((t % 1000L) * 60L / 1000L);
            s += String.format("   %02d:%02d", hour, min);
        }
        int wdt = font.width(s) + 10;
        g.fill(4, 4, 4 + wdt, 16, PANEL_DARK);
        g.drawString(font, s, 9, 6, TEXT, false);
    }

    /** True if the item is anywhere in the inventory (incl. offhand/armor slots). */
    private static boolean hasItem(Player p, Item item) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(item)) return true;
        }
        return false;
    }

    // ----------------------------------------------------------------- compass
    private static void drawCompass(GuiGraphics g, Font font, Player p, int w) {
        float bearing = (Mth.wrapDegrees(p.getYRot() + 180f) + 360f) % 360f; // 0 = N, 90 = E
        int cx = w / 2, y = 6, half = 130;
        float pxPerDeg = 3f;

        for (int deg = 0; deg < 360; deg += 15) {
            float diff = Mth.wrapDegrees(deg - bearing);
            int x = cx + Math.round(diff * pxPerDeg);
            int dist = Math.abs(x - cx);
            if (dist > half) continue;
            int a = Math.max(24, (int) (255 * (1f - dist / (float) half)));
            int col = (a << 24) | 0xFFFFFF;
            String label = switch (deg) {
                case 0 -> "N"; case 45 -> "NE"; case 90 -> "E"; case 135 -> "SE";
                case 180 -> "S"; case 225 -> "SW"; case 270 -> "W"; case 315 -> "NW";
                default -> null;
            };
            if (label != null) {
                g.drawString(font, label, x - font.width(label) / 2, y, col, false);
            } else {
                g.fill(x, y + 3, x + 1, y + 6, col);
            }
        }
        // center readout
        g.fill(cx - 15, y - 2, cx + 15, y + 11, PANEL_DARK);
        String deg = String.valueOf((int) bearing);
        g.drawString(font, deg, cx - font.width(deg) / 2, y, ACCENT, false);
        g.fill(cx - 1, y + 12, cx + 1, y + 15, ACCENT);
    }

    // ------------------------------------------------------------------- squad
    /** Your own player block (name, health, food, armor). */
    private static void drawSelf(GuiGraphics g, Font font, LocalPlayer self, int h) {
        drawRow(g, font, 6, h - 6 - 34, 140, 1, self.getGameProfile().getName(), self, true);
    }

    // --------------------------------------------------------------- contracts
    static int contractColor(String type) {
        return switch (type) {
            case "SCAVENGER" -> 0xFFE6B800;
            case "PROTECT" -> 0xFFE05C5C;
            case "BOUNTY" -> 0xFFE05CFF;
            default -> SQUAD[Math.abs(type.hashCode()) % SQUAD.length];
        };
    }

    /** Contract panels stacked on the left, directly above your player block. */
    private static void drawContracts(GuiGraphics g, Font font, int h) {
        List<ContractView> list = ClientContracts.list;
        if (list.isEmpty()) return;

        final int x = 6, w = 140, ch = 32, gap = 3;
        int shown = Math.min(3, list.size());
        int bottom = h - 6 - 34 - 6;                           // just above the player block
        int top = bottom - shown * ch - (shown - 1) * gap;

        for (int i = 0; i < shown; i++) {
            drawContract(g, font, list.get(i), x, top + i * (ch + gap), w, ch);
        }
        if (list.size() > shown) {
            String more = "+" + (list.size() - shown) + " MORE";
            g.drawString(font, more, x + 2, top - 10, DIM, false);
        }
    }

    private static void drawContract(GuiGraphics g, Font font, ContractView c, int x, int y, int w, int h) {
        int col = contractColor(c.type());
        g.fill(x, y, x + w, y + h, PANEL);
        g.fill(x, y, x + w, y + 1, col);                        // accent line on top

        g.drawString(font, c.type(), x + 6, y + 4, col, false);
        int rem = ClientContracts.remaining(c);
        if (rem >= 0) {
            String t = (rem / 60) + ":" + String.format("%02d", rem % 60);
            g.drawString(font, t, x + w - 6 - font.width(t), y + 4, rem <= 10 ? 0xFFFF4A3A : TEXT, false);
        }

        String reward = c.reward() > 0 ? c.reward() + " $" : "";
        int rewardW = reward.isEmpty() ? 0 : font.width(reward) + 6;
        String desc = (c.target() > 1 ? c.progress() + "/" + c.target() + " " : "") + c.description();
        g.drawString(font, font.plainSubstrByWidth(desc, w - 12 - rewardW), x + 6, y + 15, TEXT, false);
        if (!reward.isEmpty()) {
            g.drawString(font, reward, x + w - 6 - font.width(reward), y + 15, TEXT, false);
        }

        if (c.target() > 1) {
            int bw = w - 12;
            g.fill(x + 6, y + 25, x + 6 + bw, y + 28, 0x60000000);
            g.fill(x + 6, y + 25, x + 6 + (int) (bw * Mth.clamp(c.progress() / (float) c.target(), 0f, 1f)), y + 28, col);
        }
    }

    static void drawRow(GuiGraphics g, Font font, int x, int y, int w, int index,
                        String name, Player ent, boolean self) {
        drawRow(g, font, x, y, w, index, name, ent, self, false, null);
    }

    /**
     * compact = narrow squad-page cell: name, health bar and the class (tag) instead of food/armor bars;
     * no level readout, long names are cut to fit.
     */
    static void drawRow(GuiGraphics g, Font font, int x, int y, int w, int index,
                        String name, Player ent, boolean self, boolean compact, String tag) {
        int col = SQUAD[(index - 1) % SQUAD.length];
        g.fill(x, y, x + w, y + 32, PANEL);
        if (self && HudState.damageFlash > 0f) {
            g.fill(x, y, x + w, y + 32, ((int) (HudState.damageFlash * 80f) << 24) | 0xFF2020);
        }
        g.fill(x, y, x + 2, y + 32, col);
        int nameRoom = w - 14 - (self && !compact ? font.width("LV 00") + 4 : 0);
        g.drawString(font, font.plainSubstrByWidth(index + "  " + name.toUpperCase(), nameRoom), x + 7, y + 3, col, false);

        int bx = x + 7, bw = w - 14;
        // bar order: 1) health (green)  2) food  3) armor (white)
        final int yHealth = y + 13, yFood = y + 19, yArmor = y + 25;

        if (ent == null) {
            // teammate out of render range: nothing is known
            g.fill(bx, yHealth, bx + bw, yHealth + 4, 0x30FFFFFF);
            if (compact) {
                drawTag(g, font, tag, bx, y + 20, bw);
            } else {
                String away = "OUT OF RANGE";
                g.drawString(font, away, x + w - 6 - font.width(away), y + 3, DIM, false);
            }
            return;
        }

        float hp, trail, foodF, armorF, flash = 0f;
        if (self) {
            hp = HudState.health;
            trail = HudState.healthTrail;
            foodF = HudState.food;
            armorF = HudState.armor;
            flash = HudState.damageFlash;
        } else {
            hp = Mth.clamp(ent.getHealth() / Math.max(1f, ent.getMaxHealth()), 0f, 1f);
            trail = hp;
            foodF = 0f;
            armorF = Mth.clamp(ent.getArmorValue() / 20f, 0f, 1f);
        }

        int hpEnd = bx + (int) (bw * hp);
        g.fill(bx, yHealth, bx + bw, yHealth + 4, 0x60000000);
        if (trail > hp) {
            g.fill(hpEnd, yHealth, bx + (int) (bw * trail), yHealth + 4, 0xCCFFFFFF);   // damage trail
        }
        g.fill(bx, yHealth, hpEnd, yHealth + 4, HEALTH);
        if (flash > 0f) {
            g.fill(bx, yHealth, hpEnd, yHealth + 4, ((int) (flash * 190f) << 24) | 0xFFFFFF);   // hit flash
        }

        if (compact) {
            drawTag(g, font, tag, bx, y + 20, bw);
            return;
        }

        // Food level is not synced for other players, so only your own row has real data.
        g.fill(bx, yFood, bx + bw, yFood + 3, 0x40000000);
        if (self) {
            g.fill(bx, yFood, bx + (int) (bw * foodF), yFood + 3, FOOD);
            if (!compact) {
                String lvl = "LV " + ent.experienceLevel;
                g.drawString(font, lvl, x + w - 6 - font.width(lvl), y + 3, DIM, false);
            }
        }

        g.fill(bx, yArmor, bx + bw, yArmor + 3, 0x40000000);
        g.fill(bx, yArmor, bx + (int) (bw * armorF), yArmor + 3, ARMOR);
    }

    /** Class name line of a squad cell: blue when set, dim "NO CLASS" otherwise. */
    private static void drawTag(GuiGraphics g, Font font, String tag, int x, int y, int maxW) {
        boolean none = tag == null || tag.isEmpty();
        g.drawString(font, font.plainSubstrByWidth(none ? "NO CLASS" : tag, maxW), x, y, none ? DIM : 0xFF2EA8FF, false);
    }

    // ------------------------------------------------------------------ hotbar
    private static void drawHotbar(GuiGraphics g, Font font, Player p, int w, int h) {
        int slot = 20;
        int total = slot * 9;
        int x0 = w / 2 - total / 2;
        int y0 = h - slot - 6;
        g.fill(x0 - 2, y0 - 2, x0 + total + 2, y0 + slot + 2, PANEL);
        for (int i = 0; i < 9; i++) {
            g.fill(x0 + i * slot, y0, x0 + i * slot + slot - 1, y0 + slot, 0x40000000);
        }
        // selector slides between slots
        int sx = x0 + Math.round(HudState.selector * slot);
        g.fill(sx, y0, sx + slot - 1, y0 + slot, 0x80FFB400);
        g.fill(sx, y0 + slot - 2, sx + slot - 1, y0 + slot, ACCENT);

        for (int i = 0; i < 9; i++) {
            int x = x0 + i * slot;
            ItemStack st = p.getInventory().items.get(i);
            if (!st.isEmpty()) {
                g.renderItem(st, x + 1, y0 + 2);
                g.renderItemDecorations(font, st, x + 1, y0 + 2);
            }
            g.drawString(font, String.valueOf(i + 1), x + 2, y0 + 1, DIM, false);
        }
    }

    // ------------------------------------------------------------------ weapon
    private static Boolean taczLoaded;

    private static boolean taczLoaded() {
        if (taczLoaded == null) taczLoaded = ModList.get().isLoaded("tacz");
        return taczLoaded;
    }

    private static void drawWeapon(GuiGraphics g, Font font, Player p, int w, int h) {
        ItemStack st = p.getMainHandItem();
        if (st.isEmpty()) {
            HudState.clearMag();
            return;
        }

        String name = st.getHoverName().getString().toUpperCase();
        String big = "", small = "", mode = "", reserve = "";
        boolean chambered = false;

        TaczCompat.GunInfo gi = taczLoaded() ? TaczCompat.read(p, st) : null;
        if (gi != null) {
            big = String.valueOf(gi.mag());
            small = "/ " + gi.maxMag();                      // magazine capacity
            reserve = gi.invAmmo() ? "" : (gi.reserve() < 0 ? "INF" : String.valueOf(gi.reserve()));
            mode = gi.mode();
            chambered = gi.chambered();
            HudState.noteMag(gi.mag());
        } else {
            HudState.clearMag();
            if (st.getCount() > 1) big = String.valueOf(st.getCount());
        }
        boolean gun = gi != null;

        // Ammo state, using TACZ's own rules: its HUD counts mag + a chambered round (not for open-bolt guns),
        // and flags "low" below 25% of capacity when that total is also under 10.
        boolean noAmmo = false, empty = false, low = false;
        int bigColor = TEXT;
        if (gun) {
            int total = gi.mag() + (chambered ? 1 : 0);
            noAmmo = total <= 0 && (gi.invAmmo() || gi.reserve() == 0);    // nothing left anywhere
            empty = total <= 0 && !noAmmo;                                  // can (and should) reload; -1 = infinite
            low = !noAmmo && !empty && gi.maxMag() > 0 && total < gi.maxMag() * 0.25f && total < 10;
            if (noAmmo || empty) bigColor = 0xFFFF4A3A;
            else if (low) bigColor = 0xFFFF9A2E;
        }

        // panel width follows the content: guns keep a 160 minimum, other items shrink to fit their name
        final float S = 1.8f;
        int bigW = (int) (font.width(big) * S);
        int smallW = small.isEmpty() ? 0 : font.width(small) + 3;
        int textW = Math.max(font.width(name), bigW + smallW);
        int pw = Math.max(gun ? 160 : 64, 38 + textW + 6);
        int ph = gun ? 48 : 40;
        int x = w - pw - 6, y = h - ph - 6;

        // slide in when the held item changes
        float slide = (1f - HudState.easeOut(HudState.weaponIn)) * 40f;
        g.pose().pushPose();
        g.pose().translate(slide, 0f, 0f);

        g.fill(x, y, x + pw, y + ph, PANEL);
        g.fill(x, y, x + 2, y + ph, ACCENT);

        g.renderItem(st, x + 8, y + 12);
        g.drawString(font, name, x + pw - 6 - font.width(name), y + 4, TEXT, false);

        int bigRight = x + pw - 8 - smallW;   // right edge of the big number
        if (!big.isEmpty()) {
            float scale = S * (1f + 0.18f * HudState.ammoPop);   // pops when the count changes
            g.pose().pushPose();
            g.pose().translate(bigRight, y + 17, 0f);
            g.pose().scale(scale, scale, 1f);
            g.drawString(font, big, -font.width(big), 0, bigColor, false);
            g.pose().popPose();
            if (!small.isEmpty()) {
                g.drawString(font, small, x + pw - 6 - font.width(small), y + 25, DIM, false);
            }
        }
        if (chambered) {
            // grey "+1" under the ammo count
            g.drawString(font, "+1", bigRight - font.width("+1"), y + 33, DIM, false);
        }
        if (!mode.isEmpty()) {
            g.drawString(font, mode, x + 30, y + 26, ACCENT, false);
        }
        if (noAmmo) {
            g.drawString(font, "NO AMMO", x + 30, y + 37, 0xFFFF4A3A, false);
        } else if (!reserve.isEmpty()) {
            // total ammo carried
            g.drawString(font, "RESERVE", x + 30, y + 37, DIM, false);
            g.drawString(font, reserve, x + 30 + font.width("RESERVE ") + 2, y + 37, TEXT, false);
        }
        g.pose().popPose();

        // pulsing alert under the crosshair
        if (gun && HudConfig.LOW_AMMO_ALERT.get() && (noAmmo || empty || low)) {
            String msg = noAmmo ? "NO AMMO" : (empty ? "RELOAD" : "LOW AMMO");
            boolean red = noAmmo || empty;
            float pulse = 0.5f + 0.5f * (float) Math.sin(System.nanoTime() / 1.0e9 * (red ? 8.0 : 4.0));
            int alpha = 120 + (int) (135f * pulse);
            int col = (alpha << 24) | (red ? 0xFF4A3A : 0xFF9A2E);
            g.drawString(font, msg, w / 2 - font.width(msg) / 2, h / 2 + 22, col, true);
        }
    }
}
