package com.benji.titlestudio.title;

import com.benji.titlestudio.TitleStudio;
import com.benji.titlestudio.title.data.TitleDefinition;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TitleRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<ResourceLocation, TitleDefinition> DEFINITIONS = new LinkedHashMap<>();

    public static final ReloadListener RELOAD_LISTENER = new ReloadListener();

    private TitleRegistry() {
    }

    public static TitleDefinition get(ResourceLocation id) {
        return DEFINITIONS.get(id);
    }

    public static Map<ResourceLocation, TitleDefinition> entries() {
        return Collections.unmodifiableMap(DEFINITIONS);
    }

    public static String toJson(TitleDefinition definition) {
        if (definition == null) return "{}";
        definition.normalize();
        return GSON.toJson(definition);
    }

    public static TitleDefinition fromJson(String json) {
        TitleDefinition definition = GSON.fromJson(json, TitleDefinition.class);
        if (definition != null) definition.normalize();
        return definition;
    }

    public static final class ReloadListener extends SimpleJsonResourceReloadListener {

        private ReloadListener() {
            super(GSON, "title_presentations");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager resourceManager, ProfilerFiller profiler) {
            Map<ResourceLocation, TitleDefinition> loaded = new LinkedHashMap<>();

            objects.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                try {
                    TitleDefinition definition = GSON.fromJson(entry.getValue(), TitleDefinition.class);
                    if (definition == null) {
                        LOGGER.warn("Title definition {} is empty", entry.getKey());
                        return;
                    }

                    definition.normalize();
                    loaded.put(entry.getKey(), definition);
                } catch (Exception exception) {
                    LOGGER.error("Failed to load title definition {}", entry.getKey(), exception);
                }
            });

            DEFINITIONS.clear();
            DEFINITIONS.putAll(loaded);
            LOGGER.info("Loaded {} title presentations", DEFINITIONS.size());
        }
    }

    @Mod.EventBusSubscriber(modid = TitleStudio.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void addReloadListener(AddReloadListenerEvent event) {
            event.addListener(RELOAD_LISTENER);
        }
    }
}
