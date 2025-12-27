// command/GuildCommand.java
package net.hosenka.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;

public class GuildCommand {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("guild")
                    .then(CommandManager.literal("create")
                            .then(CommandManager.argument("name", StringArgumentType.string())
                                    .executes(context -> {
                                        String name = StringArgumentType.getString(context, "name");
                                        // create guild logic here...
                                        context.getSource().sendFeedback(
                                                () -> Text.literal("Guild created: " + name),
                                                false
                                        );

                                        return 1;
                                    }))));
        });
    }
}

