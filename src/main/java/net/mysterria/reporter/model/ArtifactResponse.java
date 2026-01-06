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
public class ArtifactResponse {

    @JsonProperty("artifactId")
    private final String artifactId;

    @JsonProperty("data")
    private final String data;

    @JsonProperty("found")
    private final boolean found;
}
