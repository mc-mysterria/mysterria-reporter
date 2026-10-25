package net.mysterria.reporter.command.admin;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.mysterria.reporter.MysterriaReporter;
import net.mysterria.reporter.domain.pathway.service.PathwayService;
import org.bukkit.command.CommandSender;

import java.util.List;

public class ReporterCommand {

    private final MysterriaReporter plugin;

    public ReporterCommand(MysterriaReporter plugin) {
        this.plugin = plugin;
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(
                Commands.literal("reporter")
                        .requires(source -> source.getSender().hasPermission("mysterriareporter.cache"))
                        .executes(this::help)
                        .then(Commands.literal("refresh").executes(this::refresh))
                        .then(Commands.literal("clear")
                                .executes(this::clearAll)
                                .then(Commands.argument("player", StringArgumentType.word()).executes(this::clearPlayer)))
                        .then(Commands.literal("stats").executes(this::stats))
                        .build(),
                "Manage the MysterriaReporter pathway cache",
                List.of("mr", "pathwaycache")
        ));
    }

    private int help(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        sender.sendMessage(Component.text("/reporter refresh - rebuild the beyonder roster", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/reporter clear [player] - drop cached pathways", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/reporter stats - cache sizes", NamedTextColor.YELLOW));
        return Command.SINGLE_SUCCESS;
    }

    private int refresh(CommandContext<CommandSourceStack> context) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, pathways()::refreshRoster);
        context.getSource().getSender().sendMessage(Component.text("Roster refresh started.", NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private int clearAll(CommandContext<CommandSourceStack> context) {
        pathways().invalidateAll();
        context.getSource().getSender().sendMessage(Component.text("All pathway caches cleared.", NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private int clearPlayer(CommandContext<CommandSourceStack> context) {
        String player = StringArgumentType.getString(context, "player");
        pathways().invalidate(player);
        context.getSource().getSender().sendMessage(Component.text("Cache cleared for " + player + ".", NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private int stats(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        sender.sendMessage(Component.text("Cached players: " + pathways().cachedPlayers(), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("Beyonders in roster: " + pathways().rosterSize(), NamedTextColor.YELLOW));
        return Command.SINGLE_SUCCESS;
    }

    private PathwayService pathways() {
        return plugin.getPathwayService();
    }
}
