package net.mysterria.reporter.api;

import dev.ua.ikeepcalm.catwalk.bridge.annotations.BridgeEventHandler;
import dev.ua.ikeepcalm.catwalk.bridge.annotations.BridgePathParam;
import dev.ua.ikeepcalm.catwalk.bridge.source.BridgeApiResponse;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import net.mysterria.reporter.model.response.BeyonderLogsResponse;
import net.mysterria.reporter.util.FailureLogLimiter;
import net.mysterria.reporter.util.FileReaderUtil;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static net.mysterria.reporter.api.bridge.BridgeApiResponse.error;

public class BeyonderLogsEndpoint {
    private static final String ENDPOINT = "GET /beyonder/logs/{player}/{amount}";

    private final CoiActivityReaderSource source;
    // The plugin logger is thread-safe, so HTTP threads log directly without calling into Bukkit.
    // The limiter counts repeats of the same failure instead of logging every request.
    private final FailureLogLimiter limiter;

    public BeyonderLogsEndpoint(CoiActivityReaderSource source, Logger logger) {
        this.source = source;
        this.limiter = new FailureLogLimiter(logger);
    }

    @OpenApi(
            path = "/beyonder/logs/{player}/{amount}",
            methods = HttpMethod.GET,
            summary = "Get beyonder activity logs",
            description = "Returns the last N activity history lines from the Circle of Imagination activity service",
            tags = {"Reporter"}
    )
    @BridgeEventHandler(description = "Get beyonder's activity logs", logRequests = true)
    public CompletableFuture<BridgeApiResponse<BeyonderLogsResponse>> getBeyonderLogs(
            @BridgePathParam("player") String player, @BridgePathParam("amount") String amount) {
        int limit;
        try { limit = Math.clamp(Integer.parseInt(amount), 1, 10000); }
        catch (NumberFormatException invalid) {
            limiter.rejected(Level.FINE, ENDPOINT, "answered 400, amount is not an integer", invalid);
            return CompletableFuture.completedFuture(error("Invalid amount: expected an integer", HttpStatus.BAD_REQUEST));
        }
        try {
            var pending = source.read(FileReaderUtil.sanitizePlayerName(player), limit);
            if (pending == null) {
                Throwable cause = source.resolveFailure();
                if (cause == null) logFailure("answered 503, PlayerActivityReader service is not registered", null);
                else logFailure("answered 503, COI activity reader could not be resolved", cause);
                return unavailable();
            }
            // Time out this request without completing the provider's shared future.
            return pending.copy().orTimeout(3, TimeUnit.SECONDS).thenApply(lines -> {
                        limiter.success(ENDPOINT);
                        return response(player, limit, lines);
                    })
                    .exceptionally(failure -> {
                        String reason = unwrap(failure) instanceof TimeoutException
                                ? "answered 503, activity history read timed out after 3s"
                                : "answered 503, activity history read failed";
                        logFailure(reason, failure);
                        return error("Activity history temporarily unavailable", HttpStatus.SERVICE_UNAVAILABLE);
                    });
        } catch (Exception | LinkageError unavailable) {
            // ClassNotFoundException/LinkageError: COI is present but predates PlayerActivityReader.
            logFailure("answered 503, calling the COI activity reader failed", unavailable);
            return unavailable();
        }
    }

    private void logFailure(String reason, Throwable failure) {
        limiter.failure(Level.WARNING, ENDPOINT, reason, failure == null ? null : unwrap(failure));
    }

    /** Reflection and future wrappers hide the real exception; report the one underneath. */
    private static Throwable unwrap(Throwable failure) {
        while ((failure instanceof CompletionException || failure instanceof ExecutionException
                || failure instanceof InvocationTargetException) && failure.getCause() != null) {
            failure = failure.getCause();
        }
        return failure;
    }

    private static CompletableFuture<BridgeApiResponse<BeyonderLogsResponse>> unavailable() {
        return CompletableFuture.completedFuture(error(
                "Activity reader unavailable; coordinated COI update required", HttpStatus.SERVICE_UNAVAILABLE));
    }

    private static BridgeApiResponse<BeyonderLogsResponse> response(String player, int limit, List<String> lines) {
        return BridgeApiResponse.success(BeyonderLogsResponse.builder().player(player).requested(limit)
                .returned(lines.size()).logs(lines).found(!lines.isEmpty()).build());
    }
}
