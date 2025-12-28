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
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.UUID;

public class ClanCommand {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("clan")
                    .then(CommandManager.literal("create")
                            .then(CommandManager.argument("name", StringArgumentType.string())
                                    .executes(context -> {
                                        String name = StringArgumentType.getString(context, "name");

                                        UUID playerId = context.getSource().getPlayer().getUuid();
                                        UUID clanId = ClanRegistry.createClan(name, playerId);
                                        ClanMembershipRegistry.joinClan(playerId, clanId);


                                        context.getSource().sendFeedback(
                                                () -> Text.literal("Clan created: " + name),
                                                false
                                        );
                                        return 1;
                                    })))

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
                                        for (Map.Entry<UUID, Clan> entry : ClanRegistry.getAllClans().entrySet()) {
                                            if (entry.getValue().getName().equalsIgnoreCase(name)) {
                                                targetClan = entry.getValue();
                                                break;
                                            }
                                        }

                                        if (targetClan == null) {
                                            context.getSource().sendError(Text.literal("Clan '" + name + "' not found."));
                                            return 0;
                                        }

                                        targetClan.addMember(playerId);
                                        ClanMembershipRegistry.joinClan(playerId, targetClan.getId());

                                        context.getSource().sendFeedback(
                                                () -> Text.literal("You have joined the clan: " + name),
                                                false
                                        );
                                        return 1;
                                    })))

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
                                }

                                ClanMembershipRegistry.leaveClan(playerId);

                                context.getSource().sendFeedback(
                                        () -> Text.literal("You have left your clan."),
                                        false
                                );

                                return 1;
                            })
                    )

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

                                allClans.values().forEach(clan -> {
                                    sb.append("- ").append(clan.getName()).append("\n");
                                });

                                context.getSource().sendFeedback(
                                        () -> Text.literal(sb.toString().trim()),
                                        false
                                );

                                return 1;
                            })
                    )

                    .then(CommandManager.literal("delete")
                            .then(CommandManager.argument("name", StringArgumentType.string())
                                    .executes(context -> {
                                        String name = StringArgumentType.getString(context, "name");
                                        UUID clanIdToDelete = null;

                                        for (Map.Entry<UUID, Clan> entry : ClanRegistry.getAllClans().entrySet()) {
                                            if (entry.getValue().getName().equalsIgnoreCase(name)) {
                                                clanIdToDelete = entry.getKey();
                                                break;
                                            }
                                        }

                                        if (clanIdToDelete == null) {
                                            context.getSource().sendError(Text.literal("Clan '" + name + "' not found."));
                                            return 0;
                                        }

                                        Clan deletedClan = ClanRegistry.getClan(clanIdToDelete);
                                        if (deletedClan != null) {
                                            for (UUID memberId : deletedClan.getMembers()) {
                                                ClanMembershipRegistry.leaveClan(memberId);
                                            }
                                        }

                                        ClanRegistry.getAllClans().remove(clanIdToDelete);

                                        context.getSource().sendFeedback(
                                                () -> Text.literal("Clan '" + name + "' has been deleted."),
                                                false
                                        );

                                        return 1;
                                    })
                            )
                    )

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
                                        sb.append("§7Leader: ");

                                        UUID leaderId = targetClan.getLeaderId();
                                        if (leaderId != null) {
                                            GameProfile profile = context.getSource().getServer().getUserCache().getByUuid(leaderId).orElse(null);
                                            String leaderName = profile != null ? profile.getName() : leaderId.toString();

                                            sb.append(leaderName != null ? leaderName : leaderId.toString());
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


                                        context.getSource().sendFeedback(() -> Text.literal(sb.toString().trim()), false);
                                        return 1;
                                    })
                            )
                    )
            );
        });
    }
}
