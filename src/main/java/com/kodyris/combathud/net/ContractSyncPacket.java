package com.kodyris.combathud.net;

import com.kodyris.combathud.client.ClientHooks;
import com.kodyris.combathud.contract.ContractView;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server -> client: the player's full contract list (replaces whatever the client had). */
public record ContractSyncPacket(List<ContractView> contracts) {

    public static void encode(ContractSyncPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.contracts.size());
        for (ContractView c : p.contracts) {
            buf.writeUtf(c.id());
            buf.writeUtf(c.type());
            buf.writeUtf(c.description());
            buf.writeVarInt(c.progress());
            buf.writeVarInt(c.target());
            buf.writeVarInt(c.reward());
            buf.writeInt(c.remainingSeconds());
        }
    }

    public static ContractSyncPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<ContractView> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new ContractView(buf.readUtf(), buf.readUtf(), buf.readUtf(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readInt()));
        }
        return new ContractSyncPacket(list);
    }

    public static void handle(ContractSyncPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.contracts(p.contracts)));
        ctx.get().setPacketHandled(true);
    }
}
