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
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

public class ClanCommand {

    private static final String TAG_REGEX = "^[A-Za-z0-9]{1,6}$";

    private static Clan findClanByTagOrName(String input) {
        if (input == null || input.isBlank()) return null;

        Clan byTag = ClanRegistry.getByTag(input);
        if (byTag != null) return byTag;

        return ClanRegistry.getByName(input);
    }

    private static boolean isValidTag(String tag) {
        return tag != null && tag.matches(TAG_REGEX);
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("clan")

                    /* ===================== CREATE ===================== */
                    .then(Commands.literal("create")

                            // Branch A:
                            // /clan create <tag> <name>
                            // plus we ALSO allow:
                            // /clan create <name>  by executing on the <tag> node when <name> is missing.
                            .then(Commands.argument("tag", StringArgumentType.word())

                                    // /clan create <name>   (auto-tag)  <-- important to avoid brigadier ambiguity
                                    .executes(context -> {
                                        String name = StringArgumentType.getString(context, "tag");
                                        UUID playerId = context.getSource().getPlayerOrException().getUUID();

                                        if (ClanMembershipRegistry.isInClan(playerId)) {
                                            context.getSource().sendFailure(Component.literal("You're already in a clan. Leave it before creating a new one."));
                                            return 0;
                                        }

                                        UUID clanId = ClanRegistry.createClan(name, playerId);
                                        ClanMembershipRegistry.joinClan(playerId, clanId);
                                        Clan clan = ClanRegistry.getClan(clanId);

                                        try {
                                            ClanDAO.saveClan(clan);
                                            ClanDAO.saveMembers(clanId, clan.getMembers());
                                        } catch (SQLException e) {
                                            // rollback in-memory state
                                            ClanMembershipRegistry.leaveClan(playerId);
                                            ClanRegistry.removeClan(clanId);

                                            e.printStackTrace();
                                            context.getSource().sendFailure(Component.literal("Failed to save clan to database."));
                                            return 0;
                                        }

                                        context.getSource().sendSuccess(
                                                () -> Component.literal("Clan created: " + name + " (tag: " + clan.getTag() + ")"),
                                                false
                                        );
                                        return 1;
                                    })

                                    // /clan create <tag> <name>
                                    .then(Commands.argument("name", StringArgumentType.string())
                                            .executes(context -> {
                                                String tag = StringArgumentType.getString(context, "tag");
                                                String name = StringArgumentType.getString(context, "name");
                                                UUID playerId = context.getSource().getPlayerOrException().getUUID();

                                                if (ClanMembershipRegistry.isInClan(playerId)) {
                                                    context.getSource().sendFailure(Component.literal("You're already in a clan. Leave it before creating a new one."));
                                                    return 0;
                                                }

                                                if (!isValidTag(tag)) {
                                                    context.getSource().sendFailure(Component.literal("Tag must be 1-6 alphanumeric characters."));
                                                    return 0;
                                                }

                                                // Don’t auto-mutate explicit tags (like adding -2); enforce uniqueness here.
                                                if (ClanRegistry.getByTag(tag) != null) {
                                                    context.getSource().sendFailure(Component.literal("That clan tag is already taken."));
                                                    return 0;
                                                }

                                                UUID clanId;
                                                try {
                                                    clanId = ClanRegistry.createClanWithTag(tag, name, playerId);
                                                } catch (IllegalArgumentException ex) {
                                                    context.getSource().sendFailure(Component.literal(ex.getMessage()));
                                                    return 0;
                                                }

                                                ClanMembershipRegistry.joinClan(playerId, clanId);
                                                Clan clan = ClanRegistry.getClan(clanId);

                                                try {
                                                    ClanDAO.saveClan(clan);
                                                    ClanDAO.saveMembers(clanId, clan.getMembers());
                                                } catch (SQLException e) {
                                                    // rollback in-memory state
                                                    ClanMembershipRegistry.leaveClan(playerId);
                                                    ClanRegistry.removeClan(clanId);

                                                    e.printStackTrace();
                                                    context.getSource().sendFailure(Component.literal("Failed to save clan to database."));
                                                    return 0;
                                                }

                                                context.getSource().sendSuccess(
                                                        () -> Component.literal("Clan created: " + name + " (tag: " + clan.getTag() + ")"),
                                                        false
                                                );
                                                return 1;
                                            })))
                    )

                    /* ===================== JOIN ===================== */
                    .then(Commands.literal("join")
                            .then(Commands.argument("name", StringArgumentType.string())
                                    .executes(context -> {
                                        String name = StringArgumentType.getString(context, "name");
                                        UUID playerId = context.getSource().getPlayerOrException().getUUID();

                                        if (ClanMembershipRegistry.isInClan(playerId)) {
                                            context.getSource().sendFailure(Component.literal("You're already in a clan."));
                                            return 0;
                                        }

                                        Clan targetClan = findClanByTagOrName(name);
                                        if (targetClan == null) {
                                            context.getSource().sendFailure(Component.literal("Clan '" + name + "' not found."));
                                            return 0;
                                        }

                                        targetClan.addMember(playerId);
                                        ClanMembershipRegistry.joinClan(playerId, targetClan.getId());

                                        try {
                                            ClanDAO.saveMembers(targetClan.getId(), targetClan.getMembers());
                                        } catch (SQLException e) {
                                            e.printStackTrace();
                                            context.getSource().sendFailure(Component.literal("Failed to save membership to database."));
                                            return 0;
                                        }

                                        context.getSource().sendSuccess(
                                                () -> Component.literal("You have joined the clan: " + targetClan.getName() + " (tag: " + targetClan.getTag() + ")"),
                                                false
                                        );
                                        return 1;
                                    })))

                    /* ===================== LEAVE ===================== */
                    .then(Commands.literal("leave")
                            .executes(context -> {
                                UUID playerId = context.getSource().getPlayerOrException().getUUID();

                                if (!ClanMembershipRegistry.isInClan(playerId)) {
                                    context.getSource().sendFailure(Component.literal("You're not in a clan."));
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
                                        context.getSource().sendFailure(Component.literal("Failed to save membership to database."));
                                        return 0;
                                    }
                                }

                                ClanMembershipRegistry.leaveClan(playerId);
                                ClanRegistry.cleanupEmptyClans();

                                context.getSource().sendSuccess(
                                        () -> Component.literal("You have left your clan."),
                                        false
                                );
                                return 1;
                            })
                    )

