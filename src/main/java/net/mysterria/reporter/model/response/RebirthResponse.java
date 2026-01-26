package net.mysterria.reporter.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class RebirthResponse {

    @JsonProperty("player")
    private final String playerName;

    @JsonProperty("uuid")
    private final String uuid;

    @JsonProperty("success")
    private final boolean success;

    @JsonProperty("message")
    private final String message;
}
