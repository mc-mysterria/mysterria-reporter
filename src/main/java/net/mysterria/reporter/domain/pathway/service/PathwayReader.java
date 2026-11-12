package net.mysterria.reporter.domain.pathway.service;

import dev.ua.ikeepcalm.coi.api.CircleOfImaginationAPI;
import dev.ua.ikeepcalm.coi.api.model.BeyonderData;
import dev.ua.ikeepcalm.coi.api.model.PathwayData;
import lombok.RequiredArgsConstructor;
import net.mysterria.reporter.domain.pathway.model.BoonPathway;
import net.mysterria.reporter.domain.pathway.model.PlayerPathway;

import java.util.List;
import java.util.function.Predicate;

/**
 * Reads one player's pathway from CircleOfImagination. When CoI knows the player as a beyonder but cannot hand
 * back their data (null or an exception), the result is {@link PathwayLoad.Status#FAILED}, never "not a beyonder".
 */
@RequiredArgsConstructor
public class PathwayReader {

    private final CircleOfImaginationAPI coi;

    public PathwayLoad load(String playerName) {
        try {
            if (!coi.isBeyonder(playerName)) {
                return PathwayLoad.notBeyonder(playerName);
            }
            BeyonderData data = coi.getBeyonderData(playerName);
            if (data == null || data.pathways() == null || data.pathways().isEmpty()) {
                return PathwayLoad.failed(playerName, "CircleOfImagination knows the player as a beyonder but returned no pathway data");
            }
            return PathwayLoad.loaded(toPathway(playerName, data.pathways(), this::isBoon));
        } catch (RuntimeException e) {
            return PathwayLoad.failed(playerName, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    public boolean isBoon(String pathwayName) {
        return pathwayName != null && coi.isOuterPathway(pathwayName);
    }

    /**
     * CoI keeps the main pathway first in a beyonder's list and appends Boons (outer pathways) after it, so the main
     * pathway is the first one that is not a Boon. Picking the lowest sequence instead can land on a Boon.
     */
    public static PathwayData mainPathway(List<PathwayData> pathways, Predicate<String> isBoon) {
        return pathways.stream()
                .filter(pathway -> !isBoon.test(pathway.name()))
                .findFirst()
                .orElse(pathways.getFirst());
    }

    public static PlayerPathway toPathway(String playerName, List<PathwayData> pathways, Predicate<String> isBoon) {
        PathwayData main = mainPathway(pathways, isBoon);
        List<BoonPathway> boons = pathways.stream()
                .filter(pathway -> pathway != main && isBoon.test(pathway.name()))
                .map(pathway -> new BoonPathway(pathway.name(), pathway.lowestSequenceLevel()))
                .toList();
        return new PlayerPathway(playerName, true, main.name(), main.lowestSequenceLevel(), main.getActingPercentage(), boons);
    }
}
