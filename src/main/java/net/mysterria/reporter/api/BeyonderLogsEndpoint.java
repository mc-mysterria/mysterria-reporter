package net.mysterria.reporter.api;

import dev.ua.ikeepcalm.catwalk.bridge.annotations.BridgeEventHandler;
import dev.ua.ikeepcalm.catwalk.bridge.annotations.BridgePathParam;
import dev.ua.ikeepcalm.catwalk.bridge.source.BridgeApiResponse;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.*;
import net.mysterria.reporter.model.response.BeyonderLogsResponse;
import net.mysterria.reporter.util.FileReaderUtil;
import org.bukkit.Bukkit;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class BeyonderLogsEndpoint {
    @OpenApi(path = "/beyonder/logs/{player}/{amount}", methods = HttpMethod.GET,
            summary = "Get beyonder activity logs", tags = {"Reporter"})
    @BridgeEventHandler(requiresAuth = true, description = "Get beyonder's activity logs", logRequests = true)
    public CompletableFuture<BridgeApiResponse<BeyonderLogsResponse>> getBeyonderLogs(
            @BridgePathParam("player") String player, @BridgePathParam("amount") String amount) {
        int limit;
        try { limit = Math.clamp(Integer.parseInt(amount), 1, 10000); }
        catch (NumberFormatException invalid) {
            return CompletableFuture.completedFuture(response(player, 0, List.of()));
        }
        try {
            var coi = Bukkit.getPluginManager().getPlugin("CircleOfImagination");
            if (coi == null || !coi.isEnabled()) return unavailable();
            var reader = coi.getClass().getMethod("readPlayerActivity", String.class, int.class);
            var pending = (CompletableFuture<?>) reader.invoke(coi, FileReaderUtil.sanitizePlayerName(player), limit);
            // The COI worker sequences this read after prior writes. Neither HTTP nor Paper blocks.
            return pending.orTimeout(3, TimeUnit.SECONDS).thenApply(value -> {
                if (!(value instanceof List<?> lines) || lines.stream().anyMatch(line -> !(line instanceof String))) {
                    throw new IllegalStateException("Invalid activity response");
                }
                return response(player, limit, lines.stream().map(String.class::cast).toList());
            }).exceptionally(failure -> BridgeApiResponse.error(
                    "Activity history temporarily unavailable", HttpStatus.SERVICE_UNAVAILABLE));
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return unavailable();
        }
    }

    private static CompletableFuture<BridgeApiResponse<BeyonderLogsResponse>> unavailable() {
        return CompletableFuture.completedFuture(BridgeApiResponse.error(
                "Activity reader unavailable; coordinated COI update required", HttpStatus.SERVICE_UNAVAILABLE));
    }

    private static BridgeApiResponse<BeyonderLogsResponse> response(String player, int limit, List<String> lines) {
        return BridgeApiResponse.success(BeyonderLogsResponse.builder().player(player).requested(limit)
                .returned(lines.size()).logs(lines).found(!lines.isEmpty()).build());
    }
}