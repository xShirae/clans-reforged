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
            // Integrated server runs under CLIENT environment - skip server-only integration.
            if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
                return;
            }

            // IMPORTANT: don't even try to touch GD code if mod isn't present at runtime
            if (!FabricLoader.getInstance().isModLoaded("griefdefender")) {
                LOGGER.info("GriefDefender not installed; skipping GD integration.");
                return;
            }

            // Helper that tries init + injection
            final Runnable tryInitAndInject = () -> {
                try {
                    net.hosenka.integration.griefdefender.GDIntegration.init(server);
                } catch (Throwable t) {
                    LOGGER.error("Failed to initialize GD integration", t);
                }

                final var provider = net.hosenka.integration.griefdefender.GDIntegration.getClanProvider();
                final boolean registered = net.hosenka.integration.griefdefender.GDIntegration.isRegistered();

                if (registered && provider != null) {
                    try {
                        net.hosenka.integration.griefdefender.CRGDClanCommands.register(server, provider);

                        System.out.println("[ClansReforged] Injected Fabric GD clan commands (/gd clan ...).");
                    } catch (Throwable t) {
                        LOGGER.error("Failed to inject Fabric GD clan commands", t);
                    }
                } else {
                    System.out.println("[ClansReforged] GD not ready yet (provider not registered). Will retry once next tick.");
                }
            };

            // 1) Try immediately
            tryInitAndInject.run();

            // 2) If GD wasn't ready, retry once next tick
            if (!net.hosenka.integration.griefdefender.GDIntegration.isRegistered()
                    || net.hosenka.integration.griefdefender.GDIntegration.getClanProvider() == null) {
                server.execute(tryInitAndInject);
            }

            // 3) Optional: serializer sanity check (debug only)
            if (net.hosenka.util.CRDebug.ENABLED) {
                try {
                    Class<?> cls = Class.forName(
                            "com.griefdefender.lib.kyori.adventure.platform.forge.MinecraftComponentSerializer"
                    );

                    URL from = cls.getProtectionDomain().getCodeSource() != null
                            ? cls.getProtectionDomain().getCodeSource().getLocation()
                            : null;

                    Method isSupported = cls.getMethod("isSupported");
                    boolean supported = (boolean) isSupported.invoke(null);

                    System.out.println("[ClansReforged][DEBUG] GD MinecraftComponentSerializer supported = " + supported);
                    System.out.println("[ClansReforged][DEBUG] Loaded from = " + from);
                } catch (Throwable t) {
                    System.out.println("[ClansReforged][DEBUG] GD serializer check failed:");
                    t.printStackTrace();
                }
            }
        });



    }

}