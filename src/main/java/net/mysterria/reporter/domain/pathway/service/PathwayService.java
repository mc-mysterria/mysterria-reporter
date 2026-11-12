package net.mysterria.reporter.domain.pathway.service;

import dev.ua.ikeepcalm.coi.api.CircleOfImaginationAPI;
import io.javalin.http.HttpStatus;
import lombok.Data;
import net.mysterria.reporter.MysterriaReporter;
import net.mysterria.reporter.config.ReporterConfig;
import net.mysterria.reporter.domain.pathway.model.BeyonderRoster;
import net.mysterria.reporter.domain.pathway.model.PlayerPathway;
import net.mysterria.reporter.util.api.ApiException;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public class PathwayService {

    /** Players retried per server tick, so a burst of failures does not stall the main thread. */
    private static final int RETRY_BATCH = 20;
    private static final long RETRY_TIMEOUT_SECONDS = 10;
    /** How many failed player names to list in the refresh warning, unless logging.debug-refresh lists them all. */
    private static final int FAILURES_TO_LOG = 10;

    private final MysterriaReporter plugin;
    private final PathwayReader reader;
    private final ReporterConfig config;
    private final Map<String, CachedPathway> playerCache = new ConcurrentHashMap<>();
    /** The last pathway read successfully for each beyonder, used when a later read fails. */
    private final Map<String, PlayerPathway> lastKnown = new ConcurrentHashMap<>();
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private final BukkitTask refreshTask;
    private volatile BeyonderRoster roster;
    private volatile RosterRefresh.Baseline baseline;
    private volatile String lastRefreshSummary = "no refresh yet";

    public PathwayService(MysterriaReporter plugin, CircleOfImaginationAPI coi, ReporterConfig config) {
        this.plugin = plugin;
        this.reader = new PathwayReader(coi);
        this.config = config;
        long intervalTicks = config.getRefreshIntervalMinutes() * 60L * 20L;
        this.refreshTask = intervalTicks > 0
                ? Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::refreshRoster, 1L, intervalTicks)
                : Bukkit.getScheduler().runTaskAsynchronously(plugin, this::refreshRoster);
    }

    /**
     * A player's pathway. A failed read is retried once on the main thread; if it still fails the last known value is
     * served, or a 503 when there is none. A failure is never cached as "not a beyonder".
     */
    public PlayerPathway getPlayerPathway(String playerName) {
        String key = key(playerName);
        CachedPathway cached = playerCache.get(key);
        if (cached != null && cached.expiresAt > System.currentTimeMillis()) {
            debug("Cache hit for " + playerName);
            return cached.pathway;
        }
        PathwayLoad load = reader.load(playerName);
        if (load.isFailed()) {
            load = retryOnMainThread(List.of(playerName)).getOrDefault(playerName, load);
        }
        switch (load.getStatus()) {
            case LOADED -> remember(load.getPathway());
            case NOT_BEYONDER -> lastKnown.remove(key);
            case FAILED -> {
                PlayerPathway previous = lastKnown.get(key);
                plugin.getLogger().warning("Could not read " + playerName + "'s pathway (" + load.getError() + "), "
                        + (previous != null ? "serving the last known value" : "no earlier value to serve"));
                if (previous != null) {
                    return previous;
                }
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Could not read " + playerName + "'s pathway right now, retry shortly");
            }
        }
        cache(load.getPathway());
        return load.getPathway();
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
            long started = System.currentTimeMillis();
            List<String> names = Arrays.stream(Bukkit.getOfflinePlayers())
                    .map(OfflinePlayer::getName)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            RosterRefresh.Outcome outcome = RosterRefresh.run(names, reader::load, this::retryOnMainThread,
                    name -> lastKnown.get(key(name)), baseline, config.getMaxRosterDropPercent(), Instant.now().toString());

            outcome.getFresh().forEach(this::remember);
            outcome.getFresh().forEach(this::cache);
            outcome.getNotBeyonders().forEach(name -> lastKnown.remove(key(name)));
            if (outcome.isPublished()) {
                roster = outcome.getRoster();
                baseline = new RosterRefresh.Baseline(outcome.getRoster(), outcome.getKnown());
            } else {
                roster = outcome.getBaseline().roster();
            }

            String summary = outcome.summary(System.currentTimeMillis() - started);
            lastRefreshSummary = summary;
            if (outcome.isClean()) {
                plugin.getLogger().info(summary);
            } else {
                plugin.getLogger().warning(summary + failureSample(outcome.getFailures(), config.isDebugRefresh() ? Integer.MAX_VALUE : FAILURES_TO_LOG));
            }
            return roster;
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Roster refresh failed, still serving the last good roster", e);
            return roster;
        } finally {
            refreshing.set(false);
        }
    }

    public void invalidate(String playerName) {
        playerCache.remove(key(playerName));
    }

    /**
     * Drops the cached answers and the last good roster, so the next refresh publishes whatever it reads even if it
     * is much smaller. The last known pathways stay as the fallback for players who fail to load.
     */
    public void invalidateAll() {
        playerCache.clear();
        roster = null;
        baseline = null;
    }

    public int cachedPlayers() {
        return playerCache.size();
    }

    public int rosterSize() {
        BeyonderRoster current = roster;
        return current != null ? current.getAmount() : 0;
    }

    public String lastRefreshSummary() {
        return lastRefreshSummary;
    }

    public void shutdown() {
        refreshTask.cancel();
        invalidateAll();
        lastKnown.clear();
    }

    /**
     * Reads the given players again on the main thread, a batch per tick. The async read is what fails inside CoI,
     * so the retry runs where CoI's own code runs. Players that time out or throw are left out of the result.
     */
    private Map<String, PathwayLoad> retryOnMainThread(List<String> names) {
        Map<String, PathwayLoad> results = new HashMap<>();
        if (Bukkit.isPrimaryThread()) {
            names.forEach(name -> results.put(name, reader.load(name)));
            return results;
        }
        for (int from = 0; from < names.size() && plugin.isEnabled(); from += RETRY_BATCH) {
            List<String> batch = names.subList(from, Math.min(from + RETRY_BATCH, names.size()));
            Future<List<PathwayLoad>> future = Bukkit.getScheduler().callSyncMethod(plugin,
                    () -> batch.stream().map(reader::load).toList());
            try {
                future.get(RETRY_TIMEOUT_SECONDS, TimeUnit.SECONDS).forEach(load -> results.put(load.getPlayerName(), load));
            } catch (TimeoutException e) {
                future.cancel(false);
                plugin.getLogger().warning("Main thread retry timed out after " + RETRY_TIMEOUT_SECONDS + "s, " + (names.size() - from) + " players left as failed");
                break;
            } catch (CancellationException e) {
                break;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ExecutionException e) {
                plugin.getLogger().warning("Main thread retry failed: " + e.getCause());
            }
        }
        return results;
    }

    private static String failureSample(List<PathwayLoad> failures, int limit) {
        if (failures.isEmpty()) {
            return "";
        }
        StringBuilder sample = new StringBuilder(". Failed players");
        failures.stream().limit(limit).forEach(load -> sample.append(' ').append(load.getPlayerName()).append(" [").append(load.getError()).append(']'));
        if (failures.size() > limit) {
            sample.append(" and ").append(failures.size() - limit).append(" more");
        }
        return sample.toString();
    }

    private void remember(PlayerPathway pathway) {
        lastKnown.put(key(pathway.getPlayerName()), pathway);
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
            playerCache.put(key(pathway.getPlayerName()), new CachedPathway(pathway, System.currentTimeMillis() + ttlMillis));
        }
    }

    private static String key(String playerName) {
        return playerName.toLowerCase(Locale.ROOT);
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
