package com.benji.titlestudio.title.studio;

import com.benji.titlestudio.TitleStudio;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

public final class TitleStudioClientHooks {

    public static final KeyMapping OPEN_STUDIO = new KeyMapping("key.ttlstd.title_studio", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_U, "key.categories.ttlstd");

    private TitleStudioClientHooks() {
    }

    @Mod.EventBusSubscriber(modid = TitleStudio.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        private ModBus() {
        }

        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(OPEN_STUDIO);
        }
    }

    @Mod.EventBusSubscriber(modid = TitleStudio.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ForgeBus {
        private ForgeBus() {
        }

        @SubscribeEvent
        public static void clientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;

            Minecraft minecraft = Minecraft.getInstance();

            while (OPEN_STUDIO.consumeClick()) {
                if (minecraft.screen instanceof TitleStudioScreen) {
                    minecraft.setScreen(null);
                } else if (minecraft.screen == null) {
                    minecraft.setScreen(new TitleStudioScreen(TitleStudioWorkspace.loadLastOrDefault(), TitleStudioScreen.Tab.BASIC));
                }
            }
        }
    }
}
