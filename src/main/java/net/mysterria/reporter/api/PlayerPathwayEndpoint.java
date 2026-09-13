package net.mysterria.reporter.api;

import dev.ua.ikeepcalm.catwalk.bridge.annotations.BridgeEventHandler;
import dev.ua.ikeepcalm.catwalk.bridge.annotations.BridgePathParam;
import dev.ua.ikeepcalm.catwalk.bridge.source.BridgeApiResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiParam;
import net.mysterria.reporter.manager.PathwayCacheManager;
import net.mysterria.reporter.model.response.PlayerPathwayResponse;

public class PlayerPathwayEndpoint {

    private final PathwayCacheManager cacheManager;

    public PlayerPathwayEndpoint(PathwayCacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @OpenApi(
            path = "/pathway/single/{player}",
            methods = HttpMethod.GET,
            summary = "Get player pathway",
            description = "Reports player pathway, sequence and acting progress by player name",
            tags = {"Reporter"},
            pathParams = {@OpenApiParam(name = "player", type = String.class, description = "Minecraft player name", required = true)}
    )
    @BridgeEventHandler(description = "Get player's primary pathway", logRequests = true)
    public BridgeApiResponse<PlayerPathwayResponse> getPathway(@BridgePathParam("player") String player) {
        PlayerPathwayResponse response = cacheManager.getPlayerPathway(player);
        return BridgeApiResponse.success(response);
    }
}

