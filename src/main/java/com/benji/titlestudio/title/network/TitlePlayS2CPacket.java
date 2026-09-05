package com.benji.titlestudio.title.network;

import com.benji.titlestudio.title.client.TitleClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record TitlePlayS2CPacket(ResourceLocation id, String json) {

    public static void encode(TitlePlayS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(packet.id);
        buffer.writeUtf(packet.json, 262_144);
    }

    public static TitlePlayS2CPacket decode(FriendlyByteBuf buffer) {
        return new TitlePlayS2CPacket(buffer.readResourceLocation(), buffer.readUtf(262_144));
    }

    public static void handle(TitlePlayS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TitleClient.receive(packet.id, packet.json)));
        context.setPacketHandled(true);
    }
}
