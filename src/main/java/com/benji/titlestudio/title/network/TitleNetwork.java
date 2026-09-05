package com.benji.titlestudio.title.network;

import com.benji.titlestudio.TitleStudio;
import com.benji.titlestudio.title.TitleRegistry;
import com.benji.titlestudio.title.data.TitleDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.List;

public final class TitleNetwork {

    private static final String PROTOCOL = "3";

    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(ResourceLocation.fromNamespaceAndPath(TitleStudio.MODID, "title_engine"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private TitleNetwork() {
    }

    public static void register() {
        int id = 0;

        CHANNEL.registerMessage(id++, TitlePlayS2CPacket.class, TitlePlayS2CPacket::encode, TitlePlayS2CPacket::decode, TitlePlayS2CPacket::handle);
        CHANNEL.registerMessage(id++, TitleRegistryRequestC2SPacket.class, TitleRegistryRequestC2SPacket::encode, TitleRegistryRequestC2SPacket::decode, TitleRegistryRequestC2SPacket::handle);
        CHANNEL.registerMessage(id, TitleRegistryListS2CPacket.class, TitleRegistryListS2CPacket::encode, TitleRegistryListS2CPacket::decode, TitleRegistryListS2CPacket::handle);
    }

    public static void play(ServerPlayer player, ResourceLocation id, TitleDefinition definition) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new TitlePlayS2CPacket(id, TitleRegistry.toJson(definition)));
    }

    public static void requestRegistry(String type) {
        CHANNEL.sendToServer(new TitleRegistryRequestC2SPacket(type));
    }

    public static void sendRegistryList(ServerPlayer player, String type, List<ResourceLocation> ids, List<ResourceLocation> templates) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new TitleRegistryListS2CPacket(type, ids, templates));
    }
}
