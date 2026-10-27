package com.kodyris.combathud.net;

import com.kodyris.combathud.CombatHud;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public final class Net {
    private Net() {}

    private static final String PROTOCOL = "1";

    /** Strict version check: this mod must be on both sides, same protocol. */
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(CombatHud.MODID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, ContractSyncPacket.class, ContractSyncPacket::encode, ContractSyncPacket::decode,
                ContractSyncPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, TitlePacket.class, TitlePacket::encode, TitlePacket::decode,
                TitlePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void toPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /** Shows a region-style title on one player's screen. Blank label = NOW ENTERING. */
    public static void title(ServerPlayer player, String label, String name) {
        title(player, label, name, "default");
    }

    /** style: default (orange), complete (green), fail (red), team (blue). */
    public static void title(ServerPlayer player, String label, String name, String style) {
        toPlayer(player, new TitlePacket(label, name, style));
    }
}
