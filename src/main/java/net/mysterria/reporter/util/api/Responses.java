package net.mysterria.reporter.util.api;

import dev.ua.ikeepcalm.catwalk.bridge.source.BridgeApiResponse;
import io.javalin.http.HttpStatus;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

public class Responses {

    public static <T> BridgeApiResponse<T> disabled() {
        return BridgeApiResponse.error("This endpoint is disabled in MysterriaReporter's config.yml", HttpStatus.NOT_FOUND);
    }

    public static <T> BridgeApiResponse<T> invalidName(String field) {
        return BridgeApiResponse.error(field + " may only contain letters, digits, '_' and '-'", HttpStatus.BAD_REQUEST);
    }

    public static <T> CompletableFuture<BridgeApiResponse<T>> ofAsync(Supplier<CompletableFuture<T>> action) {
        try {
            return action.get().handle((result, error) -> {
                if (error == null) {
                    return BridgeApiResponse.success(result);
                }
                Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                if (cause instanceof ApiException api) {
                    return BridgeApiResponse.error(api.getMessage(), api.getStatus());
                }
                throw new CompletionException(cause);
            });
        } catch (ApiException e) {
            return CompletableFuture.completedFuture(BridgeApiResponse.error(e.getMessage(), e.getStatus()));
        }
    }

    public static <T> BridgeApiResponse<T> of(Supplier<T> action) {
        try {
            return BridgeApiResponse.success(action.get());
        } catch (ApiException e) {
            return BridgeApiResponse.error(e.getMessage(), e.getStatus());
        }
    }
}
