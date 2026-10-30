package net.mysterria.reporter.domain.profile.service;

import me.angeschossen.lands.api.LandsIntegration;
import me.angeschossen.lands.api.land.Land;
import me.angeschossen.lands.api.nation.Nation;
import me.angeschossen.lands.api.player.LandPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LandLookup {

    private final LandsIntegration lands;

    public LandLookup(Plugin plugin) {
        this.lands = LandsIntegration.of(plugin);
    }

    public Object landsOf(Player player) {
        LandPlayer landPlayer = lands.getLandPlayer(player.getUniqueId());
        if (landPlayer == null || landPlayer.getLands().isEmpty()) return null;
        List<Map<String, Object>> owned = landPlayer.getLands().stream()
                .sorted(Comparator.comparing(Land::getName))
                .map(land -> {
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("name", land.getName());
                    Nation nation = land.getNation();
                    if (nation != null) entry.put("nation", nation.getName());
                    return entry;
                })
                .toList();
        return Map.of("lands", owned);
    }
}
