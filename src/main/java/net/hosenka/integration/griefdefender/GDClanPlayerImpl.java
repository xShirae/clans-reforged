package net.hosenka.integration.griefdefender;

import com.griefdefender.api.Clan;
import com.griefdefender.api.ClanPlayer;
import com.griefdefender.api.GriefDefender;
import com.griefdefender.api.User;
import com.griefdefender.api.claim.Claim;
import com.griefdefender.api.clan.Rank;
import com.griefdefender.api.data.PlayerData;
import net.hosenka.api.ClansReforgedEvents;
import net.hosenka.clan.ClanMembershipRegistry;
import net.hosenka.clan.ClanRank;
import net.hosenka.database.ClanDAO;
import net.minecraft.server.level.ServerPlayer;

import java.sql.SQLException;
import java.util.UUID;

public class GDClanPlayerImpl implements ClanPlayer {

    private final UUID playerId;
    private final net.hosenka.clan.Clan clan;

    // cache validation
    private final UUID clanId;

    // GDHooks-style: keep a User reference (may be null early)
    private final User user;

    public GDClanPlayerImpl(UUID playerId, net.hosenka.clan.Clan clan) {
        this.playerId = playerId;
        this.clan = clan;
        this.clanId = clan.getId();
        this.user = GriefDefender.getCore().getUser(playerId);
    }

    // package-private accessor so provider can validate cached entries
    UUID getClanIdInternal() {
        return this.clanId;
    }

    private void gdInvalidate(UUID clanId, UUID... players) {
        var p = GriefDefender.getCore().getClanProvider();
        if (!(p instanceof ClansReforgedClanProvider cr)) return;

        if (clanId != null) cr.invalidateClan(clanId);

        if (players != null) {
            for (UUID id : players) {
                if (id != null) cr.invalidatePlayer(id);
            }
        }
    }

    private ClanRank getInternalRank(UUID who) {
        if (clan.isLeader(who)) return ClanRank.LEADER;
        ClanRank r = ClanMembershipRegistry.getRank(who);
        return (r == null) ? ClanRank.MEMBER : r;
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
        // Guard: only allow rank changes for members of THIS clan
        UUID currentClan = ClanMembershipRegistry.getClan(this.playerId);
        if (currentClan == null || !currentClan.equals(this.clanId)) {
            return;
        }

        // capture old ranks for events
        ClanRank oldSelf = getInternalRank(this.playerId);

        CRRank target = (rank instanceof CRRank cr) ? cr : CRRank.byName(rank.getName());

        // Promote to leader = transfer leadership
        if (target == CRRank.LEADER) {
            UUID oldLeader = clan.getLeaderId();

            // transfer
            clan.setLeaderId(this.playerId);
            ClanMembershipRegistry.setRank(this.playerId, ClanRank.LEADER);

            // old leader becomes RIGHT_ARM (only if still in this clan)
            if (oldLeader != null && !oldLeader.equals(this.playerId)) {
                UUID oldLeaderClan = ClanMembershipRegistry.getClan(oldLeader);
                if (oldLeaderClan != null && oldLeaderClan.equals(this.clanId)) {
                    ClanRank oldLeaderOldRank = getInternalRank(oldLeader);
                    ClanMembershipRegistry.setRank(oldLeader, ClanRank.RIGHT_ARM);

                    // persist + invalidate + event for old leader
                    try {
                        ClanDAO.saveClan(clan);
                        ClanDAO.saveMembers(clan);
                    } catch (SQLException e) {
                        net.hosenka.util.CRDebug.log("Failed to persist clan leader/rank change", e);
                    }

                    gdInvalidate(this.clanId, this.playerId, oldLeader);

                    ClansReforgedEvents.PLAYER_RANK_UPDATE.invoker()
                            .onRankUpdate(null, oldLeader, this.clanId, oldLeaderOldRank, ClanRank.RIGHT_ARM);
                } else {
                    // still persist clan leaderId at least
                    try {
                        ClanDAO.saveClan(clan);
                        ClanDAO.saveMembers(clan);
                    } catch (SQLException e) {
                        net.hosenka.util.CRDebug.log("Failed to persist clan leader/rank change", e);
                    }
                    gdInvalidate(this.clanId, this.playerId);
                }
            } else {
                try {
                    ClanDAO.saveClan(clan);
                    ClanDAO.saveMembers(clan);
                } catch (SQLException e) {
                    net.hosenka.util.CRDebug.log("Failed to persist clan leader/rank change", e);
                }
                gdInvalidate(this.clanId, this.playerId);
            }

            // event for new leader
            ClansReforgedEvents.PLAYER_RANK_UPDATE.invoker()
                    .onRankUpdate(null, this.playerId, this.clanId, oldSelf, ClanRank.LEADER);

            return;
        }

        // Don’t silently demote current leader via setRank()
        if (clan.isLeader(this.playerId)) {
            ClanMembershipRegistry.setRank(this.playerId, ClanRank.LEADER);
            return;
        }

        ClanRank mapped = (target == CRRank.RIGHT_ARM) ? ClanRank.RIGHT_ARM : ClanRank.MEMBER;
        if (oldSelf == mapped) {
            return; // no-op
        }

        ClanMembershipRegistry.setRank(this.playerId, mapped);

        try {
            ClanDAO.saveMembers(clan);
        } catch (SQLException e) {
            net.hosenka.util.CRDebug.log("Failed to persist clan rank change", e);
        }

        // invalidate caches (2)
        gdInvalidate(this.clanId, this.playerId);

        // event
        ClansReforgedEvents.PLAYER_RANK_UPDATE.invoker()
                .onRankUpdate(null, this.playerId, this.clanId, oldSelf, mapped);
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
        if (this.user != null) return this.user.isOnline();
        if (ServerHolder.get() == null) return false;
        return ServerHolder.get().getPlayerList().getPlayer(this.playerId) != null;
    }

    @Override
    public String getFriendlyName() {
        if (user != null) {
            String n = user.getFriendlyName();
            if (n != null && !n.isBlank()) return n;
        }

        var cp = ClanMembershipRegistry.getAnyClanPlayer(playerId);
        if (cp != null && cp.getLastKnownName() != null) {
            return cp.getLastKnownName();
        }

        return playerId.toString();
    }

    @Override
    public String getIdentifier() {
        if (user != null) {
            String id = user.getIdentifier();
            if (id != null && !id.isBlank()) return id;
        }
        return playerId.toString();
    }

    @Override
    public Object getOnlinePlayer() {
        if (this.user != null) return this.user.getOnlinePlayer();
        if (ServerHolder.get() == null) return null;
        return ServerHolder.get().getPlayerList().getPlayer(this.playerId);
    }

    @Override
    public PlayerData getPlayerData() {
        User u = this.user != null ? this.user : GriefDefender.getCore().getUser(this.playerId);
        if (u != null) {
            return u.getPlayerData();
        }

        // Fallback (no throw): use overworld playerdata if possible
        var server = ServerHolder.get();
        if (server != null) {
            var overworld = server.overworld();
            UUID worldId = GriefDefender.getCore().getWorldUniqueId(overworld);
            return GriefDefender.getCore().getPlayerData(worldId, this.playerId);
        }

        throw new IllegalStateException("Unable to resolve PlayerData for " + this.playerId);
    }

    @Override
    public Claim getCurrentClaim() {
        return getPlayerData().getCurrentClaim();
    }
}
