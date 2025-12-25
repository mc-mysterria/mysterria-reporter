package net.mysterria.reporter.api;

import dev.ua.ikeepcalm.catwalk.bridge.annotations.BridgeEventHandler;
import dev.ua.ikeepcalm.catwalk.bridge.source.BridgeApiResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import net.mysterria.reporter.manager.PathwayCacheManager;
import net.mysterria.reporter.model.EveryonePathwayResponse;

public class EveryonePathwayEndpoint {

    private final PathwayCacheManager cacheManager;

    public EveryonePathwayEndpoint(PathwayCacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @OpenApi(
            path = "/pathway/everyone",
            methods = HttpMethod.GET,
            summary = "Get all beyonders pathways",
            description = "Reports pathway, sequence and acting progress for all beyonders",
            tags = {"Reporter"}
    )
    @BridgeEventHandler(requiresAuth = false, description = "Get all beyonders primary pathways", logRequests = true)
    public BridgeApiResponse<EveryonePathwayResponse> getEveryonePathway() {
        EveryonePathwayResponse response = cacheManager.getEveryonePathways();
        return BridgeApiResponse.success(response);
    }
}