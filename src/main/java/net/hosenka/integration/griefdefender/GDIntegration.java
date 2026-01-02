package net.hosenka.integration.griefdefender;

import com.griefdefender.api.GriefDefender;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.hosenka.util.CRDebug;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class GDIntegration {

    private GDIntegration() {}

    private static ClansReforgedClanProvider clanProvider;
    private static boolean registered = false;

    public static void init(MinecraftServer server) {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) return;
        if (!FabricLoader.getInstance().isModLoaded("griefdefender")) return;

        ServerHolder.set(server);

        // already registered? just refresh caches
        if (registered) {
            CRDebug.log("GDIntegration already registered; clearing caches.");
            clearCachesIfPresent();
            return;
        }

        try {
            clanProvider = new ClansReforgedClanProvider();
            GriefDefender.getRegistry().registerClanProvider(clanProvider);
            registered = true;
            CRDebug.log("GD Clan provider registered successfully.");
        } catch (Throwable t) {
            // if GD isn’t ready even at SERVER_STARTED, you’ll see it here
            CRDebug.log("Failed to register GD clan provider.", t);
        }
    }

    public static void shutdown() {
        // We can’t “unregister” from GD safely, but we CAN drop references + caches.
        clearCachesIfPresent();
        ServerHolder.set(null);
        CRDebug.log("GDIntegration shutdown: caches cleared, server cleared.");
    }

    public static @Nullable ClansReforgedClanProvider getClanProvider() {
        return clanProvider;
    }

    public static boolean isRegistered() {
        return registered;
    }

    public static void clearCachesIfPresent() {
        if (clanProvider != null) clanProvider.clearCaches();
    }

    // --------- Cache invalidation helpers (use everywhere you mutate clan data) ---------

    public static void invalidateClan(UUID clanId) {
        if (clanProvider != null) clanProvider.invalidateClan(clanId);
    }

    public static void invalidatePlayer(UUID playerId) {
        if (clanProvider != null) clanProvider.invalidatePlayer(playerId);
    }

    public static void invalidateClanAndPlayers(UUID clanId, UUID... players) {
        invalidateClan(clanId);
        if (players != null) {
            for (UUID p : players) {
                if (p != null) invalidatePlayer(p);
            }
        }
    }
}
