// command/AllianceCommand.java
package net.hosenka.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.hosenka.alliance.Alliance;
import net.hosenka.alliance.AllianceRegistry;
import net.hosenka.clan.Clan;
import net.hosenka.clan.ClanMembershipRegistry;
import net.hosenka.clan.ClanRegistry;
import net.hosenka.database.AllianceDAO;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

public class AllianceCommand {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("alliance")

                    // /alliance create <name>
                    .then(CommandManager.literal("create")
                            .then(CommandManager.argument("name", StringArgumentType.string())
                                    .executes(context -> {
                                        if (!context.getSource().hasPermissionLevel(2)) {
                                            context.getSource().sendError(Text.literal("You do not have permission to create alliances."));
                                            return 0;
                                        }

                                        String name = StringArgumentType.getString(context, "name");
                                        UUID id = AllianceRegistry.createAlliance(name);
                                        Alliance alliance = AllianceRegistry.getAlliance(id);

                                        try {
                                            AllianceDAO.saveAlliance(id, alliance);
                                        } catch (SQLException e) {
                                            e.printStackTrace();
                                            context.getSource().sendError(Text.literal("Failed to save alliance to database."));
                                            return 0;
                                        }

                                        context.getSource().sendFeedback(() -> Text.literal("Alliance created: " + name), false);
                                        return 1;
                                    })))

                    // /alliance delete <name>
                    .then(CommandManager.literal("delete")
                            .then(CommandManager.argument("name", StringArgumentType.string())
                                    .executes(context -> {
                                        if (!context.getSource().hasPermissionLevel(2)) {
                                            context.getSource().sendError(Text.literal("You do not have permission to delete alliances."));
                                            return 0;
                                        }

                                        String name = StringArgumentType.getString(context, "name");
                                        UUID toDelete = null;

                                        for (Map.Entry<UUID, Alliance> entry : AllianceRegistry.getAllAlliances().entrySet()) {
                                            if (entry.getValue().getName().equalsIgnoreCase(name)) {
                                                toDelete = entry.getKey();
                                                break;
                                            }
                                        }

                                        if (toDelete == null) {
                                            context.getSource().sendError(Text.literal("Alliance '" + name + "' not found."));
                                            return 0;
                                        }

                                        Alliance deleted = AllianceRegistry.getAlliance(toDelete);
                                        for (UUID clanId : deleted.getClans()) {
                                            Clan clan = ClanRegistry.getClan(clanId);
                                            if (clan != null) {
                                                clan.setAllianceId(null);
                                            }
                                        }

                                        try {
                                            AllianceDAO.deleteAlliance(toDelete);
                                        } catch (SQLException e) {
                                            e.printStackTrace();
                                        }

                                        AllianceRegistry.deleteAlliance(toDelete);

                                        context.getSource().sendFeedback(() -> Text.literal("Alliance '" + name + "' has been deleted."), false);
                                        return 1;
                                    })))

                    // /alliance join <name>
                    .then(CommandManager.literal("join")
                            .then(CommandManager.argument("name", StringArgumentType.string())
                                    .executes(context -> {
                                        UUID playerId = context.getSource().getPlayer().getUuid();
                                        if (!ClanMembershipRegistry.isInClan(playerId)) {
                                            context.getSource().sendError(Text.literal("You must be in a clan to join an alliance."));
                                            return 0;
                                        }

                                        String name = StringArgumentType.getString(context, "name");
                                        UUID clanId = ClanMembershipRegistry.getClan(playerId);
                                        Clan clan = ClanRegistry.getClan(clanId);

                                        if (clan == null) {
                                            context.getSource().sendError(Text.literal("Clan not found."));
                                            return 0;
                                        }

                                        if (clan.getAllianceId() != null) {
                                            context.getSource().sendError(Text.literal("Your clan is already in an alliance."));
                                            return 0;
                                        }

                                        Alliance target = null;
                                        UUID allianceId = null;

                                        for (Map.Entry<UUID, Alliance> entry : AllianceRegistry.getAllAlliances().entrySet()) {
                                            if (entry.getValue().getName().equalsIgnoreCase(name)) {
                                                target = entry.getValue();
                                                allianceId = entry.getKey();
                                                break;
                                            }
                                        }

                                        if (target == null) {
                                            context.getSource().sendError(Text.literal("Alliance '" + name + "' not found."));
                                            return 0;
                                        }

                                        target.addClan(clanId);
                                        clan.setAllianceId(allianceId);

                                        try {
                                            AllianceDAO.saveAlliance(allianceId, target);
                                        } catch (SQLException e) {
                                            e.printStackTrace();
                                        }

                                        context.getSource().sendFeedback(() -> Text.literal("Your clan has joined the alliance: " + name), false);
                                        return 1;
                                    })))

