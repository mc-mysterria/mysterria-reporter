package net.mysterria.reporter.api.bridge;

import io.javalin.http.HttpStatus;

/**
 * Failure response that carries its HTTP status through CatWalk v.0.8.
 *
 * <p>CatWalk's {@code BridgeApiResponse.error(message, status)} discards the status, and its bridge
 * processor then answers every failure with 400. The processor looks for a public {@code httpStatus}
 * field on a response whose simple class name is {@code BridgeApiResponse}, so this subclass keeps
 * that exact simple name and exposes the field. The field is {@code transient} because CatWalk renders
 * bodies with Gson, which skips transient fields; reflective reads by the processor still work.
 */
public class BridgeApiResponse<T> extends dev.ua.ikeepcalm.catwalk.bridge.source.BridgeApiResponse<T> {
    public final transient HttpStatus httpStatus;

    private BridgeApiResponse(String message, HttpStatus httpStatus) {
        super(false, message, null);
        this.httpStatus = httpStatus;
    }

    public static <T> dev.ua.ikeepcalm.catwalk.bridge.source.BridgeApiResponse<T> error(String message, HttpStatus status) {
        return new BridgeApiResponse<>(message, status);
    }
}
