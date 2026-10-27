package com.kodyris.combathud.client;

import com.kodyris.combathud.HudConfig;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

/** Per-frame animation state shared by the HUD and the inventory screen. Updated once per frame by the HUD. */
final class HudState {
    private HudState() {}

    // displayed (smoothed) values, 0..1
    static float health, healthTrail, food, armor;
    static float damageFlash;     // 0..1, spikes on damage then decays
    static float ammoPop;         // 0..1, spikes when the magazine count changes
    static float weaponIn = 1f;   // 0..1, weapon panel slide-in progress
    static float selector;        // hotbar selector position in (fractional) slots
    static float dt;              // seconds since last update

    private static boolean primed;
    private static float lastHp;
    private static float trailHold;
    private static long lastNanos;
    private static Item lastHeld;
    private static int lastMag = -1;

    static boolean animate() {
        return HudConfig.ANIMATIONS.get();
    }

    static float approach(float value, float target, float speed) {
        return value + (target - value) * (1f - (float) Math.exp(-speed * dt));
    }

    static float easeOut(float t) {
        float u = 1f - t;
        return 1f - u * u * u;
    }

    static void update(Player p) {
        long now = System.nanoTime();
        dt = primed ? Mth.clamp((now - lastNanos) / 1.0e9f, 0f, 0.1f) : 0f;
        lastNanos = now;

        float max = Math.max(1f, p.getMaxHealth());
        float hp = p.getHealth();
        float hpT = Mth.clamp(hp / max, 0f, 1f);
        float foodT = Mth.clamp(p.getFoodData().getFoodLevel() / 20f, 0f, 1f);
        float armorT = Mth.clamp(p.getArmorValue() / 20f, 0f, 1f);
        int sel = p.getInventory().selected;
        Item held = p.getMainHandItem().getItem();
        boolean heldChanged = held != lastHeld;

        if (!primed || !animate()) {
            health = healthTrail = hpT;
            food = foodT;
            armor = armorT;
            selector = sel;
            damageFlash = 0f;
            ammoPop = 0f;
            weaponIn = 1f;
        } else {
            if (hp < lastHp - 0.01f) {
                damageFlash = Math.min(1f, damageFlash + 0.35f + ((lastHp - hp) / max) * 1.5f);
                trailHold = 0.45f;
            }
            health = approach(health, hpT, 14f);
            food = approach(food, foodT, 8f);
            armor = approach(armor, armorT, 8f);

            // damage trail: stays put briefly after a hit, then drains slowly
            if (health >= healthTrail) {
                healthTrail = health;
            } else if (trailHold > 0f) {
                trailHold -= dt;
            } else {
                healthTrail = approach(healthTrail, hpT, 3f);
            }

            damageFlash = Math.max(0f, damageFlash - dt * 1.8f);
            ammoPop = Math.max(0f, ammoPop - dt * 7f);

            selector = approach(selector, sel, 24f);
            if (Math.abs(selector - sel) < 0.01f) selector = sel;

            weaponIn = Math.min(1f, weaponIn + dt * 6f);
            if (heldChanged) weaponIn = 0f;
        }

        if (heldChanged) lastMag = -1;
        lastHeld = held;
        lastHp = hp;
        primed = true;
    }

    /** Called by the weapon panel with the current magazine count. */
    static void noteMag(int mag) {
        if (animate() && lastMag >= 0 && mag != lastMag) {
            ammoPop = mag > lastMag ? 1f : 0.4f;   // big pop on reload, small pop per shot
        }
        lastMag = mag;
    }

    static void clearMag() {
        lastMag = -1;
    }
}
