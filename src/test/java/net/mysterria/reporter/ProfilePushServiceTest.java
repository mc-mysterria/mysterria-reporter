package net.mysterria.reporter;

import com.google.gson.GsonBuilder;
import net.mysterria.reporter.config.ReporterConfig;
import net.mysterria.reporter.domain.profile.model.source.PushOutcome;
import net.mysterria.reporter.domain.profile.service.BackendClient;
import net.mysterria.reporter.domain.profile.service.ProfilePushService;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfilePushServiceTest {

    private static final ReporterConfig CONFIG = new ReporterConfig(0, 0, 0, false, false, Set.of(), true, false, "http://backend", "key", 200, 5, 60);
    private static final UUID PLAYER = UUID.randomUUID();

    @Test
    void unchangedValuesAreNotResentAndRemovedPlayersAreCleared() {
        RecordingClient client = new RecordingClient();
        ProfilePushService push = new ProfilePushService(client, CONFIG, Logger.getAnonymousLogger(), UUID.randomUUID());

        push.offerAll("order", Map.of(PLAYER, Map.of("name", "Aurora")));
        push.flush();
        push.offerAll("order", Map.of(PLAYER, Map.of("name", "Aurora")));
        push.flush();
        assertEquals(1, client.posts("/api/internal/players/state").size());
        assertEquals(1, client.posts("/api/internal/players/sections/holders").size());
        assertTrue(client.posts("/api/internal/players/sections/holders").getFirst().contains(PLAYER.toString()));

        push.offerAll("order", Map.of());
        push.flush();
        assertEquals(2, client.posts("/api/internal/players/state").size());
        assertTrue(client.posts("/api/internal/players/state").get(1).contains("\"data\":null"));
    }

    @Test
    void failedPushKeepsTheSectionsAndAClosedEpochDropsThem() {
        RecordingClient client = new RecordingClient();
        client.outcomes.add(PushOutcome.RETRY);
        ProfilePushService push = new ProfilePushService(client, CONFIG, Logger.getAnonymousLogger(), UUID.randomUUID());

        push.offer(PLAYER, "pathway", Map.of("sequence", 7));
        push.flush();
        assertEquals(1, push.pendingPlayers());

        push.reset();
        client.outcomes.add(PushOutcome.EPOCH_CLOSED);
        push.offer(PLAYER, "pathway", Map.of("sequence", 6));
        push.flush();
        assertEquals(0, push.pendingPlayers());
        assertTrue(push.isEpochClosed());
    }

    private static class RecordingClient extends BackendClient {
        private final List<String[]> bodies = new ArrayList<>();
        private final Deque<PushOutcome> outcomes = new ArrayDeque<>();

        RecordingClient() {
            super(CONFIG, Logger.getAnonymousLogger());
        }

        private List<String> posts(String path) {
            return bodies.stream().filter(post -> post[0].equals(path)).map(post -> post[1]).toList();
        }

        @Override
        public PushOutcome post(String path, Object body) {
            bodies.add(new String[]{path, new GsonBuilder().serializeNulls().create().toJson(body)});
            return outcomes.isEmpty() ? PushOutcome.SENT : outcomes.poll();
        }
    }
}
