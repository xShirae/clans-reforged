// database/ClanDAO.java
package net.hosenka.database;

import net.hosenka.clan.Clan;

import java.sql.*;
import java.util.*;

public class ClanDAO {
    public static void saveClan(Clan clan) throws SQLException {
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(
                "MERGE INTO clans (id, name, leader, alliance) KEY(id) VALUES (?, ?, ?, ?)")) {
            ps.setObject(1, clan.getId());
            ps.setString(2, clan.getName());
            ps.setObject(3, clan.getLeaderId());
            ps.setObject(4, clan.getAllianceId());
            ps.executeUpdate();
        }
    }

    public static void saveMembers(UUID clanId, Set<UUID> members) throws SQLException {
        Connection conn = DatabaseManager.getConnection();

        try (PreparedStatement delete = conn.prepareStatement("DELETE FROM clan_members WHERE clan = ?")) {
            delete.setObject(1, clanId);
            delete.executeUpdate();
        }

        try (PreparedStatement insert = conn.prepareStatement("INSERT INTO clan_members (player, clan) VALUES (?, ?)")) {
            for (UUID player : members) {
                insert.setObject(1, player);
                insert.setObject(2, clanId);
                insert.addBatch();
            }
            insert.executeBatch();
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

        try (Statement stmt = DatabaseManager.getConnection().createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT * FROM clans");

            while (rs.next()) {
                UUID id = UUID.fromString(rs.getString("id"));
                String name = rs.getString("name");
                UUID leader = UUID.fromString(rs.getString("leader"));
                String allianceStr = rs.getString("alliance");
                UUID alliance = (allianceStr != null) ? UUID.fromString(allianceStr) : null;

                Clan clan = new Clan(id, name);
                clan.setLeaderId(leader);
                clan.setAllianceId(alliance);
                clans.add(clan);
            }
        }

        return clans;
    }
}
