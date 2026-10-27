package com.kodyris.combathud.contract;

import com.kodyris.combathud.CombatHud;
import com.kodyris.combathud.net.Net;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * In this pack scoreboard teams are classes. When a player is put on a different team (/team join ...)
 * they get a blue "CLASS SELECTED" banner with the class display name. Checked once a second;
 * the class a player already has at login is never announced, and nobody else is notified.
 */
@Mod.EventBusSubscriber(modid = CombatHud.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TeamEvents {
    private TeamEvents() {}

    private static final Map<UUID, String> LAST = new HashMap<>();

    private static String teamName(ServerPlayer p) {
        Team t = p.getTeam();
        return t == null ? "" : t.getName();
    }

    /**
     * The class's display name (/team modify <team> displayName ...), as plain text without colour codes.
     * If none was set the display name equals the team id, so the id is made readable instead.
     */
    private static String classDisplay(Team team, String id) {
        if (team instanceof PlayerTeam pt) {
            String shown = pt.getDisplayName().getString().trim();
            if (!shown.isEmpty() && !shown.equals(id)) return shown;
        }
        return id.replace('_', ' ').replace('-', ' ').trim();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) LAST.put(p.getUUID(), teamName(p));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        LAST.remove(e.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getTickCount() % 20 != 5) return;   // offset from the contract check

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            String now = teamName(p);
            String before = LAST.put(p.getUUID(), now);
            if (before == null || now.isEmpty() || now.equals(before)) continue;   // first sighting, no class, or unchanged

            Net.title(p, "CLASS SELECTED", classDisplay(p.getTeam(), now), "team");
        }
    }
}
