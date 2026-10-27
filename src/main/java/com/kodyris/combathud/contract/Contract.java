package com.kodyris.combathud.contract;

import net.minecraft.nbt.CompoundTag;

/**
 * Server-side contract. expiresAt is an absolute game time in ticks, or -1 for no timer.
 * onFinish / onFail are optional commands run (as the player, with full permission) when the contract ends.
 */
public record Contract(String id, String type, String description, int progress, int target, int reward,
                       long expiresAt, String onFinish, String onFail) {

    public CompoundTag toTag() {
        CompoundTag t = new CompoundTag();
        t.putString("id", id);
        t.putString("type", type);
        t.putString("desc", description);
        t.putInt("progress", progress);
        t.putInt("target", target);
        t.putInt("reward", reward);
        t.putLong("expires", expiresAt);
        t.putString("onFinish", onFinish);
        t.putString("onFail", onFail);
        return t;
    }

    public static Contract fromTag(CompoundTag t) {
        return new Contract(t.getString("id"), t.getString("type"), t.getString("desc"),
                t.getInt("progress"), Math.max(1, t.getInt("target")), t.getInt("reward"),
                t.contains("expires") ? t.getLong("expires") : -1L,
                t.getString("onFinish"), t.getString("onFail"));
    }

    public Contract withProgress(int p) {
        return new Contract(id, type, description, Math.max(0, Math.min(target, p)), target, reward, expiresAt,
                onFinish, onFail);
    }

    public Contract withCommand(boolean finish, String command) {
        return finish
                ? new Contract(id, type, description, progress, target, reward, expiresAt, command, onFail)
                : new Contract(id, type, description, progress, target, reward, expiresAt, onFinish, command);
    }

    public boolean done() {
        return progress >= target;
    }
}
