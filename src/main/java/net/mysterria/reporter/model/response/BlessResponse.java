package net.mysterria.reporter.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class BlessResponse {

    @JsonProperty("player")
    private final String playerName;

    @JsonProperty("uuid")
    private final String uuid;

    @JsonProperty("success")
    private final boolean success;

    @JsonProperty("message")
    private final String message;

    @JsonProperty("pathway")
    private final String pathway;

    @JsonProperty("sequence")
    private final Integer sequence;

    @JsonProperty("spirituality_granted")
    private final Integer spiritualityGranted;

    @JsonProperty("acting_granted")
    private final Integer actingGranted;
}
