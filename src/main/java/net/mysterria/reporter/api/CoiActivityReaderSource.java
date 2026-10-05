package net.mysterria.reporter.api;

import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Holds COI's PlayerActivityReader, resolved by name. Reporter has no compile-time or link-time
 * reference to the interface, so an older COI without it simply leaves the reader unresolved.
 * {@link #refresh()} touches the Bukkit services manager and must run on the main thread
 * (enable and service register/unregister events); HTTP threads only read the volatile snapshot.
 */
public final class CoiActivityReaderSource {
    public static final String READER_TYPE = "dev.ua.ikeepcalm.coi.api.PlayerActivityReader";

    private record Resolved(Object reader, Method read) {}

    private volatile Resolved resolved;
    // Kept for the HTTP thread to log: the main thread never logs on this path.
    private volatile Throwable resolveFailure;

    public boolean tracks(Class<?> service) {
        return service != null && READER_TYPE.equals(service.getName());
    }

    /** Main thread only: re-resolves the reader from the services manager. */
    public void refresh() {
        Resolved next = null;
        Throwable failure = null;
        try {
            Class<?> type = Class.forName(READER_TYPE, false, CoiActivityReaderSource.class.getClassLoader());
            Object reader = Bukkit.getServicesManager().load(type);
            if (reader != null) next = new Resolved(reader, type.getMethod("readPlayerActivity", String.class, int.class));
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            // COI is absent or predates PlayerActivityReader; requests answer 503 and log this failure.
            failure = unavailable;
        }
        resolved = next;
        resolveFailure = failure;
    }

    /** Why the last {@link #refresh()} left the reader unresolved, or null if it did not fail. */
    public Throwable resolveFailure() {
        return resolveFailure;
    }

    /** Returns null when the reader is unavailable. */
    @SuppressWarnings("unchecked")
    public CompletableFuture<List<String>> read(String player, int limit) throws ReflectiveOperationException {
        Resolved current = resolved;
        if (current == null) return null;
        Object pending = current.read().invoke(current.reader(), player, limit);
        if (pending instanceof CompletableFuture<?> future) return (CompletableFuture<List<String>>) future;
        throw new IllegalStateException("readPlayerActivity returned "
                + (pending == null ? "null" : pending.getClass().getName()) + " instead of a CompletableFuture");
    }
}
