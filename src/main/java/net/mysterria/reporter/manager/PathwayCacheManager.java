package net.mysterria.reporter.manager;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.ua.ikeepcalm.coi.api.CircleOfImaginationAPI;
import net.mysterria.reporter.MysterriaReporter;
import net.mysterria.reporter.model.EveryonePathwayResponse;
import net.mysterria.reporter.model.PlayerPathwayResponse;
import net.mysterria.reporter.util.PathwayUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class PathwayCacheManager {

    private final MysterriaReporter plugin;
    private final CircleOfImaginationAPI coiAPI;
    private final Cache<String, PlayerPathwayResponse> playerCache;
    private final boolean debugCache;
    private final boolean debugRefresh;
    private final int refreshInterval;
    private EveryonePathwayResponse everyoneCache;
    private BukkitTask refreshTask;

    public PathwayCacheManager(MysterriaReporter plugin, CircleOfImaginationAPI coiAPI) {
        this.plugin = plugin;
        this.coiAPI = coiAPI;

        FileConfiguration config = plugin.getConfig();

        this.debugCache = config.getBoolean("logging.debug-cache", false);
        this.debugRefresh = config.getBoolean("logging.debug-refresh", false);
        this.refreshInterval = config.getInt("cache.refresh-interval", 15);

        int playerTTL = config.getInt("cache.player-ttl", 5);
        int maxPlayerEntries = config.getInt("cache.max-player-entries", 1000);

        this.playerCache = Caffeine.newBuilder()
                .expireAfterWrite(playerTTL, TimeUnit.MINUTES)
                .maximumSize(maxPlayerEntries)
                .build();

        loadEveryoneCacheAsync();
        startRefreshTask();
    }

    public PlayerPathwayResponse getPlayerPathway(String playerName) {
        PlayerPathwayResponse cached = playerCache.getIfPresent(playerName);

        if (cached != null) {
            if (debugCache) {
                PathwayUtil.logDebug("Cache HIT for player: " + playerName);
            }
            return cached;
        }

        if (debugCache) {
            PathwayUtil.logDebug("Cache MISS for player: " + playerName);
        }

        PlayerPathwayResponse response = PathwayUtil.getPlayerPathwayData(playerName, coiAPI);
        playerCache.put(playerName, response);
        return response;
    }

    public EveryonePathwayResponse getEveryonePathways() {
        if (everyoneCache != null) {
            if (debugCache) {
                PathwayUtil.logDebug("Cache HIT for everyone pathway data");
            }
            return everyoneCache;
        }

        if (debugCache) {
            PathwayUtil.logDebug("Cache MISS for everyone pathway data - loading synchronously");
        }

        return loadEveryoneCache();
    }

    public void invalidatePlayerCache(String playerName) {
        playerCache.invalidate(playerName);
        if (debugCache) {
            PathwayUtil.logDebug("Invalidated cache for player: " + playerName);
        }
    }

    public void invalidateAllCaches() {
        playerCache.invalidateAll();
        everyoneCache = null;
        if (debugCache) {
            PathwayUtil.logDebug("Invalidated all caches");
        }
    }

    public void refreshEveryoneCache() {
        if (debugRefresh) {
            PathwayUtil.logDebug("Starting everyone cache refresh...");
        }
        loadEveryoneCacheAsync();
    }

    private void loadEveryoneCacheAsync() {
        CompletableFuture.runAsync(() -> {
            try {
                everyoneCache = loadEveryoneCache();
                if (debugRefresh) {
                    PathwayUtil.logDebug("Everyone cache refreshed successfully - " +
                                         everyoneCache.getAmount() + " beyonders loaded");
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Error refreshing everyone cache: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private EveryonePathwayResponse loadEveryoneCache() {
        List<PlayerPathwayResponse> beyonders = new ArrayList<>();

        OfflinePlayer[] offlinePlayers = Bukkit.getOfflinePlayers();
        for (OfflinePlayer player : offlinePlayers) {
            String playerName = player.getName();
            if (playerName == null) continue;

            if (coiAPI.isBeyonder(playerName)) {
                PlayerPathwayResponse playerData = PathwayUtil.getPlayerPathwayData(playerName, coiAPI);
                if (playerData.getBeyonder()) {
                    beyonders.add(playerData);
                    playerCache.put(playerName, playerData);
                }
            }
        }

        return EveryonePathwayResponse.builder()
                .amount(beyonders.size())
                .beyonder(beyonders)
                .build();
    }

    private void startRefreshTask() {
        if (refreshInterval <= 0) {
            plugin.getLogger().info("Automatic cache refresh is disabled");
            return;
        }

        long intervalTicks = refreshInterval * 60L * 20L;

        this.refreshTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin,
                this::refreshEveryoneCache,
                intervalTicks,
                intervalTicks
        );

        plugin.getLogger().info("Started cache refresh task - interval: " + refreshInterval + " minutes");
    }

    public void shutdown() {
        if (refreshTask != null && !refreshTask.isCancelled()) {
            refreshTask.cancel();
            plugin.getLogger().info("Cache refresh task cancelled");
        }
        playerCache.invalidateAll();
        everyoneCache = null;
    }

    public long getPlayerCacheSize() {
        return playerCache.estimatedSize();
    }

    public int getEveryoneCacheSize() {
        return everyoneCache != null ? everyoneCache.getAmount() : 0;
    }
}
