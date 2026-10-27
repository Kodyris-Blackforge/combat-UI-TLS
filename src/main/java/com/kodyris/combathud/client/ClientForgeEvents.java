package com.kodyris.combathud.client;

import com.kodyris.combathud.CombatHud;
import com.kodyris.combathud.HudConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.NamedGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;

@Mod.EventBusSubscriber(modid = CombatHud.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientForgeEvents {
    private ClientForgeEvents() {}

    private static final Set<ResourceLocation> SEEN = new HashSet<>();

    /** Vanilla overlays our HUD replaces. */
    private static boolean isVanillaHidden(NamedGuiOverlay o) {
        return o == VanillaGuiOverlay.HOTBAR.type()
                || o == VanillaGuiOverlay.PLAYER_HEALTH.type()
                || o == VanillaGuiOverlay.ARMOR_LEVEL.type()
                || o == VanillaGuiOverlay.FOOD_LEVEL.type()
                || o == VanillaGuiOverlay.EXPERIENCE_BAR.type()
                || o == VanillaGuiOverlay.ITEM_NAME.type();
    }

    /** Other mods' overlays (e.g. TACZ's ammo HUD), matched against the config list. */
    private static boolean isConfigHidden(ResourceLocation id) {
        String s = id.toString();
        for (String entry : HudConfig.HIDDEN_OVERLAYS.get()) {
            if (entry.endsWith("*") ? s.startsWith(entry.substring(0, entry.length() - 1)) : s.equals(entry)) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onOverlayPre(RenderGuiOverlayEvent.Pre event) {
        ResourceLocation id = event.getOverlay().id();

        if (HudConfig.LOG_OVERLAYS.get() && SEEN.add(id)) {
            CombatHud.LOGGER.info("[CombatHud] GUI overlay: {}", id);
        }

        if (!CombatHudOverlay.isActive()) return;
        if (isVanillaHidden(event.getOverlay()) || isConfigHidden(id)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientContracts.set(java.util.List.of());
    }

    /** Swap the vanilla survival inventory for ours (creative keeps its own screen). */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (event.getNewScreen() != null
                && event.getNewScreen().getClass() == InventoryScreen.class
                && CombatHudOverlay.isActive()) {
            event.setNewScreen(new DivisionInventoryScreen(mc.player));
        }
    }
}
