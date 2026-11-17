package io.github.mortuusars.envelope.lib.mortaar.fabric;

import io.github.mortuusars.envelope.lib.mortaar.MortaarClient;
import net.fabricmc.api.ClientModInitializer;

public class MortaarFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MortaarClient.init();
        // Port: no separate config screen; Envelope registers one for its mod id, which lists this config too.
    }
}