                    /* ===================== LIST ===================== */
                    .then(Commands.literal("list")
                            .executes(context -> {
                                var allClans = ClanRegistry.getAllClans();

                                if (allClans.isEmpty()) {
                                    context.getSource().sendSuccess(
                                            () -> Component.literal("No clans have been created yet."),
                                            false
                                    );
                                    return 1;
                                }

                                StringBuilder sb = new StringBuilder("Existing clans:\n");
                                allClans.values().forEach(clan ->
                                        sb.append("- ").append(clan.getTag()).append(" (").append(clan.getName()).append(")\n")
                                );

                                context.getSource().sendSuccess(
                                        () -> Component.literal(sb.toString().trim()),
                                        false
                                );
                                return 1;
                            })
                    )

                    /* ===================== DELETE ===================== */
                    .then(Commands.literal("delete")
                            .then(Commands.argument("name", StringArgumentType.string())
                                    .executes(context -> {
                                        String name = StringArgumentType.getString(context, "name");

                                        Clan clanToDelete = findClanByTagOrName(name);
                                        if (clanToDelete == null) {
                                            context.getSource().sendFailure(Component.literal("Clan '" + name + "' not found."));
                                            return 0;
                                        }

                                        UUID clanIdToDelete = clanToDelete.getId();

                                        // Remove from alliance FIRST
                                        UUID allianceId = clanToDelete.getAllianceId();
                                        if (allianceId != null) {
                                            Alliance alliance = AllianceRegistry.getAlliance(allianceId);
                                            if (alliance != null) {
                                                alliance.removeClan(clanIdToDelete);
                                                try {
                                                    net.hosenka.database.AllianceDAO.saveAlliance(allianceId, alliance);
                                                } catch (SQLException e) {
                                                    e.printStackTrace();
                                                    context.getSource().sendFailure(Component.literal("Failed to update alliance."));
                                                    return 0;
                                                }
                                            }
                                        }

                                        // Remove members from membership registry
                                        for (UUID memberId : clanToDelete.getMembers()) {
                                            ClanMembershipRegistry.leaveClan(memberId);
                                        }

                                        // Delete from DB
                                        try {
                                            ClanDAO.deleteClan(clanIdToDelete);
                                        } catch (SQLException e) {
                                            e.printStackTrace();
                                            context.getSource().sendFailure(Component.literal("Failed to delete clan from database."));
                                            return 0;
                                        }

                                        // Remove from registry (also clears tag index)
                                        ClanRegistry.removeClan(clanIdToDelete);

                                        context.getSource().sendSuccess(
                                                () -> Component.literal("Clan '" + clanToDelete.getName() + "' (tag: " + clanToDelete.getTag() + ") has been deleted."),
                                                false
                                        );
                                        return 1;
                                    })
                            )
                    )

