package com.thunder.megalith;

import com.mojang.logging.LogUtils;
import com.thunder.megalith.megastructure.datapack.MegalithStructureLoader;
import com.thunder.megalith.megastructure.placement.MegalithPlacementManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(Megalith.MOD_ID)
public class Megalith {

    public static final String MOD_ID = "megalith";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Megalith(IEventBus modEventBus) {
        // Register forge bus listeners
        NeoForge.EVENT_BUS.addListener(this::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
    }

    private void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(MegalithStructureLoader.getInstance());
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        MegalithCommands.register(event.getDispatcher(), event.getBuildContext());
    }

    private void onServerTick(ServerTickEvent.Post event) {
        MegalithPlacementManager.getInstance().tick(event.getServer());
    }

    private void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("[Megalith] Server starting — Megalith API ready.");
    }
}