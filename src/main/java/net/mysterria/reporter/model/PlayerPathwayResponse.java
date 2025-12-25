package net.mysterria.reporter.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerPathwayResponse {

    @JsonProperty("player")
    private String playerName;

    @JsonProperty("beyonder")
    private Boolean beyonder;

    @JsonProperty("pathway")
    private String pathway;

    @JsonProperty("sequence")
    private String sequence;

    @JsonProperty("acting")
    private String acting;

}