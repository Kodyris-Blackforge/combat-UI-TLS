package com.kodyris.combathud;

import com.kodyris.combathud.net.Net;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Runs on both sides: the client draws the HUD, the server owns contracts and triggers titles.
 * Client-only classes live in the client package and are only loaded on the client.
 */
@Mod(CombatHud.MODID)
public class CombatHud {
    public static final String MODID = "combathud";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CombatHud() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, HudConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ServerConfig.SPEC);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent e) {
        e.enqueueWork(Net::register);
    }
}
