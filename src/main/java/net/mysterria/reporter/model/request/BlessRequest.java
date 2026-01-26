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
        description = "Bless request",
        properties = {
                @ApiProperty(
                        name = "uuid",
                        type = "string",
                        description = "UUID of the player to bless",
                        required = true,
                        example = "550e8400-e29b-41d4-a716-446655440000"
                ),
                @ApiProperty(
                        name = "playerName",
                        type = "string",
                        description = "Name of the player to bless",
                        required = true,
                        example = "ikeepcalm"
                ),
                @ApiProperty(
                        name = "pathway",
                        type = "string",
                        description = "Pathway to assign (fool, door, sun, moon, death, darkness, hermit, tyrant, visionary, hanged, demoness, emperor, tower)",
                        required = true,
                        example = "fool"
                ),
                @ApiProperty(
                        name = "sequence",
                        type = "integer",
                        description = "Starting sequence level (0-9, where 9 is lowest)",
                        required = true,
                        example = "9"
                ),
                @ApiProperty(
                        name = "spiritualityBoost",
                        type = "integer",
                        description = "Initial spirituality to grant",
                        required = false,
                        example = "200"
                ),
                @ApiProperty(
                        name = "actingBoost",
                        type = "integer",
                        description = "Initial acting points to grant",
                        required = false,
                        example = "50"
                )
        }
)
public class BlessRequest {

    @JsonProperty("uuid")
    private String uuid;

    @JsonProperty("playerName")
    private String playerName;

    @JsonProperty("pathway")
    private String pathway;

    @JsonProperty("sequence")
    private Integer sequence;

    @JsonProperty("spiritualityBoost")
    private Integer spiritualityBoost = 200;

    @JsonProperty("actingBoost")
    private Integer actingBoost = 50;
}
