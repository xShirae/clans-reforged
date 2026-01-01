package net.hosenka.database;

import net.hosenka.clan.Clan;

import java.sql.*;
import java.util.*;

public class ClanDAO {

    public static void saveClan(Clan clan) throws SQLException {
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(
                "MERGE INTO clans (id, tag, name, leader, alliance) KEY(id) VALUES (?, ?, ?, ?, ?)")) {
            ps.setObject(1, clan.getId());
            ps.setString(2, clan.getTag());
            ps.setString(3, clan.getName());
            ps.setObject(4, clan.getLeaderId());
            ps.setObject(5, clan.getAllianceId());
            ps.executeUpdate();
        }
    }


    public static void saveMembers(UUID clanId, Set<UUID> members) throws SQLException {
        Connection conn = DatabaseManager.getConnection();

        // Remove any existing members for THIS clan first
        try (PreparedStatement delete = conn.prepareStatement("DELETE FROM clan_members WHERE clan = ?")) {
            delete.setObject(1, clanId);
            delete.executeUpdate();
        }

        // Upsert membership rows by PLAYER (player is PRIMARY KEY)
        try (PreparedStatement merge = conn.prepareStatement(
                "MERGE INTO clan_members (player, clan) KEY(player) VALUES (?, ?)")) {

            for (UUID player : members) {
                merge.setObject(1, player);
                merge.setObject(2, clanId);
                merge.addBatch();
            }
            merge.executeBatch();
        }
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

                clans.add(clan);
                clanMap.put(id, clan);
            }
        }

        try (Statement stmt = DatabaseManager.getConnection().createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT player, clan FROM clan_members");
            while (rs.next()) {
                UUID playerId = UUID.fromString(rs.getString("player"));
                UUID clanId = UUID.fromString(rs.getString("clan"));

                Clan clan = clanMap.get(clanId);
                if (clan != null) {
                    clan.addMember(playerId);
                }
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
}
