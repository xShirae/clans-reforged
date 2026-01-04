package net.hosenka.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.hosenka.alliance.Alliance;
import net.hosenka.alliance.AllianceRegistry;
import net.hosenka.clan.Clan;
import net.hosenka.clan.ClanMembershipRegistry;
import net.hosenka.clan.ClanRegistry;
import net.hosenka.database.AllianceDAO;
import net.hosenka.integration.griefdefender.GDIntegration;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

public class AllianceCommand {

    /**
     * Invalidates GD clan provider caches for clans whose ally/rival list may have changed.
     * Safe to call even if GriefDefender isn't installed/initialized.
     */
    private static void gdInvalidateAllies(UUID... clanIds) {
        if (clanIds == null) return;
        for (UUID id : clanIds) {
            if (id != null) {
                GDIntegration.invalidateClan(id);
            }
        }
    }

    private static Map.Entry<UUID, Alliance> findAllianceByName(String name) {
        for (Map.Entry<UUID, Alliance> entry : AllianceRegistry.getAllAlliances().entrySet()) {
            if (entry.getValue().getName().equalsIgnoreCase(name)) {
                return entry;
            }
        }
        return null;
    }

    private static boolean hasAllianceLeaderPermission(net.minecraft.commands.CommandSourceStack source,
                                                       UUID playerId,
                                                       UUID clanId,
                                                       Clan clan,
                                                       Alliance alliance) {
        if (source.hasPermission(2)) return true; // OP override
        UUID leaderClanId = alliance.getLeaderClanId();
        return leaderClanId != null && leaderClanId.equals(clanId) && clan.isLeader(playerId);
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            var root = Commands.literal("alliance");

            // /alliance create <name>
            var create = Commands.literal("create");
            create.then(Commands.argument("name", StringArgumentType.string())
                    .executes(context -> {
                        if (!context.getSource().hasPermission(2)) {
                            context.getSource().sendFailure(Component.literal("You do not have permission to create alliances."));
                            return 0;
                        }

                        String name = StringArgumentType.getString(context, "name");
                        UUID id = AllianceRegistry.createAlliance(name);
                        Alliance alliance = AllianceRegistry.getAlliance(id);

                        try {
                            AllianceDAO.saveAlliance(id, alliance);
                        } catch (SQLException e) {
                            e.printStackTrace();
                            context.getSource().sendFailure(Component.literal("Failed to save alliance to database."));
                            return 0;
                        }

                        context.getSource().sendSuccess(() -> Component.literal("Alliance created: " + name), false);
                        return 1;
                    }));
            root.then(create);

            // /alliance delete <name>
            var delete = Commands.literal("delete");
            delete.then(Commands.argument("name", StringArgumentType.string())
                    .executes(context -> {
                        if (!context.getSource().hasPermission(2)) {
                            context.getSource().sendFailure(Component.literal("You do not have permission to delete alliances."));
                            return 0;
                        }

                        String name = StringArgumentType.getString(context, "name");
                        Map.Entry<UUID, Alliance> entry = findAllianceByName(name);
                        if (entry == null) {
                            context.getSource().sendFailure(Component.literal("Alliance '" + name + "' not found."));
                            return 0;
                        }

                        UUID toDelete = entry.getKey();
                        Alliance deleted = entry.getValue();

                        for (UUID clanId : deleted.getClans()) {
                            Clan clan = ClanRegistry.getClan(clanId);
                            if (clan != null) {
                                clan.setAllianceId(null);
                            }
                        }

                        gdInvalidateAllies(deleted.getClans().toArray(new UUID[0]));

                        try {
                            AllianceDAO.deleteAlliance(toDelete);
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }

                        AllianceRegistry.deleteAlliance(toDelete);

                        context.getSource().sendSuccess(() -> Component.literal("Alliance '" + name + "' has been deleted."), false);
                        return 1;
                    }));
            root.then(delete);

            // /alliance join <name>
            var join = Commands.literal("join");
            join.then(Commands.argument("name", StringArgumentType.string())
                    .executes(context -> {
                        UUID playerId = context.getSource().getPlayerOrException().getUUID();
                        if (!ClanMembershipRegistry.isInClan(playerId)) {
                            context.getSource().sendFailure(Component.literal("You must be in a clan to join an alliance."));
                            return 0;
                        }

                        UUID clanId = ClanMembershipRegistry.getClan(playerId);
                        Clan clan = ClanRegistry.getClan(clanId);
                        if (clan == null) {
                            context.getSource().sendFailure(Component.literal("Clan not found."));
                            return 0;
                        }

                        if (clan.getAllianceId() != null) {
                            context.getSource().sendFailure(Component.literal("Your clan is already in an alliance."));
                            return 0;
                        }

                        String name = StringArgumentType.getString(context, "name");
                        Map.Entry<UUID, Alliance> entry = findAllianceByName(name);
                        if (entry == null) {
                            context.getSource().sendFailure(Component.literal("Alliance '" + name + "' not found."));
                            return 0;
                        }

                        UUID allianceId = entry.getKey();
                        Alliance target = entry.getValue();

                        target.addClan(clanId);
                        clan.setAllianceId(allianceId);

                        // If the alliance doesn't have a leader clan yet, assign the first joining clan.
                        if (target.getLeaderClanId() == null) {
                            target.setLeaderClanId(clanId);
                        }

                        gdInvalidateAllies(target.getClans().toArray(new UUID[0]));

                        try {
                            AllianceDAO.saveAlliance(allianceId, target);
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }

                        context.getSource().sendSuccess(() -> Component.literal("Your clan has joined the alliance: " + name), false);
                        return 1;
                    }));
            root.then(join);

            // /alliance leave
            var leave = Commands.literal("leave");
            leave.executes(context -> {
                UUID playerId = context.getSource().getPlayerOrException().getUUID();
                if (!ClanMembershipRegistry.isInClan(playerId)) {
                    context.getSource().sendFailure(Component.literal("You must be in a clan to leave an alliance."));
                    return 0;
                }

                UUID clanId = ClanMembershipRegistry.getClan(playerId);
                Clan clan = ClanRegistry.getClan(clanId);

                if (clan == null || clan.getAllianceId() == null) {
                    context.getSource().sendFailure(Component.literal("Your clan is not in an alliance."));
                    return 0;
                }

                UUID allianceId = clan.getAllianceId();
                Alliance alliance = AllianceRegistry.getAlliance(allianceId);

                if (alliance != null) {
                    alliance.removeClan(clanId);

                    // If the leaving clan was the leader clan, rotate leadership to any remaining clan.
                    if (alliance.getLeaderClanId() == null) {
                        UUID nextLeader = alliance.getClans().stream().findFirst().orElse(null);
                        alliance.setLeaderClanId(nextLeader);
                    }

                    try {
                        AllianceDAO.saveAlliance(allianceId, alliance);
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }

                clan.setAllianceId(null);

                if (alliance != null) gdInvalidateAllies(alliance.getClans().toArray(new UUID[0]));
                gdInvalidateAllies(clanId);

                context.getSource().sendSuccess(() -> Component.literal("Your clan has left its alliance."), false);
                return 1;
            });
            root.then(leave);

            /* ===================== WAR ===================== */

            // /alliance war <name>
            var war = Commands.literal("war");
            war.then(Commands.argument("name", StringArgumentType.string())
                    .executes(context -> {
                        var player = context.getSource().getPlayerOrException();
                        UUID playerId = player.getUUID();

                        if (!ClanMembershipRegistry.isInClan(playerId)) {
                            context.getSource().sendFailure(Component.literal("You must be in a clan to declare war."));
                            return 0;
                        }

                        UUID clanId = ClanMembershipRegistry.getClan(playerId);
                        Clan clan = ClanRegistry.getClan(clanId);
                        if (clan == null || clan.getAllianceId() == null) {
                            context.getSource().sendFailure(Component.literal("Your clan is not in an alliance."));
                            return 0;
                        }

                        UUID myAllianceId = clan.getAllianceId();
                        Alliance myAlliance = AllianceRegistry.getAlliance(myAllianceId);
                        if (myAlliance == null) {
                            context.getSource().sendFailure(Component.literal("Alliance not found."));
                            return 0;
                        }

                        if (!hasAllianceLeaderPermission(context.getSource(), playerId, clanId, clan, myAlliance)) {
                            context.getSource().sendFailure(Component.literal("Only the alliance leader can declare war."));
                            return 0;
                        }

                        String name = StringArgumentType.getString(context, "name");
                        Map.Entry<UUID, Alliance> targetEntry = findAllianceByName(name);
                        if (targetEntry == null) {
                            context.getSource().sendFailure(Component.literal("Alliance '" + name + "' not found."));
                            return 0;
                        }

                        UUID targetAllianceId = targetEntry.getKey();
                        Alliance targetAlliance = targetEntry.getValue();

                        if (targetAllianceId.equals(myAllianceId)) {
                            context.getSource().sendFailure(Component.literal("You cannot declare war on your own alliance."));
                            return 0;
                        }

                        if (AllianceRegistry.areAlliancesAtWar(myAllianceId, targetAllianceId)) {
                            context.getSource().sendFailure(Component.literal("Your alliance is already at war with '" + targetAlliance.getName() + "'."));
                            return 0;
                        }

                        try {
                            AllianceRegistry.declareWar(myAllianceId, targetAllianceId);
                        } catch (SQLException e) {
                            e.printStackTrace();
                            context.getSource().sendFailure(Component.literal("Failed to declare war due to a database error."));
                            return 0;
                        }

                        gdInvalidateAllies(myAlliance.getClans().toArray(new UUID[0]));
                        gdInvalidateAllies(targetAlliance.getClans().toArray(new UUID[0]));

                        context.getSource().sendSuccess(() -> Component.literal(
                                "War declared between '" + myAlliance.getName() + "' and '" + targetAlliance.getName() + "'."
                        ), false);

                        return 1;
                    }));
            root.then(war);

            /* ===================== PEACE REQUESTS ===================== */

            // /alliance peace <name>  (send request)
            // /alliance peace accept <name>
            // /alliance peace deny <name>
            var peace = Commands.literal("peace");

            // send peace request
            peace.then(Commands.argument("name", StringArgumentType.string())
                    .executes(context -> {
                        var player = context.getSource().getPlayerOrException();
                        UUID playerId = player.getUUID();

                        if (!ClanMembershipRegistry.isInClan(playerId)) {
                            context.getSource().sendFailure(Component.literal("You must be in a clan to request peace."));
                            return 0;
                        }

                        UUID clanId = ClanMembershipRegistry.getClan(playerId);
                        Clan clan = ClanRegistry.getClan(clanId);
                        if (clan == null || clan.getAllianceId() == null) {
                            context.getSource().sendFailure(Component.literal("Your clan is not in an alliance."));
                            return 0;
                        }

                        UUID myAllianceId = clan.getAllianceId();
                        Alliance myAlliance = AllianceRegistry.getAlliance(myAllianceId);
                        if (myAlliance == null) {
                            context.getSource().sendFailure(Component.literal("Alliance not found."));
                            return 0;
                        }

                        if (!hasAllianceLeaderPermission(context.getSource(), playerId, clanId, clan, myAlliance)) {
                            context.getSource().sendFailure(Component.literal("Only the alliance leader can request peace."));
                            return 0;
                        }

                        String name = StringArgumentType.getString(context, "name");
                        Map.Entry<UUID, Alliance> targetEntry = findAllianceByName(name);
                        if (targetEntry == null) {
                            context.getSource().sendFailure(Component.literal("Alliance '" + name + "' not found."));
                            return 0;
                        }

                        UUID targetAllianceId = targetEntry.getKey();
                        Alliance targetAlliance = targetEntry.getValue();

                        if (targetAllianceId.equals(myAllianceId)) {
                            context.getSource().sendFailure(Component.literal("You cannot request peace with your own alliance."));
                            return 0;
                        }

                        if (!AllianceRegistry.areAlliancesAtWar(myAllianceId, targetAllianceId)) {
                            context.getSource().sendFailure(Component.literal("Your alliance is not at war with '" + targetAlliance.getName() + "'."));
                            return 0;
                        }

                        boolean created = AllianceRegistry.requestPeace(myAllianceId, targetAllianceId, playerId);
                        if (!created) {
                            context.getSource().sendFailure(Component.literal("A peace request is already pending (or could not be created)."));
                            return 0;
                        }

                        // Notify target alliance leader if online
                        UUID leaderClan = targetAlliance.getLeaderClanId();
                        if (leaderClan != null) {
                            Clan leaderClanObj = ClanRegistry.getClan(leaderClan);
                            if (leaderClanObj != null) {
                                UUID leaderPlayer = leaderClanObj.getLeaderId();
                                if (leaderPlayer != null) {
                                    var onlineLeader = context.getSource().getServer().getPlayerList().getPlayer(leaderPlayer);
                                    if (onlineLeader != null) {
                                        onlineLeader.sendSystemMessage(Component.literal(
                                                "Peace request received from alliance '" + myAlliance.getName() + "'. " +
                                                        "Use /alliance peace accept " + myAlliance.getName() +
                                                        " or /alliance peace deny " + myAlliance.getName() + "."
                                        ));
                                    }
                                }
                            }
                        }

                        context.getSource().sendSuccess(() -> Component.literal(
                                "Peace request sent to '" + targetAlliance.getName() + "'."
                        ), false);

                        return 1;
                    }));

            // accept peace request
            var accept = Commands.literal("accept");
            accept.then(Commands.argument("name", StringArgumentType.string())
                    .executes(context -> {
                        var player = context.getSource().getPlayerOrException();
                        UUID playerId = player.getUUID();

                        if (!ClanMembershipRegistry.isInClan(playerId)) {
                            context.getSource().sendFailure(Component.literal("You must be in a clan to accept peace."));
                            return 0;
                        }

                        UUID clanId = ClanMembershipRegistry.getClan(playerId);
                        Clan clan = ClanRegistry.getClan(clanId);
                        if (clan == null || clan.getAllianceId() == null) {
                            context.getSource().sendFailure(Component.literal("Your clan is not in an alliance."));
                            return 0;
                        }

                        UUID myAllianceId = clan.getAllianceId();
                        Alliance myAlliance = AllianceRegistry.getAlliance(myAllianceId);
                        if (myAlliance == null) {
                            context.getSource().sendFailure(Component.literal("Alliance not found."));
                            return 0;
                        }

                        if (!hasAllianceLeaderPermission(context.getSource(), playerId, clanId, clan, myAlliance)) {
                            context.getSource().sendFailure(Component.literal("Only the alliance leader can accept peace."));
                            return 0;
                        }

                        String name = StringArgumentType.getString(context, "name");
                        Map.Entry<UUID, Alliance> otherEntry = findAllianceByName(name);
                        if (otherEntry == null) {
                            context.getSource().sendFailure(Component.literal("Alliance '" + name + "' not found."));
                            return 0;
                        }

                        UUID otherAllianceId = otherEntry.getKey();
                        Alliance otherAlliance = otherEntry.getValue();

                        try {
                            var req = AllianceRegistry.getPeaceRequest(otherAllianceId, myAllianceId);
                            boolean acceptedOk = AllianceRegistry.acceptPeace(myAllianceId, otherAllianceId);
                            if (!acceptedOk || req == null) {
                                context.getSource().sendFailure(Component.literal("No pending peace request from '" + otherAlliance.getName() + "'."));
                                return 0;
                            }

                            if (req.requester() != null) {
                                var requesterPlayer = context.getSource().getServer().getPlayerList().getPlayer(req.requester());
                                if (requesterPlayer != null) {
                                    requesterPlayer.sendSystemMessage(Component.literal(
                                            "Your peace request to '" + myAlliance.getName() + "' was accepted."
                                    ));
                                }
                            }
                        } catch (SQLException e) {
                            e.printStackTrace();
                            context.getSource().sendFailure(Component.literal("Failed to accept peace due to a database error."));
                            return 0;
                        }

                        gdInvalidateAllies(myAlliance.getClans().toArray(new UUID[0]));
                        gdInvalidateAllies(otherAlliance.getClans().toArray(new UUID[0]));

                        context.getSource().sendSuccess(() -> Component.literal(
                                "Peace accepted between '" + myAlliance.getName() + "' and '" + otherAlliance.getName() + "'."
                        ), false);

                        return 1;
                    }));
            peace.then(accept);

            // deny peace request
            var deny = Commands.literal("deny");
            deny.then(Commands.argument("name", StringArgumentType.string())
                    .executes(context -> {
                        var player = context.getSource().getPlayerOrException();
                        UUID playerId = player.getUUID();

                        if (!ClanMembershipRegistry.isInClan(playerId)) {
                            context.getSource().sendFailure(Component.literal("You must be in a clan to deny peace."));
                            return 0;
                        }

                        UUID clanId = ClanMembershipRegistry.getClan(playerId);
                        Clan clan = ClanRegistry.getClan(clanId);
                        if (clan == null || clan.getAllianceId() == null) {
                            context.getSource().sendFailure(Component.literal("Your clan is not in an alliance."));
                            return 0;
                        }

                        UUID myAllianceId = clan.getAllianceId();
                        Alliance myAlliance = AllianceRegistry.getAlliance(myAllianceId);
                        if (myAlliance == null) {
                            context.getSource().sendFailure(Component.literal("Alliance not found."));
                            return 0;
                        }

                        if (!hasAllianceLeaderPermission(context.getSource(), playerId, clanId, clan, myAlliance)) {
                            context.getSource().sendFailure(Component.literal("Only the alliance leader can deny peace."));
                            return 0;
                        }

                        String name = StringArgumentType.getString(context, "name");
                        Map.Entry<UUID, Alliance> otherEntry = findAllianceByName(name);
                        if (otherEntry == null) {
                            context.getSource().sendFailure(Component.literal("Alliance '" + name + "' not found."));
                            return 0;
                        }

                        UUID otherAllianceId = otherEntry.getKey();
                        Alliance otherAlliance = otherEntry.getValue();

                        var req = AllianceRegistry.getPeaceRequest(otherAllianceId, myAllianceId);
                        boolean deniedOk = AllianceRegistry.denyPeace(myAllianceId, otherAllianceId);
                        if (!deniedOk || req == null) {
                            context.getSource().sendFailure(Component.literal("No pending peace request from '" + otherAlliance.getName() + "'."));
                            return 0;
                        }

                        if (req.requester() != null) {
                            var requesterPlayer = context.getSource().getServer().getPlayerList().getPlayer(req.requester());
                            if (requesterPlayer != null) {
                                requesterPlayer.sendSystemMessage(Component.literal(
                                        "Your peace request to '" + myAlliance.getName() + "' was denied."
                                ));
                            }
                        }

                        context.getSource().sendSuccess(() -> Component.literal(
                                "Peace request denied (from '" + otherAlliance.getName() + "')."
                        ), false);

                        return 1;
                    }));
            peace.then(deny);

            root.then(peace);

            // /alliance info <name>
            var info = Commands.literal("info");
            info.then(Commands.argument("name", StringArgumentType.string())
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
                            context.getSource().sendFailure(Component.literal("Alliance '" + name + "' not found."));
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

                        context.getSource().sendSuccess(() -> Component.literal(sb.toString().trim()), false);
                        return 1;
                    }));
            root.then(info);

