// command/GuildCommand.java
package net.hosenka.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.hosenka.guild.Guild;
import net.hosenka.guild.GuildMembershipRegistry;
import net.hosenka.guild.GuildRegistry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.UUID;

public class GuildCommand {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("guild")
                    .then(CommandManager.literal("create")
                            .then(CommandManager.argument("name", StringArgumentType.string())
                                    .executes(context -> {
                                        String name = StringArgumentType.getString(context, "name");

                                        GuildRegistry.createGuild(name);

                                        context.getSource().sendFeedback(
                                                () -> Text.literal("Guild created: " + name),
                                                false
                                        );
                                        return 1;
                                    })))
                    .then(CommandManager.literal("join")
                            .then(CommandManager.argument("name", StringArgumentType.string())
                                    .executes(context -> {
                                        String name = StringArgumentType.getString(context, "name");
                                        UUID playerId = context.getSource().getPlayer().getUuid();

                                        if (GuildMembershipRegistry.isInGuild(playerId)) {
                                            context.getSource().sendError(Text.literal("You're already in a guild."));
                                            return 0;
                                        }

                                        // Search for guild by name
                                        Guild targetGuild = null;
                                        for (Map.Entry<UUID, Guild> entry : GuildRegistry.getAllGuilds().entrySet()) {
                                            if (entry.getValue().getName().equalsIgnoreCase(name)) {
                                                targetGuild = entry.getValue();
                                                break;
                                            }
                                        }

                                        if (targetGuild == null) {
                                            context.getSource().sendError(Text.literal("Guild '" + name + "' not found."));
                                            return 0;
                                        }

                                        // Add player to guild
                                        targetGuild.addMember(playerId);
                                        GuildMembershipRegistry.joinGuild(playerId, targetGuild.getId());

                                        context.getSource().sendFeedback(
                                                () -> Text.literal("You have joined the guild: " + name),
                                                false
                                        );
                                        return 1;
                                    })))
            );
        });
    }
}
