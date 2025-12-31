package net.hosenka;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.hosenka.alliance.AllianceRegistry;
import net.hosenka.clan.ClanRegistry;
import net.hosenka.command.AllianceCommand;
import net.hosenka.command.ClanCommand;
import net.hosenka.database.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import java.lang.reflect.Method;
import java.net.URL;


public class ClansReforged implements ModInitializer {
	public static final String MOD_ID = "clansreforged";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Clans Reforged initialized!");


        DatabaseManager.initialize();

        ClanRegistry.loadFromDatabase();
        AllianceRegistry.loadFromDatabase();

        ClanCommand.register();
        AllianceCommand.register();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            //integrated server runs on CLIENT environment
            if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
                return;
            }

            try {
                net.hosenka.integration.griefdefender.GDIntegration.init(server);
            } catch (Throwable t) {
                LOGGER.error("Failed to initialize GD integration", t);
            }

            try {
                Class<?> cls = Class.forName(
                        "com.griefdefender.lib.kyori.adventure.platform.forge.MinecraftComponentSerializer"
                );

                URL from = cls.getProtectionDomain().getCodeSource() != null
                        ? cls.getProtectionDomain().getCodeSource().getLocation()
                        : null;

                Method isSupported = cls.getMethod("isSupported");
                boolean supported = (boolean) isSupported.invoke(null);

                System.out.println("[ClansReforged] GD MinecraftComponentSerializer supported = " + supported);
                System.out.println("[ClansReforged] Loaded from = " + from);
            } catch (Throwable t) {
                System.out.println("[ClansReforged] GD serializer check failed:");
                t.printStackTrace();
            }
        });
    }

}