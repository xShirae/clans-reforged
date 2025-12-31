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
        // Not implemented
    }

    @Override
    public Rank getRank() {
        return null;
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
        return playerId.toString(); // Required by Subject interface
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
        return null; // You can implement GriefDefender.getCore().getPlayerData(...)
    }

    @Override
    public Claim getCurrentClaim() {
        return null;
    }
}
