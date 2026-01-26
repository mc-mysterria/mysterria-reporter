package net.mysterria.reporter.model.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.ua.ikeepcalm.catwalk.bridge.annotations.ApiProperty;
import dev.ua.ikeepcalm.catwalk.bridge.annotations.ApiSchema;
import io.javalin.openapi.JsonSchema;
import lombok.Data;

@Data
@JsonSchema
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiSchema(
        description = "Rebirth request",
        properties = {
                @ApiProperty(
                        name = "uuid",
                        type = "string",
                        description = "UUID of the player to rebirth",
                        required = true,
                        example = "550e8400-e29b-41d4-a716-446655440000"
                ),
                @ApiProperty(
                        name = "playerName",
                        type = "string",
                        description = "Name of the player to rebirth",
                        required = true,
                        example = "ikeepcalm"
                )
        }
)
public class RebirthRequest {

    @JsonProperty("uuid")
    private String uuid;

    @JsonProperty("playerName")
    private String playerName;
}
