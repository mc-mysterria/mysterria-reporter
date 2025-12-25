package net.mysterria.reporter.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.mysterria.reporter.MysterriaReporter;
import net.mysterria.reporter.manager.PathwayCacheManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CacheCommand implements CommandExecutor, TabCompleter {

    private final MysterriaReporter plugin;
    private final PathwayCacheManager cacheManager;

    public CacheCommand(MysterriaReporter plugin, PathwayCacheManager cacheManager) {
        this.plugin = plugin;
        this.cacheManager = cacheManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("mysterriareporter.cache")) {
            sender.sendMessage(Component.text("You don't have permission to use this command.").color(NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "refresh":
                cacheManager.refreshEveryoneCache();
                sender.sendMessage(Component.text("Cache refresh started...").color(NamedTextColor.GREEN));
                break;

            case "clear":
                if (args.length > 1) {
                    String playerName = args[1];
                    cacheManager.invalidatePlayerCache(playerName);
                    sender.sendMessage(Component.text("Cache cleared for player: " + playerName).color(NamedTextColor.GREEN));
                } else {
                    cacheManager.invalidateAllCaches();
                    sender.sendMessage(Component.text("All caches cleared!").color(NamedTextColor.GREEN));
                }
                break;

            case "stats":
                long playerCacheSize = cacheManager.getPlayerCacheSize();
                int everyoneCacheSize = cacheManager.getEveryoneCacheSize();
                sender.sendMessage(Component.text("=== Cache Statistics ===").color(NamedTextColor.GOLD));
                sender.sendMessage(Component.text("Player cache entries: " + playerCacheSize).color(NamedTextColor.YELLOW));
                sender.sendMessage(Component.text("Everyone cache entries: " + everyoneCacheSize).color(NamedTextColor.YELLOW));
                break;

            case "reload":
                plugin.reload();
                sender.sendMessage(Component.text("Configuration reloaded and caches refreshed!").color(NamedTextColor.GREEN));
                break;

            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== MysterriaReporter Cache Commands ===").color(NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/reporter refresh").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Refresh everyone cache").color(NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("/reporter clear [player]").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Clear cache (all or specific player)").color(NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("/reporter stats").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Show cache statistics").color(NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("/reporter reload").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Reload config and refresh caches").color(NamedTextColor.WHITE)));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("refresh");
            completions.add("clear");
            completions.add("stats");
            completions.add("reload");
        }

        return completions;
    }
}
