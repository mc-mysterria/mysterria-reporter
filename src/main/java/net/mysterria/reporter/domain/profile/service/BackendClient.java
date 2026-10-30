package net.mysterria.reporter.domain.profile.service;

import com.google.gson.Gson;
import dev.ua.ikeepcalm.catwalk.api.ApiResponse;
import dev.ua.ikeepcalm.catwalk.api.CatwalkAPI;
import net.mysterria.reporter.config.ReporterConfig;
import net.mysterria.reporter.domain.profile.model.source.PushOutcome;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class BackendClient {

    private final ReporterConfig config;
    private final Logger logger;
    private final Gson gson = new Gson();
    private final Map<String, Integer> lastStatus = new ConcurrentHashMap<>();

    public BackendClient(ReporterConfig config, Logger logger) {
        this.config = config;
        this.logger = logger;
        CatwalkAPI.setBearerToken(config.getBackendUrl(), config.getBackendKey());
    }

    public PushOutcome post(String path, Object body) {
        String json = gson.toJson(body);
        if (config.isBackendDryRun()) {
            logger.info("Dry run: POST " + path + " (" + json.length() + " bytes)");
            return PushOutcome.SENT;
        }
        ApiResponse response = CatwalkAPI.sendPost(config.getBackendUrl() + path, json);
        PushOutcome outcome = PushOutcome.of(response.getStatusCode());
        Integer previous = lastStatus.put(path, response.getStatusCode());
        if (outcome != PushOutcome.SENT && !Integer.valueOf(response.getStatusCode()).equals(previous)) {
            logger.warning("POST " + path + " answered " + response.getStatusCode() + ": " + response.getBody());
        }
        return outcome;
    }
}
