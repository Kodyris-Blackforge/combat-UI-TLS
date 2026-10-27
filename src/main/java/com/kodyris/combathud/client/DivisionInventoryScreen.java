package com.kodyris.combathud.client;

import com.kodyris.combathud.HudConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Division-style survival inventory.
 *
 * Phase 1 keeps vanilla's slot positions (Slot.x / Slot.y are final in 1.20.1), and re-skins
 * everything around them: dark backdrop, per-slot tiles, stat boxes, vitals and weapon panels.
 * Phase 2 (free layout) needs an Access Transformer on Slot.x/y or a custom menu.
 */
public class DivisionInventoryScreen extends AbstractContainerScreen<InventoryMenu> {
    private static final int BG        = 0xC00A0C0F;
    private static final int PANEL     = 0xC0141820;
    private static final int TILE      = 0xA0262B33;
    private static final int TILE_GEAR = 0xA02E7D4F;   // armor slots, Division "gear" green
    private static final int BORDER    = 0x30FFFFFF;
    private static final int ORANGE    = 0xFFFF8A00;
    private static final int TEXT      = 0xFFE8E8E8;
    private static final int DIM       = 0xFF8A9096;

    private static final String[] TABS = {"OVERVIEW", "CHARACTER", "POUCHES", "APPEARANCE", "SQUAD"};
    private static final int TAB_OVERVIEW = 0, TAB_SQUAD = 4;

    private final Player player;
    private int tab = TAB_OVERVIEW;
    private final long openedAt = System.nanoTime();

    public DivisionInventoryScreen(Player player) {
        super(player.inventoryMenu, player.getInventory(), Component.translatable("container.crafting"));
        this.player = player;
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    /** 0..1 open animation progress (eased). */
    private float openEase() {
        if (!HudConfig.ANIMATIONS.get()) return 1f;
        float t = Mth.clamp((System.nanoTime() - openedAt) / 1.8e8f, 0f, 1f);
        return HudState.easeOut(t);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float e = openEase();
        this.renderBackground(g);
        g.fill(0, 0, this.width, this.height, ((int) (0xC0 * e) << 24) | 0x0A0C0F);   // backdrop fades in

        g.pose().pushPose();
        g.pose().translate(0f, (1f - e) * 14f, 0f);                                   // panels slide up
        super.render(g, mouseX, mouseY, partialTick);
        if (this.tab == TAB_SQUAD) {
            g.pose().pushPose();
            g.pose().translate(0f, 0f, 1500f);   // in front of slot items (~150) and the player model (~1050)
            drawSquadPage(g);
            g.pose().popPose();
        }
        g.pose().popPose();

        if (this.tab != TAB_SQUAD) this.renderTooltip(g, mouseX, mouseY);
    }

    // ------------------------------------------------------------------ tab input
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            int[][] r = tabLayout();
            int y = tabsY();
            for (int i = 0; i < r.length; i++) {
                if (mx >= r[i][0] && mx < r[i][0] + r[i][1] && my >= y && my < y + 12) {
                    if ((i == TAB_OVERVIEW || i == TAB_SQUAD) && this.tab != i) {
                        this.tab = i;
                        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    }
                    return true;
                }
            }
        }
        if (this.tab == TAB_SQUAD) return true;   // no slot interaction behind the squad page
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return this.tab == TAB_SQUAD || super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return this.tab == TAB_SQUAD || super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (this.tab == TAB_SQUAD) {
            // hotbar-swap keys would act on hidden slots, so only allow closing
            if (key == 256 || this.minecraft.options.keyInventory.matches(key, scan)) this.onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // vanilla "Crafting" / "Inventory" labels intentionally removed
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int l = this.leftPos, t = this.topPos;

        drawTabs(g);

        // slot tiles
        for (int i = 0; i < this.menu.slots.size(); i++) {
            Slot s = this.menu.slots.get(i);
            boolean armor = i >= 5 && i <= 8;
            int x = l + s.x - 1, y = t + s.y - 1;
            g.fill(x, y, x + 18, y + 18, armor ? TILE_GEAR : TILE);
            outline(g, x, y, 18, 18, BORDER);
        }

        // player model (same call vanilla uses)
        InventoryScreen.renderEntityInInventoryFollowsMouse(
                g, l + 51, t + 75, 30, (float) (l + 51) - mouseX, (float) (t + 75 - 50) - mouseY, this.player);

        int sideTop = sideTop();
        drawVitals(g, l - 150, sideTop, 140);
        drawWeaponPanel(g, l + 186, sideTop, 130);
    }

