package net.mysterria.reporter;

import dev.ua.ikeepcalm.catwalk.hub.webserver.services.CatWalkWebserverService;
import dev.ua.ikeepcalm.coi.api.CircleOfImaginationAPI;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.mysterria.reporter.api.ArtifactEndpoint;
import net.mysterria.reporter.api.BeyonderDataEndpoint;
import net.mysterria.reporter.api.BeyonderLogsEndpoint;
import net.mysterria.reporter.api.EveryonePathwayEndpoint;
import net.mysterria.reporter.api.PlayerPathwayEndpoint;
import net.mysterria.reporter.command.CacheCommand;
import net.mysterria.reporter.manager.PathwayCacheManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public class MysterriaReporter extends JavaPlugin {

    @Getter
    private static MysterriaReporter instance;
    private CircleOfImaginationAPI coiAPI;
    private PathwayCacheManager cacheManager;

    @Override
    public void onEnable() {
        instance = this;

        log("Loading Mysterria Reporter...");

        saveDefaultConfig();

        enableCoiApi();

        cacheManager = new PathwayCacheManager(this, coiAPI);

        CatWalkWebserverService webserverService = Bukkit.getServicesManager().load(CatWalkWebserverService.class);
        if (webserverService == null) {
            getLogger().severe("Failed to load CatWalkWebserverService. Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        registerEndpoints(webserverService);
        registerCommands();

        log("Mysterria Reporter loaded successfully!");
    }

    private void registerCommands() {
        PluginCommand reporterCommand = getCommand("reporter");
        if (reporterCommand != null) {
            CacheCommand cacheCommand = new CacheCommand(this, cacheManager);
            reporterCommand.setExecutor(cacheCommand);
            reporterCommand.setTabCompleter(cacheCommand);
            log("Registered /reporter command");
        }
    }

    private void registerEndpoints(CatWalkWebserverService webserverService) {
        if (getConfig().getBoolean("endpoints.player-pathway", true)) {
            webserverService.registerHandlers(new PlayerPathwayEndpoint(cacheManager));
            log("Registered PlayerPathwayEndpoint at /pathway/single/{player}");
        }

        if (getConfig().getBoolean("endpoints.everyone-pathway", true)) {
            webserverService.registerHandlers(new EveryonePathwayEndpoint(cacheManager));
            log("Registered EveryonePathwayEndpoint at /pathway/everyone");
        }

        if (getConfig().getBoolean("endpoints.beyonder-data", true)) {
            webserverService.registerHandlers(new BeyonderDataEndpoint());
            log("Registered BeyonderDataEndpoint at /beyonder/{player}");
        }

        if (getConfig().getBoolean("endpoints.beyonder-logs", true)) {
            webserverService.registerHandlers(new BeyonderLogsEndpoint());
            log("Registered BeyonderLogsEndpoint at /beyonder/logs/{player}/{amount}");
        }

        if (getConfig().getBoolean("endpoints.artifact", true)) {
            webserverService.registerHandlers(new ArtifactEndpoint());
            log("Registered ArtifactEndpoint at /artifact/{id}");
        }
    }

    private void enableCoiApi() {
        Plugin coiPlugin = getServer().getPluginManager().getPlugin("CircleOfImagination");
        if (coiPlugin == null || !coiPlugin.isEnabled()) {
            log("CircleOfImagination plugin not found or not enabled, disabling plugin");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            CircleOfImaginationAPI api = Bukkit.getServer().getServicesManager().load(CircleOfImaginationAPI.class);
            if (api == null) {
                log("CircleOfImagination API not registered, disabling plugin");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            this.coiAPI = api;
            log("CircleOfImagination API hooked successfully");
        } catch (Throwable t) {
            log("Failed to hook CircleOfImagination API: " + t.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (cacheManager != null) {
            cacheManager.shutdown();
        }
        log("MysterriaReporter has been disabled!");
    }

    public void log(String message) {
        Component debugMessage = Component.text("[MR] ").color(NamedTextColor.GREEN).append(Component.text(message).color(NamedTextColor.WHITE));
        Bukkit.getConsoleSender().sendMessage(debugMessage);
    }

    public void reload() {
        reloadConfig();
        if (cacheManager != null) {
            cacheManager.invalidateAllCaches();
            cacheManager.refreshEveryoneCache();
        }
        log("Configuration reloaded and caches invalidated!");
    }
}