package net.mysterria.reporter.domain.profile.service;

import me.angeschossen.lands.api.LandsIntegration;
import me.angeschossen.lands.api.land.Land;
import me.angeschossen.lands.api.nation.Nation;
import me.angeschossen.lands.api.player.LandPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public class LandLookup {

    private static final Pattern COLOR_CODES = Pattern.compile("(?i)[&§]([0-9a-fk-or]|#[0-9a-f]{6})");

    private final LandsIntegration lands;

    public LandLookup(Plugin plugin) {
        this.lands = LandsIntegration.of(plugin);
    }

    public Object landOf(Player player) {
        LandPlayer landPlayer = lands.getLandPlayer(player.getUniqueId());
        if (landPlayer == null || landPlayer.getLands().isEmpty()) return null;
        UUID uuid = player.getUniqueId();
        Land land = landPlayer.getLands().stream()
                .min(Comparator.comparing((Land owned) -> !uuid.equals(owned.getOwnerUID())).thenComparing(Land::getName))
                .orElseThrow();
        Map<String, Object> entry = new HashMap<>();
        entry.put("name", land.getName());
        entry.put("role", plain(land.getDefaultArea().getRole(uuid).getName()));
        Nation nation = land.getNation();
        if (nation != null) entry.put("nation", Map.of("name", nation.getName(), "role", uuid.equals(nation.getOwnerUID()) ? "Leader" : "Member"));
        return entry;
    }

    private static String plain(String name) {
        return COLOR_CODES.matcher(name).replaceAll("");
    }
}
