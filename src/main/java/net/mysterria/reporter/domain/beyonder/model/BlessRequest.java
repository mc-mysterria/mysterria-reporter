package net.mysterria.reporter.domain.beyonder.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.ua.ikeepcalm.catwalk.bridge.annotations.ApiProperty;
import dev.ua.ikeepcalm.catwalk.bridge.annotations.ApiSchema;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiSchema(description = "Bless request", properties = {
        @ApiProperty(name = "uuid", description = "UUID of the player to bless", required = true, example = "550e8400-e29b-41d4-a716-446655440000"),
        @ApiProperty(name = "playerName", description = "Name of the player to bless", required = true, example = "ikeepcalm"),
        @ApiProperty(name = "pathway", description = "Pathway name as CircleOfImagination knows it", required = true, example = "fool"),
        @ApiProperty(name = "sequence", type = "integer", description = "Starting sequence, 0-9 where 9 is the lowest", required = true, example = "9"),
        @ApiProperty(name = "spiritualityBoost", type = "integer", description = "Spirituality to add, default 200", example = "200"),
        @ApiProperty(name = "actingBoost", type = "integer", description = "Primary acting to set, default 50", example = "50")
})
public class BlessRequest {

    private String uuid;
    private String playerName;
    private String pathway;
    private Integer sequence;
    private Integer spiritualityBoost;
    private Integer actingBoost;
}