                    /* ===================== INFO ===================== */
                    .then(Commands.literal("info")
                            .then(Commands.argument("name", StringArgumentType.string())
                                    .executes(context -> {
                                        String name = StringArgumentType.getString(context, "name");

                                        Clan targetClan = findClanByTagOrName(name);
                                        if (targetClan == null) {
                                            context.getSource().sendFailure(Component.literal("Clan '" + name + "' not found."));
                                            return 0;
                                        }

                                        StringBuilder sb = new StringBuilder();
                                        sb.append("§6Clan Info: ").append(targetClan.getName())
                                                .append(" §7(tag: ").append(targetClan.getTag()).append(")\n");

                                        UUID leaderId = targetClan.getLeaderId();
                                        sb.append("§7Leader: ");
                                        if (leaderId != null) {
                                            GameProfile profile = context.getSource().getServer()
                                                    .getProfileCache().get(leaderId).orElse(null);
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

                                        context.getSource().sendSuccess(
                                                () -> Component.literal(sb.toString()),
                                                false
                                        );
                                        return 1;
                                    })
                            )
                    )

                    /* ===================== DISBAND ===================== */
                    .then(Commands.literal("disband")
                            .executes(context -> {
                                UUID playerId = context.getSource().getPlayerOrException().getUUID();
                                UUID clanId = ClanMembershipRegistry.getClan(playerId);

                                if (clanId == null) {
                                    context.getSource().sendFailure(Component.literal("You are not in a clan."));
                                    return 0;
                                }

                                Clan clan = ClanRegistry.getClan(clanId);

                                if (clan == null || !clan.isLeader(playerId)) {
                                    context.getSource().sendFailure(Component.literal("Only the clan leader can disband the clan."));
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
                                            context.getSource().sendFailure(Component.literal("Failed to update alliance."));
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
                                    context.getSource().sendFailure(Component.literal("Failed to delete clan from database."));
                                    return 0;
                                }

                                // Remove from memory
                                ClanRegistry.removeClan(clanId);

                                context.getSource().sendSuccess(() ->
                                        Component.literal("Your clan '" + clan.getName() + "' (tag: " + clan.getTag() + ") has been disbanded."), false);

                                return 1;
                            })
                    )

                    /* ===================== INVITE ===================== */
                    .then(Commands.literal("invite")
                            .then(Commands.argument("player", StringArgumentType.word())
                                    .executes(context -> {
                                        UUID inviterId = context.getSource().getPlayerOrException().getUUID();
                                        UUID clanId = ClanMembershipRegistry.getClan(inviterId);

                                        if (clanId == null) {
                                            context.getSource().sendFailure(Component.literal("You are not in a clan."));
                                            return 0;
                                        }

                                        Clan clan = ClanRegistry.getClan(clanId);
                                        if (clan == null || !clan.isLeader(inviterId)) {
                                            context.getSource().sendFailure(Component.literal("Only the clan leader can invite players."));
                                            return 0;
                                        }

                                        String targetName = StringArgumentType.getString(context, "player");
                                        var targetProfile = context.getSource().getServer().getProfileCache()
                                                .get(targetName).orElse(null);

                                        if (targetProfile == null) {
                                            context.getSource().sendFailure(Component.literal("Player not found: " + targetName));
                                            return 0;
                                        }

                                        UUID targetId = targetProfile.getId();

                                        if (ClanMembershipRegistry.isInClan(targetId)) {
                                            context.getSource().sendFailure(Component.literal("That player is already in a clan."));
                                            return 0;
                                        }

                                        ClanRegistry.invitePlayer(targetId, clanId);
                                        context.getSource().sendSuccess(() ->
                                                Component.literal("Invited " + targetName + " to the clan."), false);

                                        return 1;
                                    })))


                    /* ===================== ACCEPT ===================== */
                    .then(Commands.literal("accept")
                            .executes(context -> {
                                UUID playerId = context.getSource().getPlayerOrException().getUUID();

                                if (ClanMembershipRegistry.isInClan(playerId)) {
                                    context.getSource().sendFailure(Component.literal("You're already in a clan."));
                                    return 0;
                                }

                                UUID invitedClanId = ClanRegistry.getPendingInvite(playerId);
                                if (invitedClanId == null) {
                                    context.getSource().sendFailure(Component.literal("You don't have any pending clan invites."));
                                    return 0;
                                }

                                Clan clan = ClanRegistry.getClan(invitedClanId);
                                if (clan == null) {
                                    context.getSource().sendFailure(Component.literal("The clan you were invited to no longer exists."));
                                    return 0;
                                }

                                clan.addMember(playerId);
                                ClanMembershipRegistry.joinClan(playerId, invitedClanId);
                                ClanRegistry.clearInvite(playerId);

                                try {
                                    ClanDAO.saveMembers(invitedClanId, clan.getMembers());
                                } catch (SQLException e) {
                                    e.printStackTrace();
                                    context.getSource().sendFailure(Component.literal("Failed to save membership to database."));
                                    return 0;
                                }

                                context.getSource().sendSuccess(() ->
                                        Component.literal("You have joined the clan: " + clan.getName() + " (tag: " + clan.getTag() + ")"), false);

                                return 1;
                            }))

                    /* ===================== STATUS ===================== */
                    .then(Commands.literal("status")
                            .executes(context -> {
                                UUID playerId = context.getSource().getPlayerOrException().getUUID();

                                UUID clanId = ClanMembershipRegistry.getClan(playerId);
                                Clan clan = clanId != null ? ClanRegistry.getClan(clanId) : null;

                                String clanName = clan != null ? clan.getName() + " (tag: " + clan.getTag() + ")" : "None";

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

                                context.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
                                return 1;
                            }))

                    /* ===================== RENAME ===================== */
                    .then(Commands.literal("rename")
                            .then(Commands.argument("old", StringArgumentType.string())
                                    .then(Commands.argument("new", StringArgumentType.string())
                                            .executes(context -> {
                                                String oldKey = StringArgumentType.getString(context, "old");
                                                String newName = StringArgumentType.getString(context, "new");
                                                UUID playerId = context.getSource().getPlayerOrException().getUUID();

                                                Clan targetClan = findClanByTagOrName(oldKey);
                                                if (targetClan == null) {
                                                    context.getSource().sendFailure(Component.literal("Clan '" + oldKey + "' not found."));
                                                    return 0;
                                                }

                                                if (!targetClan.isLeader(playerId) && !context.getSource().hasPermission(2)) {
                                                    context.getSource().sendFailure(Component.literal("Only the clan leader or an admin can rename the clan."));
                                                    return 0;
                                                }

                                                // Prevent duplicate DISPLAY names (tag is the stable identifier)
                                                for (Clan c : ClanRegistry.getAllClans().values()) {
                                                    if (c.getName().equalsIgnoreCase(newName)) {
                                                        context.getSource().sendFailure(Component.literal("Another clan with that name already exists."));
                                                        return 0;
                                                    }
                                                }

                                                String oldName = targetClan.getName();
                                                targetClan.setName(newName);

                                                try {
                                                    ClanDAO.saveClan(targetClan);
                                                } catch (SQLException e) {
                                                    e.printStackTrace();
                                                    context.getSource().sendFailure(Component.literal("Failed to save clan rename to database."));
                                                    return 0;
                                                }

                                                context.getSource().sendSuccess(
                                                        () -> Component.literal("Clan '" + oldName + "' has been renamed to '" + newName + "'. (tag: " + targetClan.getTag() + ")"),
                                                        false
                                                );
                                                return 1;
                                            }))))
            );
        });
    }
}
