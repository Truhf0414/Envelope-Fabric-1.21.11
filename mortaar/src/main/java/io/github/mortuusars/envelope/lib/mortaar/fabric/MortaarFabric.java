package io.github.mortuusars.envelope.lib.mortaar.fabric;

import fuzs.forgeconfigapiport.fabric.api.v5.ConfigRegistry;
import io.github.mortuusars.envelope.lib.mortaar.Config;
import io.github.mortuusars.envelope.lib.mortaar.Mortaar;
import io.github.mortuusars.envelope.lib.mortaar.command.MortaarCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.config.ModConfig;
import org.jetbrains.annotations.Nullable;

public class MortaarFabric implements ModInitializer {
    // Server field to access when no other objects are available to get it from.
    public static @Nullable MinecraftServer server;

    /** Port: id of the mod Mortaar is vendored into. */
    public static final String HOST_MOD_ID = "envelope";

    @Override
    public void onInitialize() {
        Mortaar.init();
        // Port: Mortaar is vendored into Envelope and is no longer a mod of its own, so there is no "mortaar" mod
        // container to attach the config to (ModConfig throws "No mod with id 'mortaar'"). Its server config is
        // registered under Envelope's mod id, with its own file name so it does not clash with Envelope's.
        ConfigRegistry.INSTANCE.register(HOST_MOD_ID, ModConfig.Type.SERVER, Config.Server.SPEC, "envelope-mortaar-server.toml");

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            MortaarCommand.register(dispatcher, registryAccess);
        });

        ServerLifecycleEvents.SERVER_STARTED.register(minecraftServer -> server = minecraftServer);
        ServerLifecycleEvents.SERVER_STOPPED.register(minecraftServer -> server = null);
    }
}
