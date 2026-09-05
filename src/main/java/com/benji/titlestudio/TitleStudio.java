package com.benji.titlestudio;

import com.benji.titlestudio.title.network.TitleNetwork;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(TitleStudio.MODID)
public final class TitleStudio {

    public static final String MODID = "ttlstd";

    public TitleStudio(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        modBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(TitleNetwork::register);
    }
}