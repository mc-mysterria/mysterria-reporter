package net.mysterria.reporter.domain.report.endpoint;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
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
import net.mysterria.reporter.domain.report.model.ArtifactFile;
import net.mysterria.reporter.util.api.Responses;
import net.mysterria.reporter.util.file.ServerFiles;

import java.util.Optional;

@RequiredArgsConstructor
public class ReportEndpoints {

    private final ReporterConfig config;

    @OpenApi(path = "/artifact/{id}", methods = HttpMethod.GET, tags = "Reporter",
            summary = "Get an artifact's raw data file",
            pathParams = @OpenApiParam(name = "id", description = "Artifact id"),
            responses = {
                    @OpenApiResponse(status = "200", description = "Raw YAML", content = @OpenApiContent(from = ArtifactFile.class)),
                    @OpenApiResponse(status = "404", description = "No artifact with this id")
            })
    @BridgeEventHandler(scopes = "reporter.read", logRequests = true)
    public BridgeApiResponse<ArtifactFile> getArtifactData(@BridgePathParam("id") String id) {
        if (!config.isEnabled("artifact")) {
            return Responses.disabled();
        }
        if (!ServerFiles.isSafeName(id)) {
            return Responses.invalidName("id");
        }
        return ServerFiles.readCoiFile("artifacts/" + id + ".yml")
                .map(data -> BridgeApiResponse.success(new ArtifactFile(id, data)))
                .orElseGet(() -> BridgeApiResponse.error("No artifact with id " + id, HttpStatus.NOT_FOUND));
    }

    @OpenApi(path = "/balance/report", methods = HttpMethod.GET, tags = "Reporter",
            summary = "Get the latest CircleOfImagination balance export",
            responses = {
                    @OpenApiResponse(status = "200", description = "The export as JSON"),
                    @OpenApiResponse(status = "404", description = "No export has been written yet"),
                    @OpenApiResponse(status = "500", description = "The export is not valid JSON")
            })
    @BridgeEventHandler(scopes = "reporter.read", logRequests = true)
    public BridgeApiResponse<JsonElement> getBalanceReport() {
        if (!config.isEnabled("balance-report")) {
            return Responses.disabled();
        }
        Optional<String> export = ServerFiles.readCoiFile("balance/export-latest.json");
        if (export.isEmpty()) {
            return BridgeApiResponse.error("No balance export has been written yet", HttpStatus.NOT_FOUND);
        }
        try {
            return BridgeApiResponse.success(JsonParser.parseString(export.get()));
        } catch (JsonParseException e) {
            return BridgeApiResponse.error("The balance export is not valid JSON", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
