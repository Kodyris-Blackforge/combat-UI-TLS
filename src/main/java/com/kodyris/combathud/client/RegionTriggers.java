package com.kodyris.combathud.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kodyris.combathud.CombatHud;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ways to trigger a region title, all client-side so it works on any server:
 *  1. config/combathud-regions.json  - boxes that fire when you walk into them
 *  2. a chat/tellraw line starting with "[combathud:title]"  - fire from command blocks / functions / plugins
 *  3. /combathud title <text>  - manual testing
 */
@Mod.EventBusSubscriber(modid = CombatHud.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
final class RegionTriggers {
    private RegionTriggers() {}

    static final String CHAT_PREFIX = "[combathud:title]";
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("combathud-regions.json");
    private static final String SAMPLE = """
            [
              {
                "id": "example-sector-2",
                "enabled": false,
                "dimension": "minecraft:overworld",
                "min": [0, 60, 0],
                "max": [32, 90, 32],
                "label": "NOW ENTERING",
                "name": "Sector 2"
              }
            ]
            """;

    private record Region(String id, String dimension, double[] min, double[] max, String label, String name) {}

    private static List<Region> regions = List.of();
    private static boolean loaded;
    private static final Set<String> inside = new HashSet<>();
    private static final Map<String, Long> lastShown = new HashMap<>();

    // ------------------------------------------------------------------ loading
    private static void load() {
        List<Region> out = new ArrayList<>();
        try {
            if (!Files.exists(FILE)) Files.writeString(FILE, SAMPLE);
            JsonArray arr = JsonParser.parseString(Files.readString(FILE)).getAsJsonArray();
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                if (o.has("enabled") && !o.get("enabled").getAsBoolean()) continue;
                double[] a = vec(o.getAsJsonArray("min")), b = vec(o.getAsJsonArray("max"));
                out.add(new Region(
                        str(o, "id", "region" + out.size()),
                        str(o, "dimension", ""),
                        new double[]{Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2])},
                        new double[]{Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2])},
                        str(o, "label", "NOW ENTERING"),
                        str(o, "name", "")));
            }
        } catch (Exception e) {
            CombatHud.LOGGER.warn("[CombatHud] could not read {}: {}", FILE, e.toString());
        }
        regions = out;
        loaded = true;
        inside.clear();
    }

    private static double[] vec(JsonArray a) {
        return new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()};
    }

    private static String str(JsonObject o, String key, String def) {
        return o.has(key) ? o.get(key).getAsString() : def;
    }

    // ------------------------------------------------------------------ walk-in regions
    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            inside.clear();
            return;
        }
        if (!loaded) load();
        if (regions.isEmpty()) return;

        String dim = mc.level.dimension().location().toString();
        Vec3 p = mc.player.position();
        long now = System.currentTimeMillis();

        for (Region r : regions) {
            boolean in = (r.dimension().isEmpty() || r.dimension().equals(dim))
                    && p.x >= r.min()[0] && p.x <= r.max()[0]
                    && p.y >= r.min()[1] && p.y <= r.max()[1]
                    && p.z >= r.min()[2] && p.z <= r.max()[2];
            if (in) {
                if (inside.add(r.id())) {   // just entered
                    Long last = lastShown.get(r.id());
                    if (last == null || now - last > 10_000L) {   // 10 s cooldown against border flicker
                        lastShown.put(r.id(), now);
                        TitleBanner.show(r.label(), r.name());
                    }
                }
            } else {
                inside.remove(r.id());
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        inside.clear();
        loaded = false;   // re-read the file next time you join
    }

    // ------------------------------------------------------------------ chat / tellraw trigger
    @SubscribeEvent
    public static void onChat(ClientChatReceivedEvent e) {
        String s = e.getMessage().getString().trim();
        if (!s.startsWith(CHAT_PREFIX)) return;
        e.setCanceled(true);   // keep the trigger line out of chat
        parseAndShow(s.substring(CHAT_PREFIX.length()));
    }

    /** "Sector 2"  ->  NOW ENTERING / SECTOR 2.   "NOW LEAVING | Sector 2"  ->  custom first line. */
    private static void parseAndShow(String body) {
        String label = "NOW ENTERING", name = body.trim();
        int bar = name.indexOf('|');
        if (bar >= 0) {
            label = name.substring(0, bar).trim();
            name = name.substring(bar + 1).trim();
        }
        TitleBanner.show(label, name);
    }

    // ------------------------------------------------------------------ /combathud
    @SubscribeEvent
    public static void onCommands(RegisterClientCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("combathud")
                .then(Commands.literal("title")
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(c -> {
                                    parseAndShow(StringArgumentType.getString(c, "text"));
                                    return 1;
                                })))
                .then(Commands.literal("reload")
                        .executes(c -> {
                            load();
                            int n = regions.size();
                            c.getSource().sendSuccess(() -> Component.literal("Combat HUD: loaded " + n + " region(s)"), false);
                            return 1;
                        })));
    }
}
