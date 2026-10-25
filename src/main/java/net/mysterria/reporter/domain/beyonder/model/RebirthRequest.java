package net.mysterria.reporter.domain.beyonder.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.ua.ikeepcalm.catwalk.bridge.annotations.ApiProperty;
import dev.ua.ikeepcalm.catwalk.bridge.annotations.ApiSchema;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiSchema(description = "Rebirth request", properties = {
        @ApiProperty(name = "uuid", description = "UUID of the player to rebirth", required = true, example = "550e8400-e29b-41d4-a716-446655440000"),
        @ApiProperty(name = "playerName", description = "Name of the player to rebirth", required = true, example = "ikeepcalm")
})
public class RebirthRequest {

    private String uuid;
    private String playerName;
}
