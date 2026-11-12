package net.mysterria.reporter.domain.pathway.service;

import lombok.Data;
import net.mysterria.reporter.domain.pathway.model.PlayerPathway;

/** One attempt to read a player's pathway. A failure is not the same as "not a beyonder". */
@Data
public class PathwayLoad {

    public enum Status { LOADED, NOT_BEYONDER, FAILED }

    private final String playerName;
    private final Status status;
    private final PlayerPathway pathway;
    private final String error;

    public static PathwayLoad loaded(PlayerPathway pathway) {
        return new PathwayLoad(pathway.getPlayerName(), Status.LOADED, pathway, null);
    }

    public static PathwayLoad notBeyonder(String playerName) {
        return new PathwayLoad(playerName, Status.NOT_BEYONDER, PlayerPathway.notBeyonder(playerName), null);
    }

    public static PathwayLoad failed(String playerName, String error) {
        return new PathwayLoad(playerName, Status.FAILED, null, error);
    }

    public boolean isFailed() {
        return status == Status.FAILED;
    }
}
