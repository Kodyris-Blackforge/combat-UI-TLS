package com.kodyris.combathud;

import net.minecraftforge.common.ForgeConfigSpec;

/** Common config (read on the server). */
public final class ServerConfig {
    private ServerConfig() {}

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.ConfigValue<String> REWARD_OBJECTIVE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        REWARD_OBJECTIVE = b
                .comment("Scoreboard objective that receives a contract's reward when it completes.",
                        "Empty = no automatic payout (the reward is only displayed). The objective must already exist.")
                .define("rewardObjective", "");
        SPEC = b.build();
    }
}
