package net.mysterria.reporter.api;

import dev.ua.ikeepcalm.catwalk.bridge.annotations.BridgeEventHandler;
import dev.ua.ikeepcalm.catwalk.bridge.annotations.BridgePathParam;
import dev.ua.ikeepcalm.catwalk.bridge.source.BridgeApiResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import net.mysterria.reporter.model.BeyonderDataResponse;
import net.mysterria.reporter.util.FileReaderUtil;

public class BeyonderDataEndpoint {

    @OpenApi(
            path = "/beyonder/{player}",
            methods = HttpMethod.GET,
            summary = "Get beyonder data file",
            description = "Returns the raw YAML data file for a beyonder player",
            tags = {"Reporter"}
    )
    @BridgeEventHandler(description = "Get beyonder's raw data file", logRequests = true)
    public BridgeApiResponse<BeyonderDataResponse> getBeyonderData(@BridgePathParam("player") String playerName) {
        String sanitizedName = FileReaderUtil.sanitizePlayerName(playerName);
        String filePath = "plugins/CircleOfImagination/data/beyonders/" + sanitizedName + ".yml";

        String fileContent = FileReaderUtil.readYamlFile(filePath);

        if (fileContent == null) {
            return BridgeApiResponse.success(BeyonderDataResponse.builder()
                    .player(playerName)
                    .data(null)
                    .found(false)
                    .build());
        }

        return BridgeApiResponse.success(BeyonderDataResponse.builder()
                .player(playerName)
                .data(fileContent)
                .found(true)
                .build());
    }
}
