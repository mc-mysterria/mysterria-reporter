package net.mysterria.reporter.domain.pathway.model;

import lombok.Data;

import java.util.List;

@Data
public class PlayerPathway {

    private final String playerName;
    private final boolean beyonder;
    /** The main Pathway, never a Boon. */
    private final String pathway;
    private final Integer sequence;
    private final Double acting;
    /** Boon (outer) pathways the player also holds, empty when none. */
    private final List<BoonPathway> boons;

    public static PlayerPathway notBeyonder(String playerName) {
        return new PlayerPathway(playerName, false, null, null, null, List.of());
    }
}
