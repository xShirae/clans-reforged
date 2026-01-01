package net.hosenka.integration.griefdefender;

import com.griefdefender.api.GriefDefender;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.hosenka.util.CRDebug;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

public final class GDIntegration {

    private GDIntegration() {}

    private static ClansReforgedClanProvider clanProvider;
    private static boolean registered = false;

    public static void init(MinecraftServer server) {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            return;
        }
        if (!FabricLoader.getInstance().isModLoaded("griefdefender")) {
            return;
        }

        CRDebug.log("GDIntegration.init() called");

        try {
            ServerHolder.set(server);

            // Register only once
            if (!registered) {
                clanProvider = new ClansReforgedClanProvider();
                GriefDefender.getRegistry().registerClanProvider(clanProvider);
                registered = true;
                CRDebug.log("GD Clan provider registered successfully.");
            } else {
                CRDebug.log("GD Clan provider already registered (skipping).");
            }

        } catch (IllegalStateException e) {
            // GD present but not ready
            CRDebug.log("GD present but not initialized yet (IllegalStateException).", e);
        } catch (Throwable t) {
            CRDebug.log("Unexpected error while initializing GD integration.", t);
        }
    }

    public static @Nullable ClansReforgedClanProvider getClanProvider() {
        return clanProvider;
    }

    public static boolean isRegistered() {
        return registered;
    }
}