    // --------------------------------------------------------------- sections
    /** Tab row: about a quarter of the way down the screen, but never overlapping the main panel. */
    private int tabsY() {
        return Math.max(4, Math.min(this.height / 4, this.topPos - 16));
    }

    /** Top of the vitals / in-hand panels: the lower quarter of the screen, kept fully on screen. */
    private int sideTop() {
        return Math.min((int) (this.height * 0.75f), this.height - 84 - 6);
    }

    private int tabsWidth(int pad) {
        int total = 0;
        for (String tab : TABS) total += this.font.width(tab) + pad * 2;
        return total + 3 * (TABS.length - 1);
    }

    /** {x, width} per tab, the whole row centred on the screen (padding shrinks on very small screens). */
    private int[][] tabLayout() {
        int pad = 5;
        if (tabsWidth(pad) > this.width - 8) pad = 2;
        int[][] out = new int[TABS.length][2];
        int x = (this.width - tabsWidth(pad)) / 2;
        for (int i = 0; i < TABS.length; i++) {
            int w = this.font.width(TABS[i]) + pad * 2;
            out[i][0] = x;
            out[i][1] = w;
            x += w + 3;
        }
        return out;
    }

    private void drawTabs(GuiGraphics g) {
        int y = tabsY();
        int[][] r = tabLayout();

        // page title hangs off the left of the centred tab row
        int lw = this.font.width("INVENTORY");
        int lx = r[0][0] - 12 - lw;
        g.fill(lx - 6, y, lx - 4, y + 12, ORANGE);
        g.drawString(this.font, "INVENTORY", lx, y + 2, TEXT, false);

        for (int i = 0; i < TABS.length; i++) {
            boolean active = i == this.tab;
            g.fill(r[i][0], y, r[i][0] + r[i][1], y + 12, active ? 0xC0303640 : 0x80181C22);
            if (active) g.fill(r[i][0], y, r[i][0] + r[i][1], y + 1, ORANGE);
            int pad = (r[i][1] - this.font.width(TABS[i])) / 2;
            g.drawString(this.font, TABS[i], r[i][0] + pad, y + 2, active ? TEXT : DIM, false);
        }
    }

    // ------------------------------------------------------------------ squad page
    /** Everyone online except you. Scoreboard teams are classes in this pack, so they say nothing about squads. */
    private List<PlayerInfo> roster() {
        List<PlayerInfo> out = new ArrayList<>();
        ClientPacketListener conn = this.minecraft.getConnection();
        if (conn == null) return out;
        for (PlayerInfo info : conn.getOnlinePlayers()) {
            if (!info.getProfile().getId().equals(this.player.getUUID())) out.add(info);
        }
        return out;
    }

    /** The player's class = their scoreboard team's display name (id cleaned up if no display name was set). */
    private static String classOf(PlayerInfo info) {
        PlayerTeam team = info == null ? null : info.getTeam();
        if (team == null) return "";
        String id = team.getName();
        String shown = team.getDisplayName().getString().trim();
        if (shown.isEmpty() || shown.equals(id)) shown = id.replace('_', ' ').replace('-', ' ').trim();
        return shown;
    }

    private void drawSquadPage(GuiGraphics g) {
        int l = this.leftPos, t = this.topPos;
        g.fill(l - 2, t - 2, l + 178, t + 168, 0xFF0E1116);        // opaque: hides the slots underneath
        outline(g, l - 2, t - 2, 180, 170, BORDER);
        g.fill(l + 4, t + 4, l + 6, t + 16, ORANGE);
        g.drawString(this.font, "SQUAD", l + 10, t + 6, TEXT, false);

        final int max = HudConfig.SQUAD_MAX.get();                 // 1..8 slots
        List<PlayerInfo> others = roster();
        int shown = Math.min(max - 1, others.size());

        String count = (shown + 1) + " / " + max;
        g.drawString(this.font, count, l + 170 - this.font.width(count), t + 6, DIM, false);

        // two columns of four, filled top to bottom: slots 1-4 left, 5-8 right
        final int cw = 84, rowStep = 33;
        for (int slot = 0; slot < max; slot++) {
            int x = l + 2 + (slot / 4) * (cw + 4);
            int y = t + 22 + (slot % 4) * rowStep;
            if (slot == 0) {
                ClientPacketListener conn = this.minecraft.getConnection();
                PlayerInfo me = conn == null ? null : conn.getPlayerInfo(this.player.getUUID());
                CombatHudOverlay.drawRow(g, this.font, x, y, cw, 1,
                        this.player.getGameProfile().getName(), this.player, true, true, classOf(me));
            } else if (slot <= shown) {
                PlayerInfo info = others.get(slot - 1);
                Player ent = this.minecraft.level.getPlayerByUUID(info.getProfile().getId());
                CombatHudOverlay.drawRow(g, this.font, x, y, cw, slot + 1, info.getProfile().getName(), ent, false, true, classOf(info));
            } else {
                g.fill(x, y, x + cw, y + 32, 0x50141820);
                outline(g, x, y, cw, 32, 0x20FFFFFF);
                g.drawString(this.font, (slot + 1) + "  OPEN SLOT", x + 7, y + 12, DIM, false);
            }
        }

        String foot = "SHOWING ALL ONLINE PLAYERS";
        if (others.size() > shown) foot += "   +" + (others.size() - shown) + " MORE";
        g.drawString(this.font, foot, l + 8, t + 156, DIM, false);
    }

    private void drawVitals(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 84, PANEL);
        outline(g, x, y, w, 84, BORDER);
        g.fill(x, y, x + 2, y + 12, ORANGE);
        g.drawString(this.font, "VITALS", x + 7, y + 3, TEXT, false);

        int armor = this.player.getArmorValue();
        long toughness = Math.round(this.player.getAttributeValue(Attributes.ARMOR_TOUGHNESS));

        bar(g, x + 7, y + 26, w - 14, HudState.health, HudState.healthTrail, 0xFF3DDC6B,
                "HEALTH  " + (int) Math.ceil(this.player.getHealth()) + "/" + (int) this.player.getMaxHealth());
        bar(g, x + 7, y + 42, w - 14, HudState.food, HudState.food, 0xFFFF8A2E,
                "FOOD  " + this.player.getFoodData().getFoodLevel());
        bar(g, x + 7, y + 58, w - 14, HudState.armor, HudState.armor, 0xFFEFEFEF,
                "ARMOR  " + armor + " / TOUGHNESS  " + toughness);
        g.drawString(this.font, "LEVEL  " + this.player.experienceLevel, x + 7, y + 72, DIM, false);
    }

    private void bar(GuiGraphics g, int x, int y, int w, float frac, float trail, int color, String label) {
        g.drawString(this.font, label, x, y - 9, DIM, false);
        g.fill(x, y, x + w, y + 4, 0x60000000);
        int end = x + (int) (w * Mth.clamp(frac, 0f, 1f));
        if (trail > frac) {
            g.fill(end, y, x + (int) (w * Mth.clamp(trail, 0f, 1f)), y + 4, 0xCCFFFFFF);
        }
        g.fill(x, y, end, y + 4, color);
    }

    private void drawWeaponPanel(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 60, PANEL);
        outline(g, x, y, w, 60, BORDER);
        g.fill(x, y, x + 2, y + 12, ORANGE);
        g.drawString(this.font, "IN HAND", x + 7, y + 3, TEXT, false);

        ItemStack main = this.player.getMainHandItem();
        ItemStack off = this.player.getOffhandItem();
        drawHandRow(g, x + 7, y + 18, "MAIN", main);
        drawHandRow(g, x + 7, y + 38, "OFF", off);
    }

    private void drawHandRow(GuiGraphics g, int x, int y, String tag, ItemStack st) {
        g.drawString(this.font, tag, x, y + 4, DIM, false);
        if (st.isEmpty()) {
            g.drawString(this.font, "--", x + 30, y + 4, DIM, false);
            return;
        }
        g.renderItem(st, x + 28, y);
        String name = st.getHoverName().getString();
        if (this.font.width(name) > 70) name = this.font.plainSubstrByWidth(name, 66) + "...";
        g.drawString(this.font, name, x + 48, y + 4, TEXT, false);
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
    }
}
