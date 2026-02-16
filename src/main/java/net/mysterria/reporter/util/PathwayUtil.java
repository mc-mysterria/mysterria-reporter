package net.mysterria.reporter.util;

import dev.ua.ikeepcalm.coi.api.CircleOfImaginationAPI;
import dev.ua.ikeepcalm.coi.api.model.BeyonderData;
import dev.ua.ikeepcalm.coi.api.model.PathwayData;
import net.mysterria.reporter.model.response.PlayerPathwayResponse;
import org.bukkit.Bukkit;

import java.util.Comparator;

public class PathwayUtil {

    private PathwayUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static PlayerPathwayResponse getPlayerPathwayData(String playerName, CircleOfImaginationAPI coiAPI) {
        if (!coiAPI.isBeyonder(playerName)) {
            return createNonBeyonderResponse(playerName);
        }

        // Get all data in ONE lightweight call (no full Beyonder load for offline players)
        BeyonderData beyonderData = coiAPI.getBeyonderData(playerName);
        if (beyonderData == null || beyonderData.pathways().isEmpty()) {
            return createNonBeyonderResponse(playerName);
        }

        // Find primary pathway (lowest sequence) from BeyonderData
        PathwayData primaryPathway = beyonderData.pathways().stream()
                .min(Comparator.comparingInt(PathwayData::lowestSequenceLevel))
                .orElse(null);

        if (primaryPathway == null) {
            return createNonBeyonderResponse(playerName);
        }

        // Calculate acting progress using PathwayData helper method
        String acting = String.valueOf(primaryPathway.getActingPercentage());

        return PlayerPathwayResponse.builder()
                .playerName(playerName)
                .beyonder(true)
                .pathway(primaryPathway.name())
                .sequence(String.valueOf(primaryPathway.lowestSequenceLevel()))
                .acting(acting)
                .build();
    }

    private static PlayerPathwayResponse createNonBeyonderResponse(String playerName) {
        return PlayerPathwayResponse.builder()
                .playerName(playerName)
                .beyonder(false)
                .pathway(null)
                .sequence(null)
                .acting(null)
                .build();
    }

    public static void logDebug(String message) {
        Bukkit.getLogger().info("[MysterriaReporter] " + message);
    }
}
