package net.mysterria.reporter.util;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Keeps a failing endpoint from flooding the log without hiding anything.
 *
 * <p>A failure is keyed by endpoint, reason and exception class. The first occurrence of a key logs in
 * full, with the stack trace at FINE. Repeats of the same key within the window are counted, not
 * printed; when the window has ended, one summary line reports how many were suppressed, when the
 * first and last happened and, if it differed from the line already logged, the most recent message.
 * A different reason or exception class is a different key and is never suppressed. The first success
 * after service failures logs one INFO line with how long the endpoint failed and how many requests
 * failed.
 *
 * <p>There is no timer: summaries are flushed by the next failure or success, which costs one
 * timestamp comparison when nothing is due. The key map is bounded and evicts the least recently seen
 * key, flushing its summary and logging that it happened. Safe for concurrent HTTP threads; the lock
 * only guards the maps and lines are logged after it is released.
 */
public final class FailureLogLimiter {
    public static final long WINDOW_MILLIS = 60_000;
    public static final int MAX_KEYS = 256;

    private record Key(String endpoint, String reason, String exceptionClass) {}

    private static final class Entry {
        final Key key;
        final String label;
        final Level level;
        long windowStart;
        String shown;
        long suppressed;
        long firstAt;
        long lastAt;
        String lastMessage;

        Entry(Key key, String label, Level level) {
            this.key = key;
            this.label = label;
            this.level = level;
        }
    }

    private static final class Outage {
        final long since;
        long failed;

        Outage(long since) {
            this.since = since;
        }
    }

    private record Line(Level level, String endpoint, String text, Throwable stack) {}

