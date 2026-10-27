package com.kodyris.combathud.net;

import com.kodyris.combathud.client.ClientHooks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: show a region-style title banner. style: default (orange), complete (green), fail (red), team (blue). */
public record TitlePacket(String label, String name, String style) {

    public static void encode(TitlePacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.label);
        buf.writeUtf(p.name);
        buf.writeUtf(p.style);
    }

    public static TitlePacket decode(FriendlyByteBuf buf) {
        return new TitlePacket(buf.readUtf(), buf.readUtf(), buf.readUtf());
    }

    public static void handle(TitlePacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.title(p.label, p.name, p.style)));
        ctx.get().setPacketHandled(true);
    }
}
