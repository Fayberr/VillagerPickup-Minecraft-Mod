package net.fayber.villagerpickup;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VillagerPickup implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("villager_pickup");

    @Override
    public void onInitialize() {
        LOGGER.info("Villager Pickup v2.5.0 (OFFICIAL) Initialized!");
    }
}
