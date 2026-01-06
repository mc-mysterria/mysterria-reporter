package net.mysterria.reporter.api;

import dev.ua.ikeepcalm.catwalk.bridge.annotations.BridgeEventHandler;
import dev.ua.ikeepcalm.catwalk.bridge.annotations.BridgePathParam;
import dev.ua.ikeepcalm.catwalk.bridge.source.BridgeApiResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import net.mysterria.reporter.model.ArtifactResponse;
import net.mysterria.reporter.util.FileReaderUtil;

public class ArtifactEndpoint {

    @OpenApi(
            path = "/artifact/{id}",
            methods = HttpMethod.GET,
            summary = "Get artifact data file",
            description = "Returns the raw YAML data file for an artifact by its ID",
            tags = {"Reporter"}
    )
    @BridgeEventHandler(description = "Get artifact's raw data file", logRequests = true)
    public BridgeApiResponse<ArtifactResponse> getArtifactData(@BridgePathParam("id") String artifactId) {
        String sanitizedId = FileReaderUtil.sanitizeArtifactId(artifactId);
        String filePath = "plugins/CircleOfImagination/artifacts/" + sanitizedId + ".yml";

        String fileContent = FileReaderUtil.readYamlFile(filePath);

        if (fileContent == null) {
            return BridgeApiResponse.success(ArtifactResponse.builder()
                    .artifactId(artifactId)
                    .data(null)
                    .found(false)
                    .build());
        }

        return BridgeApiResponse.success(ArtifactResponse.builder()
                .artifactId(artifactId)
                .data(fileContent)
                .found(true)
                .build());
    }
}
