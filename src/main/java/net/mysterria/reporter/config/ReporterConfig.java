package net.mysterria.reporter.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Set;
import java.util.stream.Collectors;

@Getter
@RequiredArgsConstructor
public class ReporterConfig {

    private final int playerTtlMinutes;
    private final int refreshIntervalMinutes;
    private final int maxPlayerEntries;
    private final boolean debugCache;
    private final boolean debugRefresh;
    private final Set<String> enabledEndpoints;
    private final boolean backendEnabled;
    private final boolean backendDryRun;
    private final String backendUrl;
    private final String backendKey;
    private final int backendBatchSize;
    private final int scanIntervalMinutes;
    private final int heartbeatSeconds;

    public static ReporterConfig load(FileConfiguration config) {
        ConfigurationSection endpoints = config.getConfigurationSection("endpoints");
        return new ReporterConfig(
                config.getInt("cache.player-ttl"),
                config.getInt("cache.refresh-interval"),
                config.getInt("cache.max-player-entries"),
                config.getBoolean("logging.debug-cache"),
                config.getBoolean("logging.debug-refresh"),
                endpoints == null ? Set.of() : endpoints.getKeys(false).stream().filter(endpoints::getBoolean).collect(Collectors.toUnmodifiableSet()),
                config.getBoolean("backend.enabled"),
                config.getBoolean("backend.dry-run"),
                config.getString("backend.url", "").replaceAll("/+$", ""),
                config.getString("backend.key", ""),
                config.getInt("backend.batch-size"),
                config.getInt("profile.scan-interval-minutes"),
                config.getInt("profile.heartbeat-seconds")
        );
    }

    public boolean isEnabled(String endpoint) {
        return enabledEndpoints.contains(endpoint);
    }
}
