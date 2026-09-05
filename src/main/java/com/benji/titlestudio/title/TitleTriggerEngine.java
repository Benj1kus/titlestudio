package com.benji.titlestudio.title;

import com.benji.titlestudio.TitleStudio;
import com.benji.titlestudio.title.data.TitleDefinition;
import com.benji.titlestudio.title.network.TitleNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = TitleStudio.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TitleTriggerEngine {

    private static final int CHECK_INTERVAL = 4;
    private static final Map<UUID, Map<ResourceLocation, Track>> PLAYER_STATES = new HashMap<>();

    private TitleTriggerEngine() {
    }

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (player.tickCount % CHECK_INTERVAL != 0) return;
        if (!player.isAlive()) return;

        Map<ResourceLocation, Track> states = PLAYER_STATES.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>());

        long gameTime = player.serverLevel().getGameTime();

        for (Map.Entry<ResourceLocation, TitleDefinition> entry : TitleRegistry.entries().entrySet()) {
            ResourceLocation titleId = entry.getKey();
            TitleDefinition definition = entry.getValue();
            definition.normalize();

            Track track = states.computeIfAbsent(titleId, ignored -> new Track());
            boolean matches = matches(player, definition.trigger);

            if (!matches) {
                track.inside = false;
                track.dwellTicks = 0;
                track.firedThisVisit = false;
                continue;
            }

            if (!track.inside) {
                track.inside = true;
                track.dwellTicks = 0;
                track.firedThisVisit = false;
            }

            track.dwellTicks += CHECK_INTERVAL;

            if (track.dwellTicks < definition.trigger.minimum_stay_ticks) continue;
            if (definition.trigger.once_per_visit && track.firedThisVisit) continue;

            long sinceLast = gameTime - track.lastPlayedGameTime;
            if (track.lastPlayedGameTime != Long.MIN_VALUE && sinceLast < definition.trigger.cooldown_ticks) {
                continue;
            }

            TitleNetwork.play(player, titleId, definition);
            track.firedThisVisit = true;
            track.lastPlayedGameTime = gameTime;
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        PLAYER_STATES.remove(event.getEntity().getUUID());
    }

    private static boolean matches(ServerPlayer player, TitleDefinition.Trigger trigger) {
        if (trigger == null) return false;

        ResourceLocation target = ResourceLocation.tryParse(trigger.target);
        if (target == null) return false;

        return switch (trigger.type) {
            case "biome" -> matchesBiome(player, target);
            case "structure" -> matchesStructure(player, target);
            default -> false;
        };
    }

    private static boolean matchesBiome(ServerPlayer player, ResourceLocation target) {
        return player.serverLevel().getBiome(player.blockPosition()).unwrapKey().map(key -> key.location().equals(target)).orElse(false);
    }

    private static boolean matchesStructure(ServerPlayer player, ResourceLocation target) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();

        ResourceKey<Structure> structureKey = ResourceKey.create(Registries.STRUCTURE, target);
        StructureStart start = level.structureManager().getStructureWithPieceAt(pos, structureKey);

        return start != null && start.isValid();
    }

    private static final class Track {
        boolean inside;
        int dwellTicks;
        boolean firedThisVisit;
        long lastPlayedGameTime = Long.MIN_VALUE;
    }
}
