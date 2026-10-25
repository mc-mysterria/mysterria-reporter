package net.mysterria.reporter.domain.beyonder.model;

import lombok.Data;

@Data
public class BlessResult {

    private final String playerName;
    private final String uuid;
    private final String pathway;
    private final Integer sequence;
    private final Integer spiritualityGranted;
    private final Integer actingGranted;
}