    private final Logger logger;
    private final LongSupplier clock;
    private final long windowMillis;
    private final int maxKeys;
    private final Object lock = new Object();
    // Least recently seen first, so the eldest entry is the eviction candidate.
    private final LinkedHashMap<Key, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);
    // Written under the lock, read without it so a success costs no lock while the endpoint is healthy.
    private final Map<String, Outage> outages = new ConcurrentHashMap<>();
    // Never later than the earliest window end among keys with suppressed repeats; early is harmless.
    private volatile long nextDue = Long.MAX_VALUE;

    public FailureLogLimiter(Logger logger) {
        this(logger, System::currentTimeMillis, WINDOW_MILLIS, MAX_KEYS);
    }

    FailureLogLimiter(Logger logger, LongSupplier clock, long windowMillis, int maxKeys) {
        this.logger = logger;
        this.clock = clock;
        this.windowMillis = windowMillis;
        this.maxKeys = maxKeys;
    }

    /** A request the service could not answer: limited, and counted towards the endpoint's recovery line. */
    public void failure(Level level, String endpoint, String reason, Throwable cause) {
        record(level, endpoint, reason, cause, true);
    }

    /** A request the client got wrong: limited the same way, but it does not mark the endpoint as failing. */
    public void rejected(Level level, String endpoint, String reason, Throwable cause) {
        record(level, endpoint, reason, cause, false);
    }

    /** A request that was answered normally. Logs the recovery line if the endpoint had been failing. */
    public void success(String endpoint) {
        long now = clock.getAsLong();
        if (now < nextDue && !outages.containsKey(endpoint)) return;
        List<Line> lines = new ArrayList<>();
        synchronized (lock) {
            collectDue(now, lines);
            Outage outage = outages.remove(endpoint);
            if (outage != null) {
                // The outage is over: flush what it suppressed and forget its keys so a new one logs in full.
                for (Iterator<Entry> each = entries.values().iterator(); each.hasNext(); ) {
                    Entry entry = each.next();
                    if (!entry.key.endpoint().equals(endpoint)) continue;
                    summarize(entry, lines);
                    each.remove();
                }
                lines.add(new Line(Level.INFO, endpoint, endpoint + " recovered after " + duration(now - outage.since)
                        + ": " + outage.failed + " requests failed since " + time(outage.since), null));
                nextDue = earliestDue();
            }
        }
        emit(lines);
    }

    private void record(Level level, String endpoint, String reason, Throwable cause, boolean outage) {
        if (!logger.isLoggable(level)) return;
        long now = clock.getAsLong();
        String exceptionClass = cause == null ? "none" : cause.getClass().getName();
        String message = cause == null || cause.getMessage() == null ? "" : cause.getMessage().replaceAll("\\p{Cntrl}", " ");
        String label = endpoint + " " + reason + (cause == null ? "" : ": " + exceptionClass);
        Key key = new Key(endpoint, reason, exceptionClass);
        List<Line> lines = new ArrayList<>();
        synchronized (lock) {
            collectDue(now, lines);
            if (outage) outages.computeIfAbsent(endpoint, since -> new Outage(now)).failed++;
            Entry entry = entries.get(key);
            if (entry != null && !ended(entry, now)) {
                if (entry.suppressed++ == 0) {
                    entry.firstAt = now;
                    nextDue = Math.min(nextDue, entry.windowStart + windowMillis);
                }
                entry.lastAt = now;
                entry.lastMessage = message;
            } else {
                if (entry == null) {
                    entry = new Entry(key, label, level);
                    entries.put(key, entry);
                    evictOverflow(lines);
                } else {
                    summarize(entry, lines);
                }
                entry.windowStart = now;
                entry.shown = message;
                lines.add(new Line(level, endpoint, cause == null ? label : label + ": " + message, cause));
            }
        }
        emit(lines);
    }

    /** Flushes a summary for every key whose window ended and recomputes the next due time. Caller holds the lock. */
    private void collectDue(long now, List<Line> lines) {
        if (now < nextDue) return;
        for (Entry entry : entries.values()) {
            if (entry.suppressed > 0 && ended(entry, now)) summarize(entry, lines);
        }
        nextDue = earliestDue();
    }

    private void evictOverflow(List<Line> lines) {
        while (entries.size() > maxKeys) {
            Iterator<Entry> eldest = entries.values().iterator();
            Entry evicted = eldest.next();
            eldest.remove();
            summarize(evicted, lines);
            lines.add(new Line(Level.WARNING, evicted.key.endpoint(), "Failure log limiter is tracking more than "
                    + maxKeys + " distinct failures; dropped the least recently seen: " + evicted.label
                    + ". Its next occurrence will be logged in full again.", null));
        }
    }

    private void summarize(Entry entry, List<Line> lines) {
        if (entry.suppressed == 0) return;
        StringBuilder text = new StringBuilder(entry.label).append(": ").append(entry.suppressed)
                .append(" repeats not logged (first ").append(time(entry.firstAt))
                .append(", last ").append(time(entry.lastAt)).append(")");
        if (!entry.lastMessage.equals(entry.shown)) text.append("; most recent message: ").append(entry.lastMessage);
        lines.add(new Line(entry.level, entry.key.endpoint(), text.toString(), null));
        entry.suppressed = 0;
        entry.lastMessage = null;
    }

    private long earliestDue() {
        long due = Long.MAX_VALUE;
        for (Entry entry : entries.values()) {
            if (entry.suppressed > 0) due = Math.min(due, entry.windowStart + windowMillis);
        }
        return due;
    }

    // A clock that stepped backwards ends the window too, so a key cannot be silenced indefinitely.
    private boolean ended(Entry entry, long now) {
        return now - entry.windowStart >= windowMillis || now < entry.windowStart;
    }

    private void emit(List<Line> lines) {
        for (Line line : lines) {
            logger.log(line.level(), line.text());
            if (line.stack() != null) logger.log(Level.FINE, line.endpoint() + " stack trace", line.stack());
        }
    }

    private static String time(long millis) {
        return Instant.ofEpochMilli(millis).truncatedTo(ChronoUnit.SECONDS).toString();
    }

    private static String duration(long millis) {
        if (millis < 1000) return millis + "ms";
        long seconds = millis / 1000;
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m " + seconds % 60 + "s";
        return minutes / 60 + "h " + minutes % 60 + "m " + seconds % 60 + "s";
    }
}
