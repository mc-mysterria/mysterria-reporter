package net.mysterria.reporter.domain.beyonder.endpoint;

import dev.ua.ikeepcalm.catwalk.bridge.annotations.BridgeEventHandler;
import dev.ua.ikeepcalm.catwalk.bridge.annotations.BridgePathParam;
import dev.ua.ikeepcalm.catwalk.bridge.annotations.BridgeRequestBody;
import dev.ua.ikeepcalm.catwalk.bridge.source.BridgeApiResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import lombok.RequiredArgsConstructor;
import net.mysterria.reporter.config.ReporterConfig;
import net.mysterria.reporter.domain.beyonder.model.BeyonderFile;
import net.mysterria.reporter.domain.beyonder.model.BeyonderLogs;
import net.mysterria.reporter.domain.beyonder.model.BlessRequest;
import net.mysterria.reporter.domain.beyonder.model.BlessResult;
import net.mysterria.reporter.domain.beyonder.model.RebirthRequest;
import net.mysterria.reporter.domain.beyonder.service.BeyonderService;
import net.mysterria.reporter.util.api.Responses;
import net.mysterria.reporter.util.file.ServerFiles;

import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
public class BeyonderEndpoints {

    private final BeyonderService beyonderService;
    private final ReporterConfig config;

    @OpenApi(path = "/beyonder/{player}", methods = HttpMethod.GET, tags = "Reporter",
            summary = "Get a beyonder's raw data file",
            pathParams = @OpenApiParam(name = "player", description = "Player name"),
            responses = {
                    @OpenApiResponse(status = "200", description = "Raw YAML", content = @OpenApiContent(from = BeyonderFile.class)),
                    @OpenApiResponse(status = "404", description = "No data file for this player")
            })
    @BridgeEventHandler(scopes = "reporter.beyonder", logRequests = true)
    public BridgeApiResponse<BeyonderFile> getBeyonderData(@BridgePathParam("player") String player) {
        if (!config.isEnabled("beyonder-data")) {
            return Responses.disabled();
        }
        if (!ServerFiles.isSafeName(player)) {
            return Responses.invalidName("player");
        }
        return Responses.of(() -> beyonderService.beyonderFile(player));
    }

    @OpenApi(path = "/beyonder/logs/{player}/{amount}", methods = HttpMethod.GET, tags = "Reporter",
            summary = "Get the last lines of a beyonder's activity log",
            pathParams = {
                    @OpenApiParam(name = "player", description = "Player name"),
                    @OpenApiParam(name = "amount", type = Integer.class, description = "Lines to return, 1-10000")
            },
            responses = {
                    @OpenApiResponse(status = "200", description = "Log lines, oldest first", content = @OpenApiContent(from = BeyonderLogs.class)),
                    @OpenApiResponse(status = "400", description = "Invalid player name or amount"),
                    @OpenApiResponse(status = "404", description = "No log for this player")
            })
    @BridgeEventHandler(scopes = "reporter.logs", logRequests = true)
    public CompletableFuture<BridgeApiResponse<BeyonderLogs>> getBeyonderLogs(@BridgePathParam("player") String player,
                                                                              @BridgePathParam("amount") int amount) {
        if (!config.isEnabled("beyonder-logs")) {
            return CompletableFuture.completedFuture(Responses.disabled());
        }
        if (!ServerFiles.isSafeName(player)) {
            return CompletableFuture.completedFuture(Responses.invalidName("player"));
        }
        return Responses.ofAsync(() -> beyonderService.logs(player, amount));
    }

    @OpenApi(path = "/bless", methods = HttpMethod.POST, tags = "Reporter",
            summary = "Bless a player",
            description = "Makes an online or offline player a Beyonder of the given pathway and sequence",
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = BlessRequest.class), required = true),
            responses = {
                    @OpenApiResponse(status = "200", description = "Blessed", content = @OpenApiContent(from = BlessResult.class)),
                    @OpenApiResponse(status = "400", description = "Invalid request"),
                    @OpenApiResponse(status = "409", description = "Player is already a Beyonder"),
                    @OpenApiResponse(status = "500", description = "CircleOfImagination refused the change")
            })
    @BridgeEventHandler(scopes = "reporter.admin", logRequests = true, mainThread = true)
    public BridgeApiResponse<BlessResult> blessPlayer(@BridgeRequestBody BlessRequest request) {
        if (!config.isEnabled("bless")) {
            return Responses.disabled();
        }
        return Responses.of(() -> beyonderService.bless(request));
    }

    @OpenApi(path = "/rebirth", methods = HttpMethod.POST, tags = "Reporter",
            summary = "Rebirth a player",
            description = "Removes Beyonder status from an online or offline player",
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RebirthRequest.class), required = true),
            responses = {
                    @OpenApiResponse(status = "200", description = "Rebirthed", content = @OpenApiContent(from = RebirthRequest.class)),
                    @OpenApiResponse(status = "400", description = "Invalid request"),
                    @OpenApiResponse(status = "409", description = "Player is not a Beyonder"),
                    @OpenApiResponse(status = "500", description = "CircleOfImagination refused the change")
            })
    @BridgeEventHandler(scopes = "reporter.admin", logRequests = true, mainThread = true)
    public BridgeApiResponse<RebirthRequest> rebirthPlayer(@BridgeRequestBody RebirthRequest request) {
        if (!config.isEnabled("rebirth")) {
            return Responses.disabled();
        }
        return Responses.of(() -> beyonderService.rebirth(request));
    }
}
