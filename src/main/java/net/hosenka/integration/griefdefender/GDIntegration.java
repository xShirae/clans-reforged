package net.hosenka.integration.griefdefender;

import com.griefdefender.api.GriefDefender;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.hosenka.api.ClansReforgedEvents;
import net.hosenka.util.CRDebug;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class GDIntegration {

    private GDIntegration() {}

    private static ClansReforgedClanProvider clanProvider;
    private static boolean registered = false;

    // Fabric events are global; we only want to hook them once.
    private static boolean hooksRegistered = false;

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
            return;
        }

        // Hook CR events -> invalidate provider caches when CR mutates clan data.
        registerEventHooks();
    }

    /**
     * Hooks ClansReforgedEvents into GD provider cache invalidation.
     *
     * This mirrors what GDHooks does for Bukkit plugins (listen to clan events and update
     * cached clan/player views). Without this, GD may keep stale wrappers after CR changes.
     */
    private static void registerEventHooks() {
        if (hooksRegistered) return;
        hooksRegistered = true;

        // Clan created/disbanded
        ClansReforgedEvents.CREATE_CLAN.register((creator, clanId) -> invalidateClanAndPlayers(clanId, creator.getUUID()));

        ClansReforgedEvents.DISBAND_CLAN.register((actor, clanId) -> {
            // We don't have the member list here reliably (clan may already be gone),
            // so safest is to clear all caches.
            clearCachesIfPresent();
        });

        // Membership changes
        ClansReforgedEvents.PLAYER_JOINED_CLAN.register((player, clanId) -> invalidateClanAndPlayers(clanId, player.getUUID()));
        ClansReforgedEvents.PLAYER_LEFT_CLAN.register((player, clanId) -> invalidateClanAndPlayers(clanId, player.getUUID()));
        ClansReforgedEvents.PLAYER_KICKED_CLAN.register((actor, targetPlayerId, clanId) -> invalidateClanAndPlayers(clanId, targetPlayerId));

        // Rank updates
        ClansReforgedEvents.PLAYER_RANK_UPDATE.register((actor, targetPlayerId, clanId, oldRank, newRank) ->
                invalidateClanAndPlayers(clanId, targetPlayerId));

        // Homes affect GD clan home info
        ClansReforgedEvents.PLAYER_HOME_SET.register((player, clanId, pos, yaw, pitch) -> {
            // Require home to be set inside land owned by the player OR land marked as clan land
            // where the claim owner is a member of the same clan.
            try {
                final var core = com.griefdefender.api.GriefDefender.getCore();
                final var level = player.serverLevel();
                final java.util.UUID worldId = core.getWorldUniqueId(level);
                if (worldId != null) {
                    final var bp = player.blockPosition();
                    final com.griefdefender.api.claim.Claim claim = core.getClaimAt(worldId, bp.getX(), bp.getY(), bp.getZ());
                    if (claim == null || claim.isWilderness()) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("You must set the clan home inside a claim you own or your clan owns."));
                        return net.minecraft.world.InteractionResult.FAIL;
                    }

                    final java.util.UUID owner = claim.getOwnerUniqueId();
                    if (owner != null && owner.equals(player.getUUID())) {
                        // Personal land owned by the actor
                        invalidateClan(clanId);
                        return net.minecraft.world.InteractionResult.PASS;
                    }

                    final boolean isClanLand = claim.hasAttribute("gdhooks:clan");
                    if (isClanLand && owner != null) {
                        final java.util.UUID ownerClan = net.hosenka.clan.ClanMembershipRegistry.getClan(owner);
                        if (ownerClan != null && ownerClan.equals(clanId)) {
                            invalidateClan(clanId);
                            return net.minecraft.world.InteractionResult.PASS;
                        }
                    }

                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("You can only set the clan home in land owned by you or marked as your clan land."));
                    return net.minecraft.world.InteractionResult.FAIL;
                }
            } catch (Throwable t) {
                // If anything goes wrong, fail open (don't break /clan sethome).
                net.hosenka.util.CRDebug.log("GD home-set ownership check failed; allowing.", t);
            }

            invalidateClan(clanId);
            return net.minecraft.world.InteractionResult.PASS;
        });
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