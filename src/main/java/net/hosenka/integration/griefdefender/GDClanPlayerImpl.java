package net.hosenka.integration.griefdefender;

import com.griefdefender.api.Clan;
import com.griefdefender.api.ClanPlayer;
import com.griefdefender.api.claim.Claim;
import com.griefdefender.api.clan.Rank;
import com.griefdefender.api.data.PlayerData;

import java.util.UUID;

public class GDClanPlayerImpl implements ClanPlayer {

    private final UUID playerId;
    private final net.hosenka.clan.Clan clan;

    public GDClanPlayerImpl(UUID playerId, net.hosenka.clan.Clan clan) {
        this.playerId = playerId;
        this.clan = clan;
    }

    @Override
    public UUID getUniqueId() {
        return playerId;
    }

    @Override
    public Clan getClan() {
        return new GDClanImpl(clan);
    }

    @Override
    public void setRank(Rank rank) {
        // Optional: if your mod supports ranks, persist it.
        // For now, ignore (GD can still use leader/member).
    }

    @Override
    public Rank getRank() {
        return this.clan.isLeader(playerId) ? CRRank.LEADER : CRRank.MEMBER;
    }

    @Override
    public boolean isLeader() {
        return clan.isLeader(playerId);
    }


    public String getName() {
        return playerId.toString(); // Or lookup real name
    }

    @Override
    public String getFriendlyName() {
        if (ServerHolder.get() != null) {
            var player = ServerHolder.get()
                    .getPlayerList()
                    .getPlayer(playerId);

            if (player != null) {
                return player.getName().getString();
            }
        }

        // Fallback for offline players
        return playerId.toString();
    }


    @Override
    public String getIdentifier() {
        return this.playerId.toString();
    }


    @Override
    public boolean isOnline() {
        if (ServerHolder.get() == null) {
            return false;
        }
        return ServerHolder.get().getPlayerList().getPlayer(playerId) != null;
    }

    @Override
    public Object getOnlinePlayer() {
        if (ServerHolder.get() == null) {
            return null;
        }
        return ServerHolder.get().getPlayerList().getPlayer(playerId);
    }

    @Override
    public PlayerData getPlayerData() {
        var core = com.griefdefender.api.GriefDefender.getCore();
        var user = core.getUser(this.playerId);
        if (user == null) {
            // Extremely unlikely, but better than returning null.
            throw new IllegalStateException("GriefDefender user not available for " + this.playerId);
        }
        return user.getPlayerData();
    }


    @Override
    public Claim getCurrentClaim() {
        Object online = getOnlinePlayer();
        if (online instanceof net.minecraft.server.level.ServerPlayer sp) {
            var core = com.griefdefender.api.GriefDefender.getCore();
            var world = core.getWorldUniqueId(sp.serverLevel()); // depends on GD API, may differ
        }
        return null;
    }

}


