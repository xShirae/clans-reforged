package net.hosenka.integration.griefdefender;

import com.griefdefender.api.Clan;
import com.griefdefender.api.ClanPlayer;
import com.griefdefender.api.GriefDefender;
import com.griefdefender.api.User;
import com.griefdefender.api.claim.Claim;
import com.griefdefender.api.clan.Rank;
import com.griefdefender.api.data.PlayerData;
import net.hosenka.clan.ClanMembershipRegistry;
import net.hosenka.clan.ClanRank;
import net.hosenka.database.ClanDAO;

import java.sql.SQLException;
import java.util.UUID;

public class GDClanPlayerImpl implements ClanPlayer {

    private final UUID playerId;
    private final net.hosenka.clan.Clan clan;

    // cache validation
    private final UUID clanId;

    // GDHooks-style: keep a User reference
    private final User user;

    public GDClanPlayerImpl(UUID playerId, net.hosenka.clan.Clan clan) {
        this.playerId = playerId;
        this.clan = clan;
        this.clanId = clan.getId();
        this.user = GriefDefender.getCore().getUser(playerId); // typically non-null in GD
    }

    UUID getClanIdInternal() {
        return this.clanId;
    }

    @Override
    public UUID getUniqueId() {
        return playerId;
    }

    @Override
    public Clan getClan() {
        var p = GriefDefender.getCore().getClanProvider();
        if (p instanceof ClansReforgedClanProvider cr) {
            Clan cached = cr.getClanById(this.clanId);
            if (cached != null) return cached;
        }
        return new GDClanImpl(clan);
    }

    @Override
    public void setRank(Rank rank) {
        // guard: only allow rank changes for members of THIS clan
        UUID currentClan = ClanMembershipRegistry.getClan(this.playerId);
        if (currentClan == null || !currentClan.equals(this.clanId)) {
            return;
        }

        CRRank target = (rank instanceof CRRank cr) ? cr : CRRank.byName(rank.getName());

        // Promote to leader = transfer leadership
        if (target == CRRank.LEADER) {
            UUID oldLeader = clan.getLeaderId();
            clan.setLeaderId(this.playerId);

            if (oldLeader != null && !oldLeader.equals(this.playerId)) {
                // only change old leader rank if they still belong to this clan
                UUID oldLeaderClan = ClanMembershipRegistry.getClan(oldLeader);
                if (oldLeaderClan != null && oldLeaderClan.equals(this.clanId)) {
                    ClanMembershipRegistry.setRank(oldLeader, ClanRank.RIGHT_ARM);
                }
            }

            ClanMembershipRegistry.setRank(this.playerId, ClanRank.LEADER);

            try {
                ClanDAO.saveClan(clan);
                ClanDAO.saveMembers(clan); // uses the new overload
            } catch (SQLException e) {
                net.hosenka.util.CRDebug.log("Failed to persist clan leader/rank change", e);
            }

            return;
        }
        // Don’t silently demote current leader via setRank()
        if (clan.isLeader(this.playerId)) {
            ClanMembershipRegistry.setRank(this.playerId, ClanRank.LEADER);
            return;
        }

        ClanRank mapped = (target == CRRank.RIGHT_ARM) ? ClanRank.RIGHT_ARM : ClanRank.MEMBER;
        ClanMembershipRegistry.setRank(this.playerId, mapped);

        try {
            ClanDAO.saveMembers(clan);
        } catch (SQLException e) {
            net.hosenka.util.CRDebug.log("Failed to persist clan rank change", e);
        }

    }

    @Override
    public Rank getRank() {
        if (clan.isLeader(playerId)) return CRRank.LEADER;

        ClanRank r = ClanMembershipRegistry.getRank(playerId);
        if (r == null) return CRRank.MEMBER;

        return switch (r) {
            case RIGHT_ARM -> CRRank.RIGHT_ARM;
            case LEADER -> CRRank.LEADER;
            default -> CRRank.MEMBER;
        };
    }

    @Override
    public boolean isLeader() {
        return clan.isLeader(playerId);
    }

    @Override
    public boolean isOnline() {
        // GDHooks-style
        return user != null && user.isOnline();
    }

    @Override
    public String getFriendlyName() {
        // prefer GD User name
        if (user != null) {
            String n = user.getFriendlyName();
            if (n != null && !n.isBlank()) return n;
        }

        // fallback to cached last-known name from your registry
        var cp = ClanMembershipRegistry.getAnyClanPlayer(playerId);
        if (cp != null && cp.getLastKnownName() != null) {
            return cp.getLastKnownName();
        }

        return playerId.toString();
    }

    @Override
    public String getIdentifier() {
        // GDHooks-style (but keep UUID fallback)
        if (user != null) {
            String id = user.getIdentifier();
            if (id != null && !id.isBlank()) return id;
        }
        return playerId.toString();
    }

    @Override
    public Object getOnlinePlayer() {
        return user != null ? user.getOnlinePlayer() : null;
    }

    @Override
    public PlayerData getPlayerData() {
        if (user == null) {
            throw new IllegalStateException("GriefDefender user not available for " + this.playerId);
        }
        return user.getPlayerData();
    }

    @Override
    public Claim getCurrentClaim() {
        if (user == null) return null;
        return user.getPlayerData().getCurrentClaim();
    }
}
