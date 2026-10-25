package net.mysterria.reporter.domain.beyonder.model;

import lombok.Data;

import java.util.List;

@Data
public class BeyonderLogs {

    private final String player;
    private final int requested;
    private final int returned;
    private final List<String> logs;
    private final boolean found;
}
