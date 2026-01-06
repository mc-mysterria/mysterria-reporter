package net.mysterria.reporter.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Data
@Getter
@Builder
@AllArgsConstructor
public class BeyonderDataResponse {

    @JsonProperty("player")
    private final String player;

    @JsonProperty("data")
    private final String data;

    @JsonProperty("found")
    private final boolean found;
}
