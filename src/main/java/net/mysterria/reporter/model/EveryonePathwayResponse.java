package net.mysterria.reporter.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EveryonePathwayResponse {

    @JsonProperty("amount")
    private Integer amount;

    @JsonProperty("beyonders")
    private List<PlayerPathwayResponse> beyonder;

}