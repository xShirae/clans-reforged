package net.hosenka.integration.griefdefender;

import com.griefdefender.api.Clan;
import com.griefdefender.api.ClanPlayer;
import com.griefdefender.api.claim.Claim;
import com.griefdefender.api.clan.Rank;
import com.griefdefender.api.data.PlayerData;
import net.hosenka.clan.ClanMembershipRegistry;
import net.hosenka.clan.ClanRank;

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
        // Called by GD-side features; must not be a no-op
        CRRank target = (rank instanceof CRRank cr) ? cr : CRRank.byName(rank.getName());

        // Promote to leader = transfer leadership
        if (target == CRRank.LEADER) {
            UUID oldLeader = clan.getLeaderId();
            clan.setLeaderId(this.playerId);

            // Old leader becomes RIGHT_ARM (reasonable default)
            if (oldLeader != null && !oldLeader.equals(this.playerId)) {
                ClanMembershipRegistry.setRank(oldLeader, ClanRank.RIGHT_ARM);
            }

            ClanMembershipRegistry.setRank(this.playerId, ClanRank.LEADER);
            return;
        }

        // Don't silently demote the current leader via setRank()
        if (clan.isLeader(this.playerId)) {
            // Keep leader rank stable
            ClanMembershipRegistry.setRank(this.playerId, ClanRank.LEADER);
            return;
        }

        ClanRank mapped = (target == CRRank.RIGHT_ARM) ? ClanRank.RIGHT_ARM : ClanRank.MEMBER;
        ClanMembershipRegistry.setRank(this.playerId, mapped);
    }

    @Override
    public Rank getRank() {
        // Leader is authoritative on the Clan object
        if (this.clan.isLeader(playerId)) {
            return CRRank.LEADER;
        }

        ClanRank r = ClanMembershipRegistry.getRank(playerId);
        if (r == null) return CRRank.MEMBER;

        return switch (r) {
            case RIGHT_ARM -> CRRank.RIGHT_ARM;
            case LEADER -> CRRank.LEADER;   // if stored, still fine
            default -> CRRank.MEMBER;
        };
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
        var cp = ClanMembershipRegistry.getAnyClanPlayer(playerId);
        if (cp != null && cp.getLastKnownName() != null) {
            return cp.getLastKnownName();
        }
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
            throw new IllegalStateException("GriefDefender user not available for " + this.playerId);
        }
        return user.getPlayerData();
    }



    @Override
    public Claim getCurrentClaim() {
        var core = com.griefdefender.api.GriefDefender.getCore();
        var user = core.getUser(this.playerId);
        if (user == null) {
            // If GD hasn't created/loaded a User object for this UUID, no claim context.
            return null;
        }
        return user.getPlayerData().getCurrentClaim();
    }


}


