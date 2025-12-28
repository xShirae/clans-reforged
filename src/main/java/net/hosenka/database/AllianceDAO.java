// database/AllianceDAO.java
package net.hosenka.database;

import net.hosenka.alliance.Alliance;

import java.sql.*;
import java.util.*;

public class AllianceDAO {

    public static void saveAlliance(UUID id, Alliance alliance) throws SQLException {
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(
                "MERGE INTO alliances (id, name) KEY(id) VALUES (?, ?)")) {
            ps.setObject(1, id);
            ps.setString(2, alliance.getName());
            ps.executeUpdate();
        }

        try (PreparedStatement delete = DatabaseManager.getConnection().prepareStatement(
                "DELETE FROM alliance_clans WHERE alliance = ?")) {
            delete.setObject(1, id);
            delete.executeUpdate();
        }

        try (PreparedStatement insert = DatabaseManager.getConnection().prepareStatement(
                "INSERT INTO alliance_clans (clan, alliance) VALUES (?, ?)")) {
            for (UUID clanId : alliance.getClans()) {
                insert.setObject(1, clanId);
                insert.setObject(2, id);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    public static Map<UUID, Alliance> loadAllAlliances() throws SQLException {
        Map<UUID, Alliance> alliances = new HashMap<>();

        try (Statement stmt = DatabaseManager.getConnection().createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT * FROM alliances");

            while (rs.next()) {
                UUID id = UUID.fromString(rs.getString("id"));
                String name = rs.getString("name");

                Alliance alliance = new Alliance(name);
                alliances.put(id, alliance);
            }
        }



        // Load alliance_clans mapping
        try (Statement stmt = DatabaseManager.getConnection().createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT * FROM alliance_clans");

            while (rs.next()) {
                UUID clanId = UUID.fromString(rs.getString("clan"));
                UUID allianceId = UUID.fromString(rs.getString("alliance"));

                Alliance alliance = alliances.get(allianceId);
                if (alliance != null) {
                    alliance.addClan(clanId);
                }
            }
        }

        return alliances;
    }

    public static void deleteAlliance(UUID id) throws SQLException {
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(
                "DELETE FROM alliances WHERE id = ?")) {
            ps.setObject(1, id);
            ps.executeUpdate();
        }
    }

}
