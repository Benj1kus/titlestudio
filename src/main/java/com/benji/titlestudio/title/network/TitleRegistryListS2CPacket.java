package com.benji.titlestudio.title.network;

import com.benji.titlestudio.title.studio.TitleStudioRegistryBrowserScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record TitleRegistryListS2CPacket(String type, List<ResourceLocation> ids, List<ResourceLocation> templates) {

    private static final int MAX_IDS = 16384;
    public TitleRegistryListS2CPacket {
        ids = ids == null ? List.of() : List.copyOf(ids);
        templates = templates == null ? List.of() : List.copyOf(templates);
    }

    public static void encode(TitleRegistryListS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.type != null ? packet.type : "biome", 32);

        writeIds(buffer, packet.ids);

        writeIds(buffer, packet.templates);
    }

    public static TitleRegistryListS2CPacket decode(FriendlyByteBuf buffer) {
        String type = buffer.readUtf(32);

        List<ResourceLocation> ids = readIds(buffer);

        List<ResourceLocation> templates = readIds(buffer);

        return new TitleRegistryListS2CPacket(type, ids, templates);
    }

    public static void handle(TitleRegistryListS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TitleStudioRegistryBrowserScreen.receiveServerEntries(packet.type, packet.ids, packet.templates)));

        context.setPacketHandled(true);
    }

    private static void writeIds(FriendlyByteBuf buffer, List<ResourceLocation> values) {
        int size = Math.min(values != null ? values.size() : 0, MAX_IDS);

        buffer.writeVarInt(size);

        for (int i = 0; i < size; i++) {
            buffer.writeResourceLocation(values.get(i));
        }
    }

    private static List<ResourceLocation> readIds(FriendlyByteBuf buffer) {
        int size = Math.max(0, Math.min(buffer.readVarInt(), MAX_IDS));

        List<ResourceLocation> result = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            result.add(buffer.readResourceLocation());
        }

        return result;
    }
}
