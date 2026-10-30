package net.mysterria.reporter.domain.profile.service;

import lombok.RequiredArgsConstructor;
import net.mysterria.reporter.config.ReporterConfig;
import net.mysterria.reporter.domain.profile.model.SectionUpdate;
import net.mysterria.reporter.domain.profile.model.source.PushOutcome;

import java.util.*;
import java.util.logging.Logger;

@RequiredArgsConstructor
public class ProfilePushService {

    private static final long MAX_BACKOFF_MILLIS = 10 * 60_000;

    private final BackendClient client;
    private final ReporterConfig config;
    private final Logger logger;
    private final UUID worldUid;
    private final Map<String, Map<UUID, Object>> lastValues = new HashMap<>();
    private final Map<UUID, Map<String, SectionUpdate>> pending = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> pendingHolders = new LinkedHashMap<>();
    private long lastSeq;
    private long backoffMillis;
    private long nextAttemptAt;
    private boolean epochClosed;

    public synchronized void offerAll(String section, Map<UUID, Object> current) {
        boolean firstSight = !lastValues.containsKey(section);
        Map<UUID, Object> previous = lastValues.computeIfAbsent(section, key -> new HashMap<>());
        for (UUID uuid : List.copyOf(previous.keySet())) {
            if (!current.containsKey(uuid)) offer(uuid, section, null);
        }
        current.forEach((uuid, data) -> offer(uuid, section, data));
        if (firstSight && !current.isEmpty() && !epochClosed) {
            pendingHolders.put(section, Map.of("section", section, "seq", nextSeq(),
                    "holders", current.keySet().stream().map(UUID::toString).toList()));
        }
    }

    public synchronized void offer(UUID uuid, String section, Object data) {
        Map<UUID, Object> previous = lastValues.computeIfAbsent(section, key -> new HashMap<>());
        if (previous.containsKey(uuid) ? Objects.equals(previous.get(uuid), data) : data == null) return;
        if (data == null) previous.remove(uuid);
        else previous.put(uuid, data);
        if (epochClosed) return;
        pending.computeIfAbsent(uuid, key -> new HashMap<>()).put(section, new SectionUpdate(nextSeq(), data));
    }

    public void flush() {
        for (Map.Entry<String, Map<String, Object>> holders : List.copyOf(holderRequests())) {
            PushOutcome outcome = post("/api/internal/players/sections/holders", holders.getValue());
            if (outcome == PushOutcome.RETRY) return;
            settleHolders(holders);
        }
        while (true) {
            Map<UUID, Map<String, SectionUpdate>> batch = takeBatch();
            if (batch.isEmpty()) return;
            List<Map<String, Object>> players = new ArrayList<>();
            batch.forEach((uuid, sections) -> players.add(Map.of("uuid", uuid.toString(), "sections", sections)));
            PushOutcome outcome = client.post("/api/internal/players/state", Map.of("worldUid", worldUid.toString(), "players", players));
            if (!settle(batch, outcome)) return;
        }
    }

    public PushOutcome post(String path, Map<String, Object> body) {
        synchronized (this) {
            if (epochClosed) return PushOutcome.EPOCH_CLOSED;
        }
        Map<String, Object> withWorld = new HashMap<>(body);
        withWorld.put("worldUid", worldUid.toString());
        PushOutcome outcome = client.post(path, withWorld);
        if (outcome == PushOutcome.EPOCH_CLOSED) closeEpoch();
        return outcome;
    }

    public synchronized void reset() {
        lastValues.clear();
        pending.clear();
        pendingHolders.clear();
        epochClosed = false;
        backoffMillis = 0;
        nextAttemptAt = 0;
    }

    public synchronized int pendingPlayers() {
        return pending.size();
    }

    public synchronized boolean isEpochClosed() {
        return epochClosed;
    }

    private long nextSeq() {
        lastSeq = Math.max(System.currentTimeMillis(), lastSeq + 1);
        return lastSeq;
    }

    private synchronized Set<Map.Entry<String, Map<String, Object>>> holderRequests() {
        return Set.copyOf(pendingHolders.entrySet());
    }

    private synchronized void settleHolders(Map.Entry<String, Map<String, Object>> sent) {
        pendingHolders.remove(sent.getKey(), sent.getValue());
    }

    private synchronized Map<UUID, Map<String, SectionUpdate>> takeBatch() {
        if (epochClosed || System.currentTimeMillis() < nextAttemptAt) return Map.of();
        Map<UUID, Map<String, SectionUpdate>> batch = new LinkedHashMap<>();
        for (Map.Entry<UUID, Map<String, SectionUpdate>> entry : pending.entrySet()) {
            if (batch.size() >= config.getBackendBatchSize()) break;
            batch.put(entry.getKey(), Map.copyOf(entry.getValue()));
        }
        return batch;
    }

    private synchronized boolean settle(Map<UUID, Map<String, SectionUpdate>> batch, PushOutcome outcome) {
        switch (outcome) {
            case SENT, REJECTED -> {
                batch.forEach((uuid, sent) -> pending.computeIfPresent(uuid, (key, sections) -> {
                    sent.forEach(sections::remove);
                    return sections.isEmpty() ? null : sections;
                }));
                backoffMillis = 0;
                return true;
            }
            case RETRY -> {
                backoffMillis = Math.min(MAX_BACKOFF_MILLIS, Math.max(30_000, backoffMillis * 2));
                nextAttemptAt = System.currentTimeMillis() + backoffMillis;
                return false;
            }
            default -> {
                closeEpoch();
                return false;
            }
        }
    }

    private synchronized void closeEpoch() {
        if (epochClosed) return;
        epochClosed = true;
        pending.clear();
        pendingHolders.clear();
        logger.warning("The backend closed this server's epoch; profile pushes stop until /reporter backfill");
    }
}
