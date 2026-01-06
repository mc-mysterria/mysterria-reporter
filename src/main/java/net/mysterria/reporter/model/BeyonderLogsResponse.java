package net.mysterria.reporter.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

import java.util.List;

@Data
@Getter
@Builder
@AllArgsConstructor
public class BeyonderLogsResponse {

    @JsonProperty("player")
    private final String player;

    @JsonProperty("requested")
    private final int requested;

    @JsonProperty("returned")
    private final int returned;

    @JsonProperty("logs")
    private final List<String> logs;

    @JsonProperty("found")
    private final boolean found;
}
