package net.mysterria.reporter.util;

import dev.ua.ikeepcalm.coi.api.CircleOfImaginationAPI;
import dev.ua.ikeepcalm.coi.api.model.BeyonderData;
import dev.ua.ikeepcalm.coi.api.model.PathwayData;
import net.mysterria.reporter.model.PlayerPathwayResponse;
import org.bukkit.Bukkit;

import java.util.Map;

public class PathwayUtil {

    private PathwayUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static PlayerPathwayResponse getPlayerPathwayData(String playerName, CircleOfImaginationAPI coiAPI) {
        if (!coiAPI.isBeyonder(playerName)) {
            return createNonBeyonderResponse(playerName);
        }

        BeyonderData beyonderData = coiAPI.getBeyonderData(playerName);
        String primaryPathwayName = getPrimaryPathwayName(playerName, coiAPI);

        if (primaryPathwayName == null) {
            return createNonBeyonderResponse(playerName);
        }

        PathwayData primaryPathway = beyonderData.getPathway(primaryPathwayName);
        String acting = calculateActingProgress(primaryPathway);

        return PlayerPathwayResponse.builder()
                .playerName(playerName)
                .beyonder(true)
                .pathway(primaryPathwayName)
                .sequence(String.valueOf(primaryPathway.lowestSequenceLevel()))
                .acting(acting)
                .build();
    }

    public static String getPrimaryPathwayName(String playerName, CircleOfImaginationAPI coiAPI) {
        Map<String, Integer> pathways = coiAPI.getPathways(playerName);
        if (pathways == null || pathways.isEmpty()) {
            return null;
        }

        String primaryPathway = null;
        int lowestSequence = Integer.MAX_VALUE;

        for (Map.Entry<String, Integer> entry : pathways.entrySet()) {
            if (entry.getValue() < lowestSequence) {
                lowestSequence = entry.getValue();
                primaryPathway = entry.getKey();
            }
        }

        return primaryPathway;
    }

    private static String calculateActingProgress(PathwayData pathway) {
        double acting = pathway.acting();
        double neededActing = pathway.neededActing();

        if (neededActing == 0) {
            return "0";
        }

        return String.valueOf(acting / neededActing);
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
