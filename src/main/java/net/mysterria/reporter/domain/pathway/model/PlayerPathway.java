package net.mysterria.reporter.domain.pathway.model;

import lombok.Data;

@Data
public class PlayerPathway {

    private final String playerName;
    private final boolean beyonder;
    private final String pathway;
    private final Integer sequence;
    private final Double acting;

    public static PlayerPathway notBeyonder(String playerName) {
        return new PlayerPathway(playerName, false, null, null, null);
    }
}
