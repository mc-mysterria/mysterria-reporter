package net.mysterria.reporter.domain.profile.service;

import lombok.RequiredArgsConstructor;
import org.bukkit.NamespacedKey;
import org.bukkit.Statistic;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.nio.file.Path;
import java.util.*;

@RequiredArgsConstructor
public class PresenceService {

    private static final NamespacedKey STARTING_CONTINENT = new NamespacedKey("mysterriamap", "starting_continent");

    private final Plugin plugin;
    private final ProfilePushService push;
    private final LandLookup landLookup;
    private Map<String, String> titlePermissions = Map.of();

    public void loadTitlePermissions() {
        Path titles = Path.of("plugins", "MysterriaTitles", "titles.yml");
        if (!titles.toFile().isFile()) return;
        ConfigurationSection root = YamlConfiguration.loadConfiguration(titles.toFile()).getConfigurationSection("titles");
        if (root == null) return;
        Map<String, String> permissions = new HashMap<>();
        for (String id : root.getKeys(false)) {
            if ("PERMISSION".equalsIgnoreCase(root.getString(id + ".unlock.method"))) {
                permissions.put(id, root.getString(id + ".unlock.permission", "mysterria.titles.title." + id));
            }
        }
        titlePermissions = Map.copyOf(permissions);
    }

    public void beat() {
        List<Map<String, Object>> online = new ArrayList<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            push.offer(player.getUniqueId(), "granted_titles", grantedTitles(player));
            if (landLookup != null) push.offer(player.getUniqueId(), "land", landLookup.landOf(player));
            if (isVanished(player)) continue;
            Map<String, Object> entry = new HashMap<>();
            entry.put("uuid", player.getUniqueId().toString());
            entry.put("name", player.getName());
            entry.put("playtimeTicks", player.getStatistic(Statistic.PLAY_ONE_MINUTE));
            String continent = player.getPersistentDataContainer().get(STARTING_CONTINENT, PersistentDataType.STRING);
            if (continent != null) entry.put("continent", continent);
            online.add(entry);
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> push.post("/api/internal/players/heartbeat", Map.of("players", online)));
    }

    private Object grantedTitles(Player player) {
        List<String> granted = titlePermissions.entrySet().stream()
                .filter(title -> player.hasPermission(title.getValue()))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        return granted.isEmpty() ? null : Map.of("unlocked", granted);
    }

    private static boolean isVanished(Player player) {
        return player.getMetadata("vanished").stream().anyMatch(MetadataValue::asBoolean);
    }
}
