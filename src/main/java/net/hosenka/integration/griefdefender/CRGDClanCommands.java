package net.hosenka.integration.griefdefender;

import com.griefdefender.api.Clan;
import com.griefdefender.api.CommandResult;
import com.griefdefender.api.GriefDefender;
import com.griefdefender.api.claim.Claim;
import com.griefdefender.api.claim.ClaimResult;
import com.griefdefender.api.claim.TrustType;
import com.griefdefender.api.claim.TrustTypes;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.hosenka.util.CRDebug;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class CRGDClanCommands {

    private CRGDClanCommands() {}

    /**
     * Injects /gd clan trust|untrust into GD's existing /gd command.
     * Call this from SERVER_STARTED (after GDIntegration.init()).
     */
    public static void register(MinecraftServer server, ClansReforgedClanProvider clanProvider) {
        if (server == null || clanProvider == null) {
            return;
        }

        final CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
        final CommandNode<CommandSourceStack> root = dispatcher.getRoot();
        final CommandNode<CommandSourceStack> gdNode = root.getChild("gd");

        if (!(gdNode instanceof LiteralCommandNode<CommandSourceStack> gdLiteral)) {
            CRDebug.log("Could not find /gd root node to inject into. (gd node missing or not literal)");
            return;
        }

        if (gdLiteral.getChild("clan") != null) {
            CRDebug.log("/gd already has 'clan' child. Skipping injection.");
            return;
        }

        // Build: /gd clan trust ... and /gd clan untrust ...
        LiteralCommandNode<CommandSourceStack> clanNode = Commands.literal("clan")
                .then(buildTrustNode(clanProvider))
                .then(buildUntrustNode(clanProvider))
                .build();

        gdLiteral.addChild(clanNode);

        CRDebug.log("Injected /gd clan trust|untrust command nodes into GD dispatcher.");
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildTrustNode(ClansReforgedClanProvider clanProvider) {
        return Commands.literal("trust")
                .then(Commands.argument("clan", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestClans(clanProvider, builder))
                        // /gd clan trust <clan>   (default builder)
                        .executes(ctx -> executeClanTrust(ctx, clanProvider, TrustTypes.BUILDER, null))
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestTrustTypes(builder))
                                // /gd clan trust <clan> <type>
                                .executes(ctx -> executeClanTrust(ctx, clanProvider,
                                        parseTrustType(StringArgumentType.getString(ctx, "type"), true),
                                        null
                                ))
                                .then(Commands.argument("identifier", StringArgumentType.word())
                                        // /gd clan trust <clan> <type> <identifier>
                                        .executes(ctx -> executeClanTrust(ctx, clanProvider,
                                                parseTrustType(StringArgumentType.getString(ctx, "type"), true),
                                                StringArgumentType.getString(ctx, "identifier")
                                        ))
                                )
                        )
                );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildUntrustNode(ClansReforgedClanProvider clanProvider) {
        return Commands.literal("untrust")
                .then(Commands.argument("clan", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestClans(clanProvider, builder))
                        // /gd clan untrust <clan>   (default none)
                        .executes(ctx -> executeClanUntrust(ctx, clanProvider, TrustTypes.NONE, null))
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestTrustTypes(builder))
                                // /gd clan untrust <clan> <type>
                                .executes(ctx -> executeClanUntrust(ctx, clanProvider,
                                        parseTrustType(StringArgumentType.getString(ctx, "type"), false),
                                        null
                                ))
                                .then(Commands.argument("identifier", StringArgumentType.word())
                                        // /gd clan untrust <clan> <type> <identifier>
                                        .executes(ctx -> executeClanUntrust(ctx, clanProvider,
                                                parseTrustType(StringArgumentType.getString(ctx, "type"), false),
                                                StringArgumentType.getString(ctx, "identifier")
                                        ))
                                )
                        )
                );
    }

    private static int executeClanTrust(
            CommandContext<CommandSourceStack> ctx,
            ClansReforgedClanProvider clanProvider,
            TrustType trustType,
            String identifier
    ) {
        final CommandSourceStack source = ctx.getSource();
        final ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }

        final String clanArg = StringArgumentType.getString(ctx, "clan");
        CRDebug.log("GD clan trust called: clan=" + clanArg + " type=" + trustType.getName() + " identifier=" + identifier);

        final Clan clan = clanProvider.getClan(clanArg);
        if (clan == null) {
            source.sendFailure(Component.literal("Unknown clan: " + clanArg));
            return 0;
        }

        final CommandResult canUse = GriefDefender.getCore().canUseCommand(player, TrustTypes.MANAGER, identifier);
        if (!canUse.successful() || canUse.getClaim() == null) {
            source.sendFailure(Component.literal("You don't have permission to trust here (or no claim context)."));
            return 0;
        }

        final Claim claim = canUse.getClaim();

        // Match GDHooks behavior: resident only allowed in admin/town
        if (!claim.isAdminClaim() && !claim.isTown() && trustType == TrustTypes.RESIDENT) {
            source.sendFailure(Component.literal("Resident trust not allowed in this claim type."));
            return 0;
        }

        if (claim.isClanTrusted(clan, trustType)) {
            source.sendFailure(Component.literal("Clan already has " + trustType.getName() + " trust: " + clan.getTag()));
            return 0;
        }

        // Cause stack like hooks (safe even if GD ignores it on Fabric)
        try {
            GriefDefender.getEventManager().getCauseStackManager().pushCause(player);
        } catch (Throwable ignored) {}

        final ClaimResult result = claim.addClanTrust(clan.getTag(), trustType);

        try {
            GriefDefender.getEventManager().getCauseStackManager().popCause();
        } catch (Throwable ignored) {}

        if (!result.successful()) {
            source.sendFailure(Component.literal("Failed to add clan trust: " + clan.getTag()));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Granted " + trustType.getName() + " trust to clan " + clan.getTag()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeClanUntrust(
            CommandContext<CommandSourceStack> ctx,
            ClansReforgedClanProvider clanProvider,
            TrustType trustType,
            String identifier
    ) {
        final CommandSourceStack source = ctx.getSource();
        final ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }

        final String clanArg = StringArgumentType.getString(ctx, "clan");
        CRDebug.log("GD clan untrust called: clan=" + clanArg + " type=" + trustType.getName() + " identifier=" + identifier);

        final Clan clan = clanProvider.getClan(clanArg);
        if (clan == null) {
            source.sendFailure(Component.literal("Unknown clan: " + clanArg));
            return 0;
        }

        final CommandResult canUse = GriefDefender.getCore().canUseCommand(player, TrustTypes.MANAGER, identifier);
        if (!canUse.successful() || canUse.getClaim() == null) {
            source.sendFailure(Component.literal("You don't have permission to untrust here (or no claim context)."));
            return 0;
        }

        final Claim claim = canUse.getClaim();

        try {
            GriefDefender.getEventManager().getCauseStackManager().pushCause(player);
        } catch (Throwable ignored) {}

        final ClaimResult result = claim.removeClanTrust(clan.getTag(), trustType);

        try {
            GriefDefender.getEventManager().getCauseStackManager().popCause();
        } catch (Throwable ignored) {}

        if (!result.successful()) {
            source.sendFailure(Component.literal("Failed to remove clan trust: " + clan.getTag()));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Removed clan trust for " + clan.getTag()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestClans(
            ClansReforgedClanProvider clanProvider,
            SuggestionsBuilder builder
    ) {
        try {
            for (Clan clan : clanProvider.getAllClans()) {
                builder.suggest(clan.getTag());
                builder.suggest(clan.getName());
            }
        } catch (Throwable t) {
            CRDebug.log("Clan suggestions failed", t);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestTrustTypes(SuggestionsBuilder builder) {
        builder.suggest("accessor");
        builder.suggest("builder");
        builder.suggest("container");
        builder.suggest("manager");
        builder.suggest("resident");
        return builder.buildFuture();
    }

    private static TrustType parseTrustType(String input, boolean forTrust) {
        if (input == null || input.isBlank()) {
            return forTrust ? TrustTypes.BUILDER : TrustTypes.NONE;
        }

        String s = input.trim().toLowerCase(Locale.ROOT);
        if (s.endsWith("s")) {
            s = s.substring(0, s.length() - 1); // accessors -> accessor
        }

        return switch (s) {
            case "accessor" -> TrustTypes.ACCESSOR;
            case "builder" -> TrustTypes.BUILDER;
            case "container" -> TrustTypes.CONTAINER;
            case "manager" -> TrustTypes.MANAGER;
            case "resident" -> TrustTypes.RESIDENT;
            case "none" -> TrustTypes.NONE;
            default -> forTrust ? TrustTypes.BUILDER : TrustTypes.NONE;
        };
    }


}
