package com.kodyris.combathud;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public final class HudConfig {
    private HudConfig() {}

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> HIDDEN_OVERLAYS;
    public static final ForgeConfigSpec.BooleanValue LOG_OVERLAYS;
    public static final ForgeConfigSpec.BooleanValue ANIMATIONS;
    public static final ForgeConfigSpec.BooleanValue DAMAGE_VIGNETTE;
    public static final ForgeConfigSpec.BooleanValue LOW_AMMO_ALERT;
    public static final ForgeConfigSpec.BooleanValue REGION_SOUND;
    public static final ForgeConfigSpec.IntValue SQUAD_MAX;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        HIDDEN_OVERLAYS = b
                .comment("Other mods' GUI overlays to cancel while Combat HUD is active (survival/adventure).",
                        "Format: namespace:path. A trailing * matches a prefix, e.g. \"tacz:gun_hud*\".",
                        "Careful: \"tacz:*\" would also remove TACZ's crosshair/hit markers.")
                .defineListAllowEmpty("hiddenOverlays", List.of("tacz:tac_gun_hud_overlay"), o -> o instanceof String);
        LOG_OVERLAYS = b
                .comment("Log every GUI overlay id once per session so you can find the exact id to hide.",
                        "Look for '[CombatHud] GUI overlay:' lines in logs/latest.log, then turn this off.")
                .define("logOverlays", true);
        ANIMATIONS = b
                .comment("Smooth bars, damage trail, ammo pop, weapon slide-in, hotbar slide, inventory open animation.",
                        "Off = everything snaps instantly.")
                .define("animations", true);
        DAMAGE_VIGNETTE = b
                .comment("Red screen-edge flash when you take damage, and a slow pulse at low health.")
                .define("damageVignette", true);
        LOW_AMMO_ALERT = b
                .comment("Pulsing LOW AMMO / RELOAD text under the crosshair (TACZ guns).")
                .define("lowAmmoAlert", true);
        REGION_SOUND = b
                .comment("Play a soft UI sound when a region title appears.")
                .define("regionTitleSound", true);
        SQUAD_MAX = b
                .comment("Squad slots shown on the SQUAD tab (1-8).")
                .defineInRange("squadMax", 8, 1, 8);
        SPEC = b.build();
    }
}
