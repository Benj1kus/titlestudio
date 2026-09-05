package com.benji.titlestudio.title.network;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

public record TitleRegistryRequestC2SPacket(String type) {

    public static void encode(TitleRegistryRequestC2SPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.type != null ? packet.type : "biome", 32);
    }

    public static TitleRegistryRequestC2SPacket decode(FriendlyByteBuf buffer) {
        return new TitleRegistryRequestC2SPacket(buffer.readUtf(32));
    }

    public static void handle(TitleRegistryRequestC2SPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();

            if (player == null) {
                return;
            }

            String type = packet.type == null ? "biome" : packet.type.toLowerCase(Locale.ROOT);

            List<ResourceLocation> ids = new ArrayList<>();
            List<ResourceLocation> templates = new ArrayList<>();

            try {
                if ("structure".equals(type)) {

                    Registry<Structure> registry = player.serverLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);
                    ids.addAll(registry.keySet());
                    templates.addAll(collectStructureTemplates(player.getServer().getResourceManager()));

                } else {

                    Registry<Biome> registry = player.serverLevel().registryAccess().registryOrThrow(Registries.BIOME);
                    ids.addAll(registry.keySet());
                    type = "biome";
                }

            } catch (Exception ignored) {
                ids.clear();
                templates.clear();
            }
            ids.sort(Comparator.comparing(ResourceLocation::toString));
            templates.sort(Comparator.comparing(ResourceLocation::toString));
            TitleNetwork.sendRegistryList(player, type, ids, templates);
        });
        context.setPacketHandled(true);
    }

    private static List<ResourceLocation> collectStructureTemplates(ResourceManager resourceManager) {
        if (resourceManager == null) {
            return List.of();
        }

        Set<ResourceLocation> result = new LinkedHashSet<>();
        resourceManager.listResources("structures", id -> id.getPath().endsWith(".nbt")).keySet().forEach(resourceId -> {
            String path = resourceId.getPath();

            if (!path.startsWith("structures/")) {
                return;
            }
            path = path.substring("structures/".length());
            if (path.endsWith(".nbt")) {
                path = path.substring(0, path.length() - 4);
            }
            if (path.isBlank()) {
                return;
            }
            result.add(ResourceLocation.fromNamespaceAndPath(resourceId.getNamespace(), path));
        });

        return new ArrayList<>(result);
    }
}
