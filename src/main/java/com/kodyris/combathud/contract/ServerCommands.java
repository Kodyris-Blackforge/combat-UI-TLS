package com.kodyris.combathud.contract;

import com.kodyris.combathud.CombatHud;
import com.kodyris.combathud.net.Net;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;

/**
 * /contract add|progress|set|complete|fail|remove|clear   (op level 2, works from command blocks and functions)
 * /hudtitle <targets> <text>   ("Sector 2"  or  "NOW LEAVING | Sector 2")
 */
@Mod.EventBusSubscriber(modid = CombatHud.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ServerCommands {
    private ServerCommands() {}

    private interface IdOp { boolean run(ServerPlayer p, String id); }
    private interface ValueOp { boolean run(ServerPlayer p, String id, int value); }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();

        d.register(Commands.literal("contract")
                .requires(s -> s.hasPermission(2))
                .then(addCommand())
                .then(valueCommand("progress", ContractManager::progress))
                .then(valueCommand("set", ContractManager::set))
                .then(idCommand("complete", ContractManager::complete))
                .then(idCommand("fail", ContractManager::fail))
                .then(idCommand("remove", ContractManager::remove))
                .then(hookCommand("onfinish", true))
                .then(hookCommand("onfail", false))
                .then(clearCommand()));

        d.register(titleCommand());
    }

    // ---------------------------------------------------------------- /contract add (built inside-out)
    private static LiteralArgumentBuilder<CommandSourceStack> addCommand() {
        var description = Commands.argument("description", StringArgumentType.greedyString())
                .executes(ServerCommands::runAdd);
        var seconds = Commands.argument("seconds", IntegerArgumentType.integer(-1)).then(description);
        var reward = Commands.argument("reward", IntegerArgumentType.integer(0)).then(seconds);
        var target = Commands.argument("target", IntegerArgumentType.integer(1)).then(reward);
        var type = Commands.argument("type", StringArgumentType.word()).then(target);
        var id = Commands.argument("id", StringArgumentType.word()).then(type);
        var targets = Commands.argument("targets", EntityArgument.players()).then(id);
        return Commands.literal("add").then(targets);
    }

    private static int runAdd(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "targets");
        for (ServerPlayer p : players) {
            ContractManager.add(p,
                    StringArgumentType.getString(c, "id"),
                    StringArgumentType.getString(c, "type"),
                    StringArgumentType.getString(c, "description"),
                    IntegerArgumentType.getInteger(c, "target"),
                    IntegerArgumentType.getInteger(c, "reward"),
                    IntegerArgumentType.getInteger(c, "seconds"));
        }
        return players.size();
    }

    // ---------------------------------------------------------------- other subcommands
    private static LiteralArgumentBuilder<CommandSourceStack> clearCommand() {
        var targets = Commands.argument("targets", EntityArgument.players())
                .executes(c -> {
                    int n = 0;
                    for (ServerPlayer p : EntityArgument.getPlayers(c, "targets")) {
                        if (ContractManager.clear(p)) n++;
                    }
                    return n;
                });
        return Commands.literal("clear").then(targets);
    }

    /** /contract onfinish|onfail <targets> <id> <command...>   (use "-" as the command to clear it) */
    private static LiteralArgumentBuilder<CommandSourceStack> hookCommand(String name, boolean finish) {
        var command = Commands.argument("command", StringArgumentType.greedyString())
                .executes(c -> {
                    String text = StringArgumentType.getString(c, "command").trim();
                    if (text.equals("-")) text = "";
                    int n = 0;
                    for (ServerPlayer p : EntityArgument.getPlayers(c, "targets")) {
                        if (ContractManager.setCommand(p, StringArgumentType.getString(c, "id"), finish, text)) n++;
                    }
                    return n;
                });
        var id = Commands.argument("id", StringArgumentType.word()).then(command);
        var targets = Commands.argument("targets", EntityArgument.players()).then(id);
        return Commands.literal(name).then(targets);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> idCommand(String name, IdOp op) {
        var id = Commands.argument("id", StringArgumentType.word())
                .executes(c -> {
                    int n = 0;
                    for (ServerPlayer p : EntityArgument.getPlayers(c, "targets")) {
                        if (op.run(p, StringArgumentType.getString(c, "id"))) n++;
                    }
                    return n;   // number of players that had that contract
                });
        var targets = Commands.argument("targets", EntityArgument.players()).then(id);
        return Commands.literal(name).then(targets);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> valueCommand(String name, ValueOp op) {
        var value = Commands.argument("value", IntegerArgumentType.integer())
                .executes(c -> {
                    int n = 0;
                    for (ServerPlayer p : EntityArgument.getPlayers(c, "targets")) {
                        if (op.run(p, StringArgumentType.getString(c, "id"), IntegerArgumentType.getInteger(c, "value"))) n++;
                    }
                    return n;
                });
        var id = Commands.argument("id", StringArgumentType.word()).then(value);
        var targets = Commands.argument("targets", EntityArgument.players()).then(id);
        return Commands.literal(name).then(targets);
    }

    // ---------------------------------------------------------------- /hudtitle
    /**
     * /hudtitle <targets> <text>
     * /hudtitle <targets> style <default|complete|fail|team|class> <text>
     */
    private static LiteralArgumentBuilder<CommandSourceStack> titleCommand() {
        var plain = Commands.argument("text", StringArgumentType.greedyString())
                .executes(c -> runTitle(c, "default"));
        var styledText = Commands.argument("text", StringArgumentType.greedyString())
                .executes(c -> runTitle(c, StringArgumentType.getString(c, "style")));
        var style = Commands.argument("style", StringArgumentType.word())
                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                        new String[]{"default", "complete", "fail", "team", "class"}, b))
                .then(styledText);
        var styled = Commands.literal("style").then(style);
        var targets = Commands.argument("targets", EntityArgument.players()).then(styled).then(plain);
        return Commands.literal("hudtitle").requires(s -> s.hasPermission(2)).then(targets);
    }

    private static int runTitle(CommandContext<CommandSourceStack> c, String style) throws CommandSyntaxException {
        String text = StringArgumentType.getString(c, "text").trim();
        String label = "", name = text;
        int bar = text.indexOf('|');
        if (bar >= 0) {
            label = text.substring(0, bar).trim();
            name = text.substring(bar + 1).trim();
        }
        Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "targets");
        for (ServerPlayer p : players) Net.title(p, label, name, style);
        return players.size();
    }
}