                    // /alliance leave
                    .then(CommandManager.literal("leave")
                            .executes(context -> {
                                UUID playerId = context.getSource().getPlayer().getUuid();
                                if (!ClanMembershipRegistry.isInClan(playerId)) {
                                    context.getSource().sendError(Text.literal("You must be in a clan to leave an alliance."));
                                    return 0;
                                }

                                UUID clanId = ClanMembershipRegistry.getClan(playerId);
                                Clan clan = ClanRegistry.getClan(clanId);

                                if (clan == null || clan.getAllianceId() == null) {
                                    context.getSource().sendError(Text.literal("Your clan is not in an alliance."));
                                    return 0;
                                }

                                Alliance alliance = AllianceRegistry.getAlliance(clan.getAllianceId());
                                if (alliance != null) {
                                    alliance.removeClan(clanId);
                                    try {
                                        AllianceDAO.saveAlliance(clan.getAllianceId(), alliance);
                                    } catch (SQLException e) {
                                        e.printStackTrace();
                                    }
                                }

                                clan.setAllianceId(null);

                                context.getSource().sendFeedback(() -> Text.literal("Your clan has left its alliance."), false);
                                return 1;
                            }))

                    // /alliance info <name>
                    .then(CommandManager.literal("info")
                            .then(CommandManager.argument("name", StringArgumentType.string())
                                    .executes(context -> {
                                        String name = StringArgumentType.getString(context, "name");

                                        Alliance alliance = null;
                                        for (Alliance a : AllianceRegistry.getAllAlliances().values()) {
                                            if (a.getName().equalsIgnoreCase(name)) {
                                                alliance = a;
                                                break;
                                            }
                                        }

                                        if (alliance == null) {
                                            context.getSource().sendError(Text.literal("Alliance '" + name + "' not found."));
                                            return 0;
                                        }

                                        StringBuilder sb = new StringBuilder();
                                        sb.append("§6Alliance Info: ").append(alliance.getName()).append("\n");
                                        sb.append("§7Clans (").append(alliance.getClans().size()).append("):\n");

                                        for (UUID clanId : alliance.getClans()) {
                                            Clan clan = ClanRegistry.getClan(clanId);
                                            if (clan != null) {
                                                sb.append("- ").append(clan.getName()).append("\n");
                                            } else {
                                                sb.append("- Unknown Clan (").append(clanId).append(")\n");
                                            }
                                        }

                                        context.getSource().sendFeedback(() -> Text.literal(sb.toString().trim()), false);
                                        return 1;
                                    }))
                    )

                    // /alliance list
                    .then(CommandManager.literal("list")
                            .executes(context -> {
                                var allAlliances = AllianceRegistry.getAllAlliances();

                                if (allAlliances.isEmpty()) {
                                    context.getSource().sendFeedback(
                                            () -> Text.literal("No alliances have been created yet."),
                                            false
                                    );
                                    return 1;
                                }

                                StringBuilder sb = new StringBuilder("Existing alliances:\n");
                                for (Alliance alliance : allAlliances.values()) {
                                    sb.append("- ").append(alliance.getName())
                                            .append(" (").append(alliance.getClans().size()).append(" clans)\n");
                                }

                                context.getSource().sendFeedback(
                                        () -> Text.literal(sb.toString().trim()),
                                        false
                                );
                                return 1;
                            })
                    )

                    // /alliance rename <old> <new>
                    .then(CommandManager.literal("rename")
                            .then(CommandManager.argument("old", StringArgumentType.string())
                                    .then(CommandManager.argument("new", StringArgumentType.string())
                                            .executes(context -> {
                                                if (!context.getSource().hasPermissionLevel(2)) {
                                                    context.getSource().sendError(Text.literal("You do not have permission to rename alliances."));
                                                    return 0;
                                                }

                                                String oldName = StringArgumentType.getString(context, "old");
                                                String newName = StringArgumentType.getString(context, "new");

                                                UUID idToRename = null;
                                                Alliance target = null;

                                                for (Map.Entry<UUID, Alliance> entry : AllianceRegistry.getAllAlliances().entrySet()) {
                                                    if (entry.getValue().getName().equalsIgnoreCase(oldName)) {
                                                        idToRename = entry.getKey();
                                                        target = entry.getValue();
                                                        break;
                                                    }
                                                }

                                                if (target == null || idToRename == null) {
                                                    context.getSource().sendError(Text.literal("Alliance '" + oldName + "' not found."));
                                                    return 0;
                                                }

                                                // Check name collision
                                                for (Alliance alliance : AllianceRegistry.getAllAlliances().values()) {
                                                    if (alliance.getName().equalsIgnoreCase(newName)) {
                                                        context.getSource().sendError(Text.literal("Another alliance with that name already exists."));
                                                        return 0;
                                                    }
                                                }

                                                target.setName(newName);

                                                try {
                                                    AllianceDAO.saveAlliance(idToRename, target);
                                                } catch (SQLException e) {
                                                    e.printStackTrace();
                                                }

                                                context.getSource().sendFeedback(() ->
                                                        Text.literal("Alliance '" + oldName + "' has been renamed to '" + newName + "'."), false);
                                                return 1;
                                            }))))
            );
        });
    }
}
