package com.kodyris.combathud.contract;

import com.kodyris.combathud.ServerConfig;
import com.kodyris.combathud.net.ContractSyncPacket;
import com.kodyris.combathud.net.Net;
import com.kodyris.combathud.CombatHud;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player contracts. Kept in memory while the player is online and mirrored into the player's
 * persistent data on every change, so they survive relogs and restarts.
 */
public final class ContractManager {
    private ContractManager() {}

    private static final String KEY = "combathud_contracts";
    private static final Map<UUID, List<Contract>> CACHE = new HashMap<>();

    // ------------------------------------------------------------------ storage
    private static List<Contract> list(ServerPlayer p) {
        return CACHE.computeIfAbsent(p.getUUID(), id -> load(p));
    }

    private static List<Contract> load(ServerPlayer p) {
        List<Contract> out = new ArrayList<>();
        CompoundTag data = p.getPersistentData();
        if (data.contains(KEY, Tag.TAG_LIST)) {
            ListTag lt = data.getList(KEY, Tag.TAG_COMPOUND);
            for (int i = 0; i < lt.size(); i++) out.add(Contract.fromTag(lt.getCompound(i)));
        }
        return out;
    }

    public static void save(ServerPlayer p) {
        ListTag lt = new ListTag();
        for (Contract c : list(p)) lt.add(c.toTag());
        p.getPersistentData().put(KEY, lt);
    }

    public static void logout(ServerPlayer p) {
        save(p);
        CACHE.remove(p.getUUID());
    }

    public static void sync(ServerPlayer p) {
        long now = p.serverLevel().getGameTime();
        List<ContractView> views = new ArrayList<>();
        for (Contract c : list(p)) {
            int rem = c.expiresAt() < 0 ? -1 : (int) Math.max(0L, (c.expiresAt() - now) / 20L);
            views.add(new ContractView(c.id(), c.type(), c.description(), c.progress(), c.target(), c.reward(), rem));
        }
        Net.toPlayer(p, new ContractSyncPacket(views));
    }

    private static void changed(ServerPlayer p) {
        save(p);
        sync(p);
    }

    private static int indexOf(List<Contract> l, String id) {
        for (int i = 0; i < l.size(); i++) if (l.get(i).id().equals(id)) return i;
        return -1;
    }

    // ------------------------------------------------------------------ operations
    /** Adds (or replaces) a contract. seconds < 0 means no timer. */
    public static void add(ServerPlayer p, String id, String type, String desc, int target, int reward, int seconds) {
        long expires = seconds < 0 ? -1L : p.serverLevel().getGameTime() + seconds * 20L;
        String t = type.toUpperCase(Locale.ROOT);
        List<Contract> l = list(p);
        l.removeIf(c -> c.id().equals(id));
        l.add(new Contract(id, t, desc, 0, Math.max(1, target), Math.max(0, reward), expires, "", ""));
        changed(p);
        Net.title(p, "CONTRACT ACCEPTED", t);
    }

    /** Adds amount to the progress; completes the contract when it reaches the target. */
    public static boolean progress(ServerPlayer p, String id, int amount) {
        List<Contract> l = list(p);
        int i = indexOf(l, id);
        if (i < 0) return false;
        return set(p, id, l.get(i).progress() + amount);
    }

    public static boolean set(ServerPlayer p, String id, int value) {
        List<Contract> l = list(p);
        int i = indexOf(l, id);
        if (i < 0) return false;
        Contract c = l.get(i).withProgress(value);
        l.set(i, c);
        if (c.done()) return complete(p, id);
        changed(p);
        return true;
    }

    public static boolean complete(ServerPlayer p, String id) {
        List<Contract> l = list(p);
        int i = indexOf(l, id);
        if (i < 0) return false;
        Contract c = l.remove(i);
        payout(p, c.reward());
        changed(p);
        Net.title(p, "CONTRACT COMPLETE", c.reward() > 0 ? c.type() + "  +" + c.reward() + " $" : c.type(), "complete");
        run(p, c.onFinish(), c);
        return true;
    }

    public static boolean fail(ServerPlayer p, String id) {
        List<Contract> l = list(p);
        int i = indexOf(l, id);
        if (i < 0) return false;
        Contract c = l.remove(i);
        changed(p);
        Net.title(p, "CONTRACT FAILED", c.type(), "fail");
        run(p, c.onFail(), c);
        return true;
    }

    public static boolean remove(ServerPlayer p, String id) {
        List<Contract> l = list(p);
        int i = indexOf(l, id);
        if (i < 0) return false;
        l.remove(i);
        changed(p);
        return true;
    }

    public static boolean clear(ServerPlayer p) {
        List<Contract> l = list(p);
        if (l.isEmpty()) return false;
        l.clear();
        changed(p);
        return true;
    }

    /** Fails contracts whose timer ran out. Called about once a second. */
    public static void checkExpiry(ServerPlayer p) {
        long now = p.serverLevel().getGameTime();
        List<String> expired = new ArrayList<>();
        for (Contract c : list(p)) {
            if (c.expiresAt() >= 0 && now >= c.expiresAt()) expired.add(c.id());
        }
        for (String id : expired) fail(p, id);
    }

    /** Attaches a command to run when the contract finishes (finish=true) or fails. Empty string clears it. */
    public static boolean setCommand(ServerPlayer p, String id, boolean finish, String command) {
        List<Contract> l = list(p);
        int i = indexOf(l, id);
        if (i < 0) return false;
        l.set(i, l.get(i).withCommand(finish, command));
        save(p);
        return true;
    }

    /**
     * Runs a stored command as the player with full permission and no chat output.
     * Placeholders: _lp (or {player}) = the contract owner, {reward} {id} {type} {target}
     */
    private static void run(ServerPlayer p, String template, Contract c) {
        if (template == null || template.isBlank() || p.getServer() == null) return;
        String who = p.getGameProfile().getName();
        String cmd = template
                .replaceAll("(?<!\\w)_lp(?!\\w)", java.util.regex.Matcher.quoteReplacement(who))   // _lp = local player
                .replace("{player}", who)
                .replace("{reward}", String.valueOf(c.reward()))
                .replace("{id}", c.id())
                .replace("{type}", c.type())
                .replace("{target}", String.valueOf(c.target()));
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        try {
            CommandSourceStack src = p.createCommandSourceStack().withPermission(4).withSuppressedOutput();
            p.getServer().getCommands().performPrefixedCommand(src, cmd);
        } catch (Exception e) {
            CombatHud.LOGGER.warn("[CombatHud] contract command failed ({}): {}", cmd, e.toString());
        }
    }

    private static void payout(ServerPlayer p, int reward) {
        String name = ServerConfig.REWARD_OBJECTIVE.get();
        if (reward <= 0 || name.isEmpty() || p.getServer() == null) return;
        Scoreboard sb = p.getServer().getScoreboard();
        Objective o = sb.getObjective(name);
        if (o != null) sb.getOrCreatePlayerScore(p.getScoreboardName(), o).add(reward);
    }
}
