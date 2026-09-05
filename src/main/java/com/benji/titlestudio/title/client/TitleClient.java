package com.benji.titlestudio.title.client;

import com.benji.titlestudio.TitleStudio;
import com.benji.titlestudio.title.TitleRegistry;
import com.benji.titlestudio.title.data.TitleDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

@Mod.EventBusSubscriber(modid = TitleStudio.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TitleClient {

    private static ActiveTitle active;

    private TitleClient() {
    }

    public static void receive(ResourceLocation id, String json) {
        TitleDefinition definition;
        try {
            definition = TitleRegistry.fromJson(json);
        } catch (Exception ignored) {
            return;
        }

        if (definition == null) return;
        play(id, definition);
    }

    public static void play(ResourceLocation id, TitleDefinition definition) {
        if (definition == null) return;
        definition.normalize();
        active = new ActiveTitle(id, definition, 0);
        playAppearanceSound(definition);
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (active == null) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            active = null;
            return;
        }

        active.ageTicks++;
        if (active.ageTicks > active.definition.totalTicks() + 2) {
            active = null;
        }
    }

    @SubscribeEvent
    public static void renderGui(RenderGuiEvent.Post event) {
        if (active == null) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) return;

        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();

        TitleTextRenderer.render(active.definition, event.getGuiGraphics(), 0, 0, width, height, active.ageTicks + event.getPartialTick());
    }

    private static void playAppearanceSound(TitleDefinition definition) {
        if (definition.sound == null || definition.sound.event == null || definition.sound.event.isBlank()) {
            return;
        }

        ResourceLocation id = ResourceLocation.tryParse(definition.sound.event);
        if (id == null) return;

        SoundSource source;
        try {
            source = SoundSource.valueOf(definition.sound.source.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            source = SoundSource.MASTER;
        }

        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(id, source, definition.sound.volume, definition.sound.pitch, RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true));
    }

    private static final class ActiveTitle {
        final ResourceLocation id;
        final TitleDefinition definition;
        int ageTicks;

        ActiveTitle(ResourceLocation id, TitleDefinition definition, int ageTicks) {
            this.id = id;
            this.definition = definition;
            this.ageTicks = ageTicks;
        }
    }
}
