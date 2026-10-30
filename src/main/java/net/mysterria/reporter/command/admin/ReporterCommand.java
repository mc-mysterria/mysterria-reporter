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
import net.mysterria.reporter.domain.profile.service.ProfilePushService;
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
                        .then(Commands.literal("backfill").executes(this::backfill))
                        .build(),
                "Manage the MysterriaReporter pathway cache",
                List.of("mr", "pathwaycache")
        ));
    }

    private int help(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        sender.sendMessage(Component.text("/reporter refresh - rebuild the beyonder roster", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/reporter clear [player] - drop cached pathways", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/reporter stats - cache sizes and profile push state", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/reporter backfill - resend every profile to the backend", NamedTextColor.YELLOW));
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
        ProfilePushService push = plugin.getProfilePushService();
        if (push == null) {
            sender.sendMessage(Component.text("Profile push: disabled in config", NamedTextColor.YELLOW));
        } else {
            sender.sendMessage(Component.text("Profile push for world " + plugin.getWorldUid() + ": " + push.pendingPlayers() + " players pending"
                    + (push.isEpochClosed() ? ", epoch closed" : ""), NamedTextColor.YELLOW));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int backfill(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (plugin.getProfileScanService() == null) {
            sender.sendMessage(Component.text("Profile push is disabled in config.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
        plugin.getProfilePushService().reset();
        plugin.getProfileScanService().reset();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, plugin.getProfileScanService()::scan);
        sender.sendMessage(Component.text("Backfill started.", NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private PathwayService pathways() {
        return plugin.getPathwayService();
    }
}
