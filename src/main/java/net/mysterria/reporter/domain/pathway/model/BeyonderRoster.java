package net.mysterria.reporter.domain.pathway.model;

import lombok.Data;

import java.util.List;

@Data
public class BeyonderRoster {

    private final int amount;
    private final List<PlayerPathway> beyonder;
    private final String generatedAt;
}