            // /alliance list
            var list = Commands.literal("list");
            list.executes(context -> {
                var allAlliances = AllianceRegistry.getAllAlliances();

                if (allAlliances.isEmpty()) {
                    context.getSource().sendSuccess(() -> Component.literal("No alliances have been created yet."), false);
                    return 1;
                }

                StringBuilder sb = new StringBuilder("Existing alliances:\n");
                for (Alliance alliance : allAlliances.values()) {
                    sb.append("- ").append(alliance.getName())
                            .append(" (").append(alliance.getClans().size()).append(" clans)\n");
                }

                context.getSource().sendSuccess(() -> Component.literal(sb.toString().trim()), false);
                return 1;
            });
            root.then(list);

            // /alliance rename <old> <new>
            var rename = Commands.literal("rename");
            var oldArg = Commands.argument("old", StringArgumentType.string());
            var newArg = Commands.argument("new", StringArgumentType.string());

            newArg.executes(context -> {
                if (!context.getSource().hasPermission(2)) {
                    context.getSource().sendFailure(Component.literal("You do not have permission to rename alliances."));
                    return 0;
                }

                String oldName = StringArgumentType.getString(context, "old");
                String newName = StringArgumentType.getString(context, "new");

                Map.Entry<UUID, Alliance> entry = findAllianceByName(oldName);
                if (entry == null) {
                    context.getSource().sendFailure(Component.literal("Alliance '" + oldName + "' not found."));
                    return 0;
                }

                // Check name collision
                for (Alliance alliance : AllianceRegistry.getAllAlliances().values()) {
                    if (alliance.getName().equalsIgnoreCase(newName)) {
                        context.getSource().sendFailure(Component.literal("Another alliance with that name already exists."));
                        return 0;
                    }
                }

                UUID idToRename = entry.getKey();
                Alliance target = entry.getValue();
                target.setName(newName);

                try {
                    AllianceDAO.saveAlliance(idToRename, target);
                } catch (SQLException e) {
                    e.printStackTrace();
                }

                context.getSource().sendSuccess(() ->
                        Component.literal("Alliance '" + oldName + "' has been renamed to '" + newName + "'."), false);
                return 1;
            });

            oldArg.then(newArg);
            rename.then(oldArg);
            root.then(rename);

            dispatcher.register(root);
        });
    }
}
