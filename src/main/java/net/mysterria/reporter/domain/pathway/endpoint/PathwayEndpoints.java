package net.mysterria.reporter.domain.pathway.endpoint;

import dev.ua.ikeepcalm.catwalk.bridge.annotations.BridgeEventHandler;
import dev.ua.ikeepcalm.catwalk.bridge.annotations.BridgePathParam;
import dev.ua.ikeepcalm.catwalk.bridge.source.BridgeApiResponse;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import lombok.RequiredArgsConstructor;
import net.mysterria.reporter.config.ReporterConfig;
import net.mysterria.reporter.domain.pathway.model.BeyonderRoster;
import net.mysterria.reporter.domain.pathway.model.PlayerPathway;
import net.mysterria.reporter.domain.pathway.service.PathwayService;
import net.mysterria.reporter.util.api.Responses;
import net.mysterria.reporter.util.file.ServerFiles;

@RequiredArgsConstructor
public class PathwayEndpoints {

    private final PathwayService pathwayService;
    private final ReporterConfig config;

    @OpenApi(path = "/pathway/single/{player}", methods = HttpMethod.GET, tags = "Reporter",
            summary = "Get a player's primary pathway",
            pathParams = @OpenApiParam(name = "player", description = "Player name"),
            responses = {
                    @OpenApiResponse(status = "200", description = "Pathway, sequence and acting; beyonder=false when the player has none",
                            content = @OpenApiContent(from = PlayerPathway.class)),
                    @OpenApiResponse(status = "400", description = "Invalid player name")
            })
    @BridgeEventHandler(scopes = "reporter.read", logRequests = true)
    public BridgeApiResponse<PlayerPathway> getPathway(@BridgePathParam("player") String player) {
        if (!config.isEnabled("player-pathway")) {
            return Responses.disabled();
        }
        if (!ServerFiles.isSafeName(player)) {
            return Responses.invalidName("player");
        }
        return BridgeApiResponse.success(pathwayService.getPlayerPathway(player));
    }

    @OpenApi(path = "/pathway/everyone", methods = HttpMethod.GET, tags = "Reporter",
            summary = "Get every beyonder's primary pathway",
            responses = {
                    @OpenApiResponse(status = "200", description = "All beyonders", content = @OpenApiContent(from = BeyonderRoster.class)),
                    @OpenApiResponse(status = "503", description = "The roster is still being built, retry shortly")
            })
    @BridgeEventHandler(scopes = "reporter.read", logRequests = true)
    public BridgeApiResponse<BeyonderRoster> getEveryonePathway() {
        if (!config.isEnabled("everyone-pathway")) {
            return Responses.disabled();
        }
        BeyonderRoster roster = pathwayService.getRoster();
        return roster != null
                ? BridgeApiResponse.success(roster)
                : BridgeApiResponse.error("The beyonder roster is still being built, retry shortly", HttpStatus.SERVICE_UNAVAILABLE);
    }
}
