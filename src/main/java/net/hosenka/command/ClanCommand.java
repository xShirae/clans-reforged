package net.hosenka.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.hosenka.alliance.Alliance;
import net.hosenka.alliance.AllianceRegistry;
import net.hosenka.api.ClansReforgedEvents;
import net.hosenka.clan.Clan;
import net.hosenka.clan.ClanMembershipRegistry;
import net.hosenka.clan.ClanRank;
import net.hosenka.clan.ClanRegistry;
import net.hosenka.database.ClanDAO;
import net.hosenka.integration.griefdefender.GDIntegration;
import net.minecraft.commands.Commands;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionResult;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
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

    private static boolean canManageHome(UUID playerId, Clan clan) {
        if (clan.isLeader(playerId)) return true;
        ClanRank rank = ClanMembershipRegistry.getRank(playerId);
        return rank == ClanRank.RIGHT_ARM || rank == ClanRank.LEADER;
    }

    private static ServerLevel resolveLevel(net.minecraft.server.MinecraftServer server, String dim) {
        ResourceLocation rl = ResourceLocation.tryParse(dim);
        if (rl == null) return null;
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, rl);
        return server.getLevel(key);
    }

    private static Map<UUID, ClanRank> buildMemberRanks(net.hosenka.clan.Clan clan) {
        Map<UUID, ClanRank> out = new HashMap<>();
        UUID leader = clan.getLeaderId();

        for (UUID member : clan.getMembers()) {
            ClanRank r;

            // Leader is authoritative from clan.leaderId
            if (leader != null && leader.equals(member)) {
                r = ClanRank.LEADER;
            } else {
                ClanRank stored = ClanMembershipRegistry.getRank(member);
                r = (stored == null) ? ClanRank.MEMBER : stored;

                // Don’t store LEADER in member ranks unless it matches leaderId
                if (r == ClanRank.LEADER) r = ClanRank.MEMBER;
            }

            out.put(member, r);
        }

        return out;
    }

    // GD cache invalidation helper (2)
    private static void gdInvalidate(UUID clanId, UUID... players) {
        var cp = GDIntegration.getClanProvider();
        if (cp == null) return;

        if (clanId != null) cp.invalidateClan(clanId);

        if (players != null) {
            for (UUID p : players) {
                if (p != null) cp.invalidatePlayer(p);
            }
        }
    }

    private static boolean isValidTag(String tag) {
        return tag != null && tag.matches(TAG_REGEX);
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("clan")

                    /* ===================== CREATE ===================== */
                    .then(Commands.literal("create")

                            .then(Commands.argument("tag", StringArgumentType.word())

                                    // /clan create <name>   (auto-tag)
                                    .executes(context -> {
                                        String name = StringArgumentType.getString(context, "tag");
                                        var player = context.getSource().getPlayerOrException();
                                        UUID playerId = player.getUUID();

                                        // Track last-known name
                                        ClanMembershipRegistry.updateLastKnownName(playerId, player.getName().getString());

                                        if (ClanMembershipRegistry.isInClan(playerId)) {
                                            context.getSource().sendFailure(Component.literal("You're already in a clan. Leave it before creating a new one."));
                                            return 0;
                                        }

                                        // EVENTS: PreCreateClan (cancellable)
                                        InteractionResult pre = ClansReforgedEvents.PRE_CREATE_CLAN.invoker()
                                                .onPreCreate(player, name, name);
                                        if (pre == InteractionResult.FAIL) {
                                            return 0;
                                        }

                                        UUID clanId = ClanRegistry.createClan(name, playerId);
                                        Clan clan = ClanRegistry.getClan(clanId);

                                        try {
                                            ClanDAO.saveClan(clan);
                                            ClanDAO.saveMembers(clanId, buildMemberRanks(clan));
                                        } catch (SQLException e) {
                                            // rollback in-memory state
                                            ClanMembershipRegistry.leaveClan(playerId);
                                            ClanRegistry.removeClan(clanId);

                                            e.printStackTrace();
                                            context.getSource().sendFailure(Component.literal("Failed to save clan to database."));
                                            return 0;
                                        }

                                        // EVENTS: CreateClan (post)
                                        ClansReforgedEvents.CREATE_CLAN.invoker().onCreate(player, clanId);

                                        // invalidate caches
                                        gdInvalidate(clanId, playerId);

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
                                                var player = context.getSource().getPlayerOrException();
                                                UUID playerId = player.getUUID();

                                                // Track last-known name
                                                ClanMembershipRegistry.updateLastKnownName(playerId, player.getName().getString());

                                                if (ClanMembershipRegistry.isInClan(playerId)) {
                                                    context.getSource().sendFailure(Component.literal("You're already in a clan. Leave it before creating a new one."));
                                                    return 0;
                                                }

                                                if (!isValidTag(tag)) {
                                                    context.getSource().sendFailure(Component.literal("Tag must be 1-6 alphanumeric characters."));
                                                    return 0;
                                                }

                                                if (ClanRegistry.getByTag(tag) != null) {
                                                    context.getSource().sendFailure(Component.literal("That clan tag is already taken."));
                                                    return 0;
                                                }

                                                // EVENTS: PreCreateClan (cancellable)
                                                InteractionResult pre = ClansReforgedEvents.PRE_CREATE_CLAN.invoker()
                                                        .onPreCreate(player, tag, name);
                                                if (pre == InteractionResult.FAIL) {
                                                    return 0;
                                                }

                                                UUID clanId;
                                                try {
                                                    clanId = ClanRegistry.createClanWithTag(tag, name, playerId);
                                                } catch (IllegalArgumentException ex) {
                                                    context.getSource().sendFailure(Component.literal(ex.getMessage()));
                                                    return 0;
                                                }

                                                Clan clan = ClanRegistry.getClan(clanId);

                                                try {
                                                    ClanDAO.saveClan(clan);
                                                    ClanDAO.saveMembers(clanId, buildMemberRanks(clan));
                                                } catch (SQLException e) {
                                                    // rollback in-memory state
                                                    ClanMembershipRegistry.leaveClan(playerId);
                                                    ClanRegistry.removeClan(clanId);

                                                    e.printStackTrace();
                                                    context.getSource().sendFailure(Component.literal("Failed to save clan to database."));
                                                    return 0;
                                                }

                                                // EVENTS: CreateClan (post)
                                                ClansReforgedEvents.CREATE_CLAN.invoker().onCreate(player, clanId);

                                                // invalidate caches
                                                gdInvalidate(clanId, playerId);

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
                                        var player = context.getSource().getPlayerOrException();
                                        UUID playerId = player.getUUID();

                                        ClanMembershipRegistry.updateLastKnownName(playerId, player.getName().getString());

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
                                            ClanDAO.saveMembers(targetClan.getId(), buildMemberRanks(targetClan));
                                        } catch (SQLException e) {
                                            e.printStackTrace();
                                            context.getSource().sendFailure(Component.literal("Failed to save membership to database."));
                                            return 0;
                                        }

                                        // EVENTS: PlayerJoinedClan (post)
                                        ClansReforgedEvents.PLAYER_JOINED_CLAN.invoker().onJoin(player, targetClan.getId());

                                        // invalidate caches
                                        gdInvalidate(targetClan.getId(), playerId);

                                        context.getSource().sendSuccess(
                                                () -> Component.literal("You have joined the clan: " + targetClan.getName() + " (tag: " + targetClan.getTag() + ")"),
                                                false
                                        );
                                        return 1;
                                    })))

                    /* ===================== HOME ===================== */
                    .then(Commands.literal("sethome")
                            .executes(ctx -> {
                                var player = ctx.getSource().getPlayerOrException();
                                UUID playerId = player.getUUID();

                                ClanMembershipRegistry.updateLastKnownName(playerId, player.getName().getString());

                                UUID clanId = ClanMembershipRegistry.getClan(playerId);
                                if (clanId == null) {
                                    ctx.getSource().sendFailure(Component.literal("You're not in a clan."));
                                    return 0;
                                }

                                Clan clan = ClanRegistry.getClan(clanId);
                                if (clan == null) {
                                    ctx.getSource().sendFailure(Component.literal("Clan not found."));
                                    return 0;
                                }

                                if (!canManageHome(playerId, clan)) {
                                    ctx.getSource().sendFailure(Component.literal("You must be Leader or Right Arm to set the clan home."));
                                    return 0;
                                }

                                // EVENTS: PlayerHomeSet (cancellable)
                                GlobalPos pos = GlobalPos.of(player.serverLevel().dimension(), player.blockPosition());
                                InteractionResult homeRes = ClansReforgedEvents.PLAYER_HOME_SET.invoker()
                                        .onHomeSet(player, clanId, pos, player.getYRot(), player.getXRot());
                                if (homeRes == InteractionResult.FAIL) {
                                    return 0;
                                }

                                String dim = player.serverLevel().dimension().location().toString();
                                clan.setHome(
                                        dim,
                                        player.getX(),
                                        player.getY(),
                                        player.getZ(),
                                        player.getYRot(),
                                        player.getXRot(),
                                        "local"
                                );

                                try {
                                    ClanDAO.saveClan(clan);
                                } catch (SQLException e) {
                                    e.printStackTrace();
                                    ctx.getSource().sendFailure(Component.literal("Failed to save clan home to database."));
                                    return 0;
                                }

                                // invalidate caches
                                gdInvalidate(clanId);

                                ctx.getSource().sendSuccess(() -> Component.literal("Clan home set."), false);
                                return 1;
                            })
                    )

                    .then(Commands.literal("home")
                            .executes(ctx -> {
                                var player = ctx.getSource().getPlayerOrException();
                                UUID playerId = player.getUUID();

                                ClanMembershipRegistry.updateLastKnownName(playerId, player.getName().getString());

                                UUID clanId = ClanMembershipRegistry.getClan(playerId);
                                if (clanId == null) {
                                    ctx.getSource().sendFailure(Component.literal("You're not in a clan."));
                                    return 0;
                                }

                                Clan clan = ClanRegistry.getClan(clanId);
                                if (clan == null) {
                                    ctx.getSource().sendFailure(Component.literal("Clan not found."));
                                    return 0;
                                }

                                if (!clan.hasHome()) {
                                    ctx.getSource().sendFailure(Component.literal("Your clan does not have a home set."));
                                    return 0;
                                }

                                ServerLevel target = resolveLevel(ctx.getSource().getServer(), clan.getHomeDimension());
                                if (target == null) {
                                    ctx.getSource().sendFailure(Component.literal("Clan home dimension is not available: " + clan.getHomeDimension()));
                                    return 0;
                                }

                                player.teleportTo(
                                        target,
                                        clan.getHomeX(),
                                        clan.getHomeY(),
                                        clan.getHomeZ(),
                                        clan.getHomeYaw(),
                                        clan.getHomePitch()
                                );

                                ctx.getSource().sendSuccess(() -> Component.literal("Teleported to clan home."), false);
                                return 1;
                            })
                    )

                    .then(Commands.literal("delhome")
                            .executes(ctx -> {
                                var player = ctx.getSource().getPlayerOrException();
                                UUID playerId = player.getUUID();

                                ClanMembershipRegistry.updateLastKnownName(playerId, player.getName().getString());

                                UUID clanId = ClanMembershipRegistry.getClan(playerId);
                                if (clanId == null) {
                                    ctx.getSource().sendFailure(Component.literal("You're not in a clan."));
                                    return 0;
                                }

                                Clan clan = ClanRegistry.getClan(clanId);
                                if (clan == null) {
                                    ctx.getSource().sendFailure(Component.literal("Clan not found."));
                                    return 0;
                                }

                                if (!canManageHome(playerId, clan)) {
                                    ctx.getSource().sendFailure(Component.literal("You must be Leader or Right Arm to delete the clan home."));
                                    return 0;
                                }

                                clan.clearHome();

                                try {
                                    ClanDAO.saveClan(clan);
                                } catch (SQLException e) {
                                    e.printStackTrace();
                                    ctx.getSource().sendFailure(Component.literal("Failed to save clan home removal to database."));
                                    return 0;
                                }

                                // invalidate caches
                                gdInvalidate(clanId);

                                ctx.getSource().sendSuccess(() -> Component.literal("Clan home removed."), false);
                                return 1;
                            })
                    )

                    /* ===================== LEAVE ===================== */
                    .then(Commands.literal("leave")
                            .executes(context -> {
                                var player = context.getSource().getPlayerOrException();
                                UUID playerId = player.getUUID();

                                ClanMembershipRegistry.updateLastKnownName(playerId, player.getName().getString());

                                if (!ClanMembershipRegistry.isInClan(playerId)) {
                                    context.getSource().sendFailure(Component.literal("You're not in a clan."));
                                    return 0;
                                }

                                UUID clanId = ClanMembershipRegistry.getClan(playerId);
                                Clan clan = ClanRegistry.getClan(clanId);

                                if (clan != null) {
                                    clan.removeMember(playerId);
                                    try {
                                        ClanDAO.saveMembers(clanId, buildMemberRanks(clan));
                                    } catch (SQLException e) {
                                        e.printStackTrace();
                                        context.getSource().sendFailure(Component.literal("Failed to save membership to database."));
                                        return 0;
                                    }
                                }

                                ClanMembershipRegistry.leaveClan(playerId);
                                ClanRegistry.cleanupEmptyClans();

                                // EVENTS: PlayerLeftClan (post)
                                ClansReforgedEvents.PLAYER_LEFT_CLAN.invoker().onLeave(player, clanId);

                                // invalidate caches
                                gdInvalidate(clanId, playerId);

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
                                        var affectedMembers = new ArrayList<>(clanToDelete.getMembers()); // cache invalidation support

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

                                        // EVENTS: DisbandClan (post) - actor may be null if command source isn't a player
                                        ServerPlayer actor = null;
                                        try {
                                            actor = context.getSource().getPlayer();
                                        } catch (Exception ignored) {}
                                        ClansReforgedEvents.DISBAND_CLAN.invoker().onDisband(actor, clanIdToDelete);

                                        // invalidate caches
                                        gdInvalidate(clanIdToDelete, affectedMembers.toArray(new UUID[0]));

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
                                var player = context.getSource().getPlayerOrException();
                                UUID playerId = player.getUUID();

                                ClanMembershipRegistry.updateLastKnownName(playerId, player.getName().getString());

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

                                var affectedMembers = new ArrayList<>(clan.getMembers()); // cache invalidation support

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

                                // EVENTS: DisbandClan (post)
                                ClansReforgedEvents.DISBAND_CLAN.invoker().onDisband(player, clanId);

                                // invalidate caches
                                gdInvalidate(clanId, affectedMembers.toArray(new UUID[0]));

                                context.getSource().sendSuccess(() ->
                                        Component.literal("Your clan '" + clan.getName() + "' (tag: " + clan.getTag() + ") has been disbanded."), false);

                                return 1;
                            })
                    )

                    /* ===================== INVITE ===================== */
                    .then(Commands.literal("invite")
                            .then(Commands.argument("player", StringArgumentType.word())
                                    .executes(context -> {
                                        var inviter = context.getSource().getPlayerOrException();
                                        UUID inviterId = inviter.getUUID();

                                        ClanMembershipRegistry.updateLastKnownName(inviterId, inviter.getName().getString());

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

                                        ClanMembershipRegistry.updateLastKnownName(targetId, targetProfile.getName());

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
                                var player = context.getSource().getPlayerOrException();
                                UUID playerId = player.getUUID();

                                ClanMembershipRegistry.updateLastKnownName(playerId, player.getName().getString());

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
                                    ClanDAO.saveMembers(invitedClanId, buildMemberRanks(clan));
                                } catch (SQLException e) {
                                    e.printStackTrace();
                                    context.getSource().sendFailure(Component.literal("Failed to save membership to database."));
                                    return 0;
                                }

                                // EVENTS: PlayerJoinedClan (post)
                                ClansReforgedEvents.PLAYER_JOINED_CLAN.invoker().onJoin(player, invitedClanId);

                                // invalidate caches
                                gdInvalidate(invitedClanId, playerId);

                                context.getSource().sendSuccess(() ->
                                        Component.literal("You have joined the clan: " + clan.getName() + " (tag: " + clan.getTag() + ")"), false);

                                return 1;
                            }))

                    /* ===================== STATUS ===================== */
                    .then(Commands.literal("status")
                            .executes(context -> {
                                var player = context.getSource().getPlayerOrException();
                                UUID playerId = player.getUUID();

                                ClanMembershipRegistry.updateLastKnownName(playerId, player.getName().getString());

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
                                                var player = context.getSource().getPlayerOrException();
                                                UUID playerId = player.getUUID();

                                                ClanMembershipRegistry.updateLastKnownName(playerId, player.getName().getString());

                                                Clan targetClan = findClanByTagOrName(oldKey);
                                                if (targetClan == null) {
                                                    context.getSource().sendFailure(Component.literal("Clan '" + oldKey + "' not found."));
                                                    return 0;
                                                }

                                                if (!targetClan.isLeader(playerId) && !context.getSource().hasPermission(2)) {
                                                    context.getSource().sendFailure(Component.literal("Only the clan leader or an admin can rename the clan."));
                                                    return 0;
                                                }

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

                                                // invalidate caches
                                                gdInvalidate(targetClan.getId());

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
