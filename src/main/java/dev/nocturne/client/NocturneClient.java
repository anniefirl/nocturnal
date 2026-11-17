package dev.nocturne.client;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NocturneClient implements ClientModInitializer {
    public static final String MOD_ID = "nocturne";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("Nocturne Client {} initialized.", "0.1.0");
        // Feature registration will live in dedicated packages:
        // hud/, module/, config/, gui/, input/.
    }
}
