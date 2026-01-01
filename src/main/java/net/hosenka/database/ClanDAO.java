package net.hosenka.database;

import net.hosenka.clan.Clan;
import net.hosenka.clan.ClanMembershipRegistry;
import net.hosenka.clan.ClanRank;
import java.util.Locale;
import java.sql.*;
import java.util.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ClanDAO {

    public static void saveClan(Clan clan) throws SQLException {
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(
                "MERGE INTO clans (id, tag, name, leader, alliance, home_dim, home_x, home_y, home_z, home_yaw, home_pitch, home_server) " +
                        "KEY(id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setObject(1, clan.getId());
            ps.setString(2, clan.getTag());
            ps.setString(3, clan.getName());
            ps.setObject(4, clan.getLeaderId());
            ps.setObject(5, clan.getAllianceId());
            ps.setObject(1, clan.getId());
            ps.setString(2, clan.getTag());
            ps.setString(3, clan.getName());
            ps.setObject(4, clan.getLeaderId());
            ps.setObject(5, clan.getAllianceId());

            if (clan.hasHome()) {
                ps.setString(6, clan.getHomeDimension());
                ps.setDouble(7, clan.getHomeX());
                ps.setDouble(8, clan.getHomeY());
                ps.setDouble(9, clan.getHomeZ());
                ps.setFloat(10, clan.getHomeYaw());
                ps.setFloat(11, clan.getHomePitch());
                ps.setString(12, clan.getHomeServer());
            } else {
                ps.setObject(6, null);
                ps.setObject(7, null);
                ps.setObject(8, null);
                ps.setObject(9, null);
                ps.setObject(10, null);
                ps.setObject(11, null);
                ps.setObject(12, null);
            }

            ps.executeUpdate();
        }
    }



    public static void saveMembers(UUID clanId, Map<UUID, ClanRank> memberRanks) throws SQLException {
        Connection conn = DatabaseManager.getConnection();

        // Remove all members for this clan (players who left should disappear)
        try (PreparedStatement delete = conn.prepareStatement("DELETE FROM clan_members WHERE clan = ?")) {
            delete.setObject(1, clanId);
            delete.executeUpdate();
        }

        // Upsert current members
        try (PreparedStatement merge = conn.prepareStatement(
                "MERGE INTO clan_members (player, clan, rank) KEY(player) VALUES (?, ?, ?)")) {

            for (var e : memberRanks.entrySet()) {
                UUID playerId = e.getKey();
                ClanRank rank = (e.getValue() == null) ? ClanRank.MEMBER : e.getValue();

                merge.setObject(1, playerId);
                merge.setObject(2, clanId);
                merge.setString(3, rank.name());
                merge.addBatch();
            }
            merge.executeBatch();
        }
    }

    public static void saveMembers(Clan clan) throws SQLException {
        Map<UUID, ClanRank> ranks = new HashMap<>();
        UUID leader = clan.getLeaderId();

        for (UUID member : clan.getMembers()) {
            ClanRank r = ClanMembershipRegistry.getRank(member);
            if (r == null) r = ClanRank.MEMBER;

            // leaderId is authoritative
            if (leader != null && leader.equals(member)) {
                r = ClanRank.LEADER;
            } else if (r == ClanRank.LEADER) {
                // prevent “extra leaders” in member table
                r = ClanRank.MEMBER;
            }

            ranks.put(member, r);
        }

        saveMembers(clan.getId(), ranks);
    }






    public static void deleteClan(UUID clanId) throws SQLException {
        try (PreparedStatement ps = DatabaseManager.getConnection()
                .prepareStatement("DELETE FROM clans WHERE id = ?")) {
            ps.setObject(1, clanId);
            ps.executeUpdate();
        }
    }

    public static List<Clan> loadAllClans() throws SQLException {
        List<Clan> clans = new ArrayList<>();
        Map<UUID, Clan> clanMap = new HashMap<>();

        try (Statement stmt = DatabaseManager.getConnection().createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT * FROM clans");

            while (rs.next()) {
                UUID id = UUID.fromString(rs.getString("id"));
                String name = rs.getString("name");

                // New column
                String tag = rs.getString("tag");

                UUID leader = UUID.fromString(rs.getString("leader"));
                String allianceStr = rs.getString("alliance");
                UUID alliance = (allianceStr == null || allianceStr.isBlank()) ? null : UUID.fromString(allianceStr);

                // Back-compat: if loading an old row where tag is null, generate one from name
                if (tag == null || tag.isBlank()) {
                    tag = Clan.sanitizeTag(name != null ? name : id.toString());
                }

                Clan clan = new Clan(id, tag, name != null ? name : tag);
                clan.setLeaderId(leader);
                clan.setAllianceId(alliance);
                String homeDim = rs.getString("home_dim");
                if (homeDim != null && !homeDim.isBlank()) {
                    Double hx = (Double) rs.getObject("home_x");
                    Double hy = (Double) rs.getObject("home_y");
                    Double hz = (Double) rs.getObject("home_z");
                    Float yaw = (Float) rs.getObject("home_yaw");
                    Float pitch = (Float) rs.getObject("home_pitch");
                    String homeServer = rs.getString("home_server");

                    if (hx != null && hy != null && hz != null) {
                        clan.setHome(
                                homeDim,
                                hx, hy, hz,
                                yaw != null ? yaw : 0f,
                                pitch != null ? pitch : 0f,
                                homeServer != null ? homeServer : ""
                        );
                    }
                }


                clans.add(clan);
                clanMap.put(id, clan);
            }
        }

        return clans;
    }


    private static UUID uuidOrNull(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.isEmpty()) return null;
        try {
            return UUID.fromString(v);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static Map<UUID, Map<UUID, ClanRank>> loadAllMemberRanks() throws SQLException {
        Map<UUID, Map<UUID, ClanRank>> result = new HashMap<>();
        Connection conn = DatabaseManager.getConnection();

        // Try new schema first (with rank column). If missing, fallback to old schema.
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT player, clan, rank FROM clan_members");
            while (rs.next()) {
                UUID playerId = UUID.fromString(rs.getString("player"));
                UUID clanId = UUID.fromString(rs.getString("clan"));
                String rankStr = rs.getString("rank");

                ClanRank rank;
                try {
                    rank = (rankStr == null || rankStr.isBlank())
                            ? ClanRank.MEMBER
                            : ClanRank.valueOf(rankStr.trim().toUpperCase(Locale.ROOT));
                } catch (Exception ignored) {
                    rank = ClanRank.MEMBER;
                }

                result.computeIfAbsent(clanId, k -> new HashMap<>()).put(playerId, rank);
            }
            return result;
        } catch (SQLException oldSchema) {
            // Old schema: no rank column, treat everyone as MEMBER
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery("SELECT player, clan FROM clan_members");
                while (rs.next()) {
                    UUID playerId = UUID.fromString(rs.getString("player"));
                    UUID clanId = UUID.fromString(rs.getString("clan"));
                    result.computeIfAbsent(clanId, k -> new HashMap<>()).put(playerId, ClanRank.MEMBER);
                }
                return result;
            }
        }
    }

}
