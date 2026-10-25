package net.mysterria.reporter.domain.pathway.service;

import dev.ua.ikeepcalm.coi.api.CircleOfImaginationAPI;
import dev.ua.ikeepcalm.coi.api.model.BeyonderData;
import dev.ua.ikeepcalm.coi.api.model.PathwayData;
import lombok.Data;
import net.mysterria.reporter.MysterriaReporter;
import net.mysterria.reporter.config.ReporterConfig;
import net.mysterria.reporter.domain.pathway.model.BeyonderRoster;
import net.mysterria.reporter.domain.pathway.model.PlayerPathway;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class PathwayService {

    private final MysterriaReporter plugin;
    private final CircleOfImaginationAPI coi;
    private final ReporterConfig config;
    private final Map<String, CachedPathway> playerCache = new ConcurrentHashMap<>();
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private final BukkitTask refreshTask;
    private volatile BeyonderRoster roster;

    public PathwayService(MysterriaReporter plugin, CircleOfImaginationAPI coi, ReporterConfig config) {
        this.plugin = plugin;
        this.coi = coi;
        this.config = config;
        long intervalTicks = config.getRefreshIntervalMinutes() * 60L * 20L;
        this.refreshTask = intervalTicks > 0
                ? Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::refreshRoster, 1L, intervalTicks)
                : Bukkit.getScheduler().runTaskAsynchronously(plugin, this::refreshRoster);
    }

    public PlayerPathway getPlayerPathway(String playerName) {
        String key = playerName.toLowerCase(Locale.ROOT);
        CachedPathway cached = playerCache.get(key);
        if (cached != null && cached.expiresAt > System.currentTimeMillis()) {
            debug("Cache hit for " + playerName);
            return cached.pathway;
        }
        PlayerPathway pathway = loadPathway(playerName);
        cache(pathway);
        return pathway;
    }

    public BeyonderRoster getRoster() {
        BeyonderRoster current = roster;
        return current != null ? current : refreshRoster();
    }

    public BeyonderRoster refreshRoster() {
        if (!refreshing.compareAndSet(false, true)) {
            return roster;
        }
        try {
            List<PlayerPathway> beyonders = Arrays.stream(Bukkit.getOfflinePlayers())
                    .map(OfflinePlayer::getName)
                    .filter(Objects::nonNull)
                    .distinct()
                    .filter(coi::isBeyonder)
                    .map(this::loadPathway)
                    .filter(PlayerPathway::isBeyonder)
                    .toList();
            beyonders.forEach(this::cache);
            roster = new BeyonderRoster(beyonders.size(), beyonders, Instant.now().toString());
            if (config.isDebugRefresh()) {
                plugin.getLogger().info("Pathway roster refreshed, " + beyonders.size() + " beyonders");
            }
            return roster;
        } finally {
            refreshing.set(false);
        }
    }

    public void invalidate(String playerName) {
        playerCache.remove(playerName.toLowerCase(Locale.ROOT));
    }

    public void invalidateAll() {
        playerCache.clear();
        roster = null;
    }

    public int cachedPlayers() {
        return playerCache.size();
    }

    public int rosterSize() {
        BeyonderRoster current = roster;
        return current != null ? current.getAmount() : 0;
    }

    public void shutdown() {
        refreshTask.cancel();
        invalidateAll();
    }

    private PlayerPathway loadPathway(String playerName) {
        if (!coi.isBeyonder(playerName)) {
            return PlayerPathway.notBeyonder(playerName);
        }
        BeyonderData data = coi.getBeyonderData(playerName);
        if (data == null) {
            return PlayerPathway.notBeyonder(playerName);
        }
        return data.pathways().stream()
                .min(Comparator.comparingInt(PathwayData::lowestSequenceLevel))
                .map(primary -> new PlayerPathway(playerName, true, primary.name(), primary.lowestSequenceLevel(), primary.getActingPercentage()))
                .orElse(PlayerPathway.notBeyonder(playerName));
    }

    private void cache(PlayerPathway pathway) {
        if (playerCache.size() >= config.getMaxPlayerEntries()) {
            long now = System.currentTimeMillis();
            playerCache.values().removeIf(entry -> entry.expiresAt <= now);
            if (playerCache.size() >= config.getMaxPlayerEntries()) {
                playerCache.clear();
            }
        }
        long ttlMillis = config.getPlayerTtlMinutes() * 60_000L;
        if (ttlMillis > 0) {
            playerCache.put(pathway.getPlayerName().toLowerCase(Locale.ROOT), new CachedPathway(pathway, System.currentTimeMillis() + ttlMillis));
        }
    }

    private void debug(String message) {
        if (config.isDebugCache()) {
            plugin.getLogger().info(message);
        }
    }

    @Data
    private static class CachedPathway {
        private final PlayerPathway pathway;
        private final long expiresAt;
    }
}
