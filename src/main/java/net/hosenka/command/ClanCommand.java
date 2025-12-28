// command/ClanCommand.java
package net.hosenka.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.hosenka.alliance.Alliance;
import net.hosenka.alliance.AllianceRegistry;
import net.hosenka.clan.Clan;
import net.hosenka.clan.ClanMembershipRegistry;
import net.hosenka.clan.ClanRegistry;
import net.hosenka.database.ClanDAO;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

public class ClanCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("clan")

                    /* ===================== CREATE ===================== */
                    .then(CommandManager.literal("create")
                            .then(CommandManager.argument("name", StringArgumentType.string())
                                    .executes(context -> {
                                        String name = StringArgumentType.getString(context, "name");
                                        UUID playerId = context.getSource().getPlayer().getUuid();

                                        UUID clanId = ClanRegistry.createClan(name, playerId);
                                        ClanMembershipRegistry.joinClan(playerId, clanId);

                                        Clan clan = ClanRegistry.getClan(clanId);

                                        try {
                                            ClanDAO.saveClan(clan);
                                            ClanDAO.saveMembers(clanId, clan.getMembers());
                                        } catch (SQLException e) {
                                            e.printStackTrace();
                                            context.getSource().sendError(Text.literal("Failed to save clan to database."));
                                            return 0;
                                        }

                                        context.getSource().sendFeedback(
                                                () -> Text.literal("Clan created: " + name),
                                                false
                                        );
                                        return 1;
                                    })))

                    /* ===================== JOIN ===================== */
                    .then(CommandManager.literal("join")
                            .then(CommandManager.argument("name", StringArgumentType.string())
                                    .executes(context -> {
                                        String name = StringArgumentType.getString(context, "name");
                                        UUID playerId = context.getSource().getPlayer().getUuid();

                                        if (ClanMembershipRegistry.isInClan(playerId)) {
                                            context.getSource().sendError(Text.literal("You're already in a clan."));
                                            return 0;
                                        }

                                        Clan targetClan = null;
                                        for (Clan clan : ClanRegistry.getAllClans().values()) {
                                            if (clan.getName().equalsIgnoreCase(name)) {
                                                targetClan = clan;
                                                break;
                                            }
                                        }

                                        if (targetClan == null) {
                                            context.getSource().sendError(Text.literal("Clan '" + name + "' not found."));
                                            return 0;
                                        }

                                        targetClan.addMember(playerId);
                                        ClanMembershipRegistry.joinClan(playerId, targetClan.getId());

                                        try {
                                            ClanDAO.saveMembers(targetClan.getId(), targetClan.getMembers());
                                        } catch (SQLException e) {
                                            e.printStackTrace();
                                        }

                                        context.getSource().sendFeedback(
                                                () -> Text.literal("You have joined the clan: " + name),
                                                false
                                        );
                                        return 1;
                                    })))

                    /* ===================== LEAVE ===================== */
                    .then(CommandManager.literal("leave")
                            .executes(context -> {
                                UUID playerId = context.getSource().getPlayer().getUuid();

                                if (!ClanMembershipRegistry.isInClan(playerId)) {
                                    context.getSource().sendError(Text.literal("You're not in a clan."));
                                    return 0;
                                }

                                UUID clanId = ClanMembershipRegistry.getClan(playerId);
                                Clan clan = ClanRegistry.getClan(clanId);

                                if (clan != null) {
                                    clan.removeMember(playerId);

                                    try {
                                        ClanDAO.saveMembers(clanId, clan.getMembers());
                                    } catch (SQLException e) {
                                        e.printStackTrace();
                                    }
                                }

                                ClanMembershipRegistry.leaveClan(playerId);
                                ClanRegistry.cleanupEmptyClans();

                                context.getSource().sendFeedback(
                                        () -> Text.literal("You have left your clan."),
                                        false
                                );
                                return 1;
                            })
                    )

                    /* ===================== LIST ===================== */
                    .then(CommandManager.literal("list")
                            .executes(context -> {
                                var allClans = ClanRegistry.getAllClans();

                                if (allClans.isEmpty()) {
                                    context.getSource().sendFeedback(
                                            () -> Text.literal("No clans have been created yet."),
                                            false
                                    );
                                    return 1;
                                }

                                StringBuilder sb = new StringBuilder("Existing clans:\n");
                                allClans.values().forEach(clan ->
                                        sb.append("- ").append(clan.getName()).append("\n")
                                );

                                context.getSource().sendFeedback(
                                        () -> Text.literal(sb.toString().trim()),
                                        false
                                );
                                return 1;
                            })
                    )

                    /* ===================== DELETE ===================== */
                    .then(CommandManager.literal("delete")
                            .then(CommandManager.argument("name", StringArgumentType.string())
                                    .executes(context -> {
                                        String name = StringArgumentType.getString(context, "name");
                                        UUID clanIdToDelete = null;
                                        Clan clanToDelete = null;

                                        for (Map.Entry<UUID, Clan> entry : ClanRegistry.getAllClans().entrySet()) {
                                            if (entry.getValue().getName().equalsIgnoreCase(name)) {
                                                clanIdToDelete = entry.getKey();
                                                clanToDelete = entry.getValue();
                                                break;
                                            }
                                        }

                                        if (clanToDelete == null) {
                                            context.getSource().sendError(Text.literal("Clan '" + name + "' not found."));
                                            return 0;
                                        }

                                        //  Remove from alliance FIRST
                                        UUID allianceId = clanToDelete.getAllianceId();
                                        if (allianceId != null) {
                                            Alliance alliance = AllianceRegistry.getAlliance(allianceId);
                                            if (alliance != null) {
                                                alliance.removeClan(clanIdToDelete);
                                                try {
                                                    net.hosenka.database.AllianceDAO.saveAlliance(allianceId, alliance);
                                                } catch (SQLException e) {
                                                    e.printStackTrace();
                                                    context.getSource().sendError(Text.literal("Failed to update alliance."));
                                                    return 0;
                                                }
                                            }
                                        }

                                        // Remove members
                                        for (UUID memberId : clanToDelete.getMembers()) {
                                            ClanMembershipRegistry.leaveClan(memberId);
                                        }

                                        // Delete from DB
                                        try {
                                            ClanDAO.deleteClan(clanIdToDelete);
                                        } catch (SQLException e) {
                                            e.printStackTrace();
                                            context.getSource().sendError(Text.literal("Failed to delete clan from database."));
                                            return 0;
                                        }

                                        // Remove from registry
                                        ClanRegistry.getAllClans().remove(clanIdToDelete);

                                        context.getSource().sendFeedback(
                                                () -> Text.literal("Clan '" + name + "' has been deleted."),
                                                false
                                        );
                                        return 1;
                                    })
                            )
                    )


                    /* ===================== INFO ===================== */
                    .then(CommandManager.literal("info")
                            .then(CommandManager.argument("name", StringArgumentType.string())
                                    .executes(context -> {
                                        String name = StringArgumentType.getString(context, "name");

                                        Clan targetClan = null;
                                        for (Clan clan : ClanRegistry.getAllClans().values()) {
                                            if (clan.getName().equalsIgnoreCase(name)) {
                                                targetClan = clan;
                                                break;
                                            }
                                        }

                                        if (targetClan == null) {
                                            context.getSource().sendError(Text.literal("Clan '" + name + "' not found."));
                                            return 0;
                                        }

                                        StringBuilder sb = new StringBuilder();
                                        sb.append("§6Clan Info: ").append(targetClan.getName()).append("\n");

                                        UUID leaderId = targetClan.getLeaderId();
                                        sb.append("§7Leader: ");
                                        if (leaderId != null) {
                                            GameProfile profile = context.getSource().getServer()
                                                    .getUserCache().getByUuid(leaderId).orElse(null);
                                            sb.append(profile != null ? profile.getName() : leaderId.toString());
                                        } else {
                                            sb.append("None");
                                        }
                                        sb.append("\n");

                                        sb.append("§7Alliance: ");
                                        UUID allianceId = targetClan.getAllianceId();
                                        if (allianceId == null) {
                                            sb.append("None");
                                        } else {
                                            Alliance alliance = AllianceRegistry.getAlliance(allianceId);
                                            sb.append(alliance != null ? alliance.getName() : "Unknown");
                                        }

                                        context.getSource().sendFeedback(
                                                () -> Text.literal(sb.toString()),
                                                false
                                        );
                                        return 1;
                                    })
                            )
                    )
                    /* ===================== DISBAND ===================== */
                    .then(CommandManager.literal("disband")
                            .executes(context -> {
                                UUID playerId = context.getSource().getPlayer().getUuid();
                                UUID clanId = ClanMembershipRegistry.getClan(playerId);

                                if (clanId == null) {
                                    context.getSource().sendError(Text.literal("You are not in a clan."));
                                    return 0;
                                }

                                Clan clan = ClanRegistry.getClan(clanId);

                                if (clan == null || !clan.isLeader(playerId)) {
                                    context.getSource().sendError(Text.literal("Only the clan leader can disband the clan."));
                                    return 0;
                                }

                                // Remove from alliance
                                UUID allianceId = clan.getAllianceId();
                                if (allianceId != null) {
                                    Alliance alliance = AllianceRegistry.getAlliance(allianceId);
                                    if (alliance != null) {
                                        alliance.removeClan(clanId);
                                        try {
                                            net.hosenka.database.AllianceDAO.saveAlliance(allianceId, alliance);
                                        } catch (SQLException e) {
                                            e.printStackTrace();
                                            context.getSource().sendError(Text.literal("Failed to update alliance."));
                                            return 0;
                                        }
                                    }
                                }

                                // Remove all members
                                for (UUID memberId : clan.getMembers()) {
                                    ClanMembershipRegistry.leaveClan(memberId);
                                }

                                // Delete from DB
                                try {
                                    ClanDAO.deleteClan(clanId);
                                } catch (SQLException e) {
                                    e.printStackTrace();
                                    context.getSource().sendError(Text.literal("Failed to delete clan from database."));
                                    return 0;
                                }

                                // Remove from memory
                                ClanRegistry.getAllClans().remove(clanId);

                                context.getSource().sendFeedback(() ->
                                        Text.literal("Your clan '" + clan.getName() + "' has been disbanded."), false);

                                return 1;
                            })
                    )

                    /* ===================== INVITE ===================== */
                    .then(CommandManager.literal("invite")
                            .then(CommandManager.argument("player", StringArgumentType.word())
                                    .executes(context -> {
                                        UUID inviterId = context.getSource().getPlayer().getUuid();
                                        UUID clanId = ClanMembershipRegistry.getClan(inviterId);

                                        if (clanId == null) {
                                            context.getSource().sendError(Text.literal("You are not in a clan."));
                                            return 0;
                                        }

                                        Clan clan = ClanRegistry.getClan(clanId);
                                        if (clan == null || !clan.isLeader(inviterId)) {
                                            context.getSource().sendError(Text.literal("Only the clan leader can invite players."));
                                            return 0;
                                        }

                                        String targetName = StringArgumentType.getString(context, "player");
                                        var targetProfile = context.getSource().getServer().getUserCache()
                                                .findByName(targetName).orElse(null);

                                        if (targetProfile == null) {
                                            context.getSource().sendError(Text.literal("Player not found: " + targetName));
                                            return 0;
                                        }

                                        UUID targetId = targetProfile.getId();

                                        if (ClanMembershipRegistry.isInClan(targetId)) {
                                            context.getSource().sendError(Text.literal("That player is already in a clan."));
                                            return 0;
                                        }

                                        ClanRegistry.invitePlayer(targetId, clanId);
                                        context.getSource().sendFeedback(() ->
                                                Text.literal("Invited " + targetName + " to the clan."), false);

                                        return 1;
                                    })))

                    /* ===================== JOIN ===================== */
                    .then(CommandManager.literal("join")
                            .executes(context -> {
                                UUID playerId = context.getSource().getPlayer().getUuid();

                                if (ClanMembershipRegistry.isInClan(playerId)) {
                                    context.getSource().sendError(Text.literal("You're already in a clan."));
                                    return 0;
                                }

                                UUID invitedClanId = ClanRegistry.getPendingInvite(playerId);
                                if (invitedClanId == null) {
                                    context.getSource().sendError(Text.literal("You don't have any pending clan invites."));
                                    return 0;
                                }

                                Clan clan = ClanRegistry.getClan(invitedClanId);
                                if (clan == null) {
                                    context.getSource().sendError(Text.literal("The clan you were invited to no longer exists."));
                                    return 0;
                                }

                                clan.addMember(playerId);
                                ClanMembershipRegistry.joinClan(playerId, invitedClanId);
                                ClanRegistry.clearInvite(playerId);

                                try {
                                    ClanDAO.saveMembers(invitedClanId, clan.getMembers());
                                } catch (SQLException e) {
                                    e.printStackTrace();
                                }

                                context.getSource().sendFeedback(() ->
                                        Text.literal("You have joined the clan: " + clan.getName()), false);

                                return 1;
                            }))

                    /* ===================== STATUS ===================== */
                    .then(CommandManager.literal("status")
                            .executes(context -> {
                                UUID playerId = context.getSource().getPlayer().getUuid();

                                UUID clanId = ClanMembershipRegistry.getClan(playerId);
                                Clan clan = clanId != null ? ClanRegistry.getClan(clanId) : null;

                                String clanName = clan != null ? clan.getName() : "None";

                                String allianceName = "None";
                                if (clan != null && clan.getAllianceId() != null) {
                                    Alliance alliance = AllianceRegistry.getAlliance(clan.getAllianceId());
                                    if (alliance != null) {
                                        allianceName = alliance.getName();
                                    }
                                }

                                StringBuilder sb = new StringBuilder();
                                sb.append("§6Your Clan Status:\n");
                                sb.append("§7Clan: ").append(clanName).append("\n");
                                sb.append("§7Alliance: ").append(allianceName);

                                context.getSource().sendFeedback(() -> Text.literal(sb.toString()), false);
                                return 1;
                            }))





                    /* ===================== RENAME ===================== */
                    .then(CommandManager.literal("rename")
                            .then(CommandManager.argument("old", StringArgumentType.string())
                                    .then(CommandManager.argument("new", StringArgumentType.string())
                                            .executes(context -> {
                                                String oldName = StringArgumentType.getString(context, "old");
                                                String newName = StringArgumentType.getString(context, "new");
                                                UUID playerId = context.getSource().getPlayer().getUuid();

                                                Clan targetClan = null;
                                                for (Clan clan : ClanRegistry.getAllClans().values()) {
                                                    if (clan.getName().equalsIgnoreCase(oldName)) {
                                                        targetClan = clan;
                                                        break;
                                                    }
                                                }

                                                if (targetClan == null) {
                                                    context.getSource().sendError(Text.literal("Clan '" + oldName + "' not found."));
                                                    return 0;
                                                }

                                                if (!targetClan.isLeader(playerId) && !context.getSource().hasPermissionLevel(2)) {
                                                    context.getSource().sendError(Text.literal("Only the clan leader or an admin can rename the clan."));
                                                    return 0;
                                                }

                                                for (Clan clan : ClanRegistry.getAllClans().values()) {
                                                    if (clan.getName().equalsIgnoreCase(newName)) {
                                                        context.getSource().sendError(Text.literal("Another clan with that name already exists."));
                                                        return 0;
                                                    }
                                                }

                                                targetClan.setName(newName);

                                                try {
                                                    ClanDAO.saveClan(targetClan);
                                                } catch (SQLException e) {
                                                    e.printStackTrace();
                                                }

                                                context.getSource().sendFeedback(
                                                        () -> Text.literal("Clan '" + oldName + "' has been renamed to '" + newName + "'."),
                                                        false
                                                );
                                                return 1;
                                            }))))
            );
        });
    }
}
