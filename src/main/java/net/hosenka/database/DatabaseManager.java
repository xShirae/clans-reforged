// database/DatabaseManager.java
package net.hosenka.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    private static Connection connection;

    public static void initialize() {
        try {
            // Creates file-based DB at ./clansreforged.mv.db
            connection = DriverManager.getConnection("jdbc:h2:file:./clansreforged;AUTO_SERVER=TRUE");

            // Initialize schema
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS clans (
                        id UUID PRIMARY KEY,
                        name VARCHAR(64) UNIQUE NOT NULL,
                        leader UUID NOT NULL,
                        alliance UUID
                    );
                """);

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS clan_members (
                        player UUID PRIMARY KEY,
                        clan UUID NOT NULL,
                        FOREIGN KEY (clan) REFERENCES clans(id) ON DELETE CASCADE
                    );
                """);

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS alliances (
                        id UUID PRIMARY KEY,
                        name VARCHAR(64) UNIQUE NOT NULL
                    );
                """);

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS alliance_clans (
                        clan UUID PRIMARY KEY,
                        alliance UUID NOT NULL,
                        FOREIGN KEY (alliance) REFERENCES alliances(id) ON DELETE CASCADE
                    );
                """);

                System.out.println("[ClansReforged] H2 database initialized successfully.");
            }

        } catch (SQLException e) {
            System.err.println("[ClansReforged] Failed to initialize database:");
            e.printStackTrace();
        }
    }

    public static Connection getConnection() {
        return connection;
    }
}
