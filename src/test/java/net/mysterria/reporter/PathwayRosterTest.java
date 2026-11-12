package net.mysterria.reporter;

import dev.ua.ikeepcalm.coi.api.CircleOfImaginationAPI;
import dev.ua.ikeepcalm.coi.api.model.BeyonderData;
import dev.ua.ikeepcalm.coi.api.model.PathwayData;
import net.mysterria.reporter.domain.pathway.model.BeyonderRoster;
import net.mysterria.reporter.domain.pathway.model.BoonPathway;
import net.mysterria.reporter.domain.pathway.model.PlayerPathway;
import net.mysterria.reporter.domain.pathway.service.PathwayLoad;
import net.mysterria.reporter.domain.pathway.service.PathwayReader;
import net.mysterria.reporter.domain.pathway.service.RosterRefresh;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathwayRosterTest {

    private static final Set<String> BOONS = Set.of("aeon", "secondlaw");
    private static final Function<List<String>, Map<String, PathwayLoad>> NO_RETRY = names -> Map.of();

    @Test
    void aKnownBeyonderWithoutDataIsAFailureNotANonBeyonder() {
        FakeCoi coi = new FakeCoi();
        coi.beyonders.add("Ghost");
        coi.beyonders.add("Broken");
        coi.throwing.add("Broken");

        PathwayReader reader = new PathwayReader(coi.api());
        assertEquals(PathwayLoad.Status.FAILED, reader.load("Ghost").getStatus());
        assertEquals(PathwayLoad.Status.FAILED, reader.load("Broken").getStatus());
        assertTrue(reader.load("Broken").getError().contains("ConcurrentModificationException"));
        assertEquals(PathwayLoad.Status.NOT_BEYONDER, reader.load("Mortal").getStatus());
    }

    @Test
    void theMainPathwayWinsOverABoonWithALowerSequence() {
        FakeCoi coi = new FakeCoi();
        coi.add("Klein", pathway("fool", 7), pathway("aeon", 2));
        coi.add("Amon", pathway("secondlaw", 1), pathway("error", 4));

        PathwayReader reader = new PathwayReader(coi.api());
        PlayerPathway klein = reader.load("Klein").getPathway();
        assertEquals("fool", klein.getPathway());
        assertEquals(7, klein.getSequence());
        assertEquals(List.of(new BoonPathway("aeon", 2)), klein.getBoons());

        PlayerPathway amon = reader.load("Amon").getPathway();
        assertEquals("error", amon.getPathway());
        assertEquals(4, amon.getSequence());
        assertEquals(List.of(new BoonPathway("secondlaw", 1)), amon.getBoons());
    }

    @Test
    void aFailedPlayerIsRetriedThenKeptFromLastTime() {
        FakeCoi coi = new FakeCoi();
        coi.add("Fixed", pathway("door", 5));
        coi.add("Kept", pathway("sun", 0));
        coi.add("New", pathway("tyrant", 9));
        coi.add("Fine", pathway("hermit", 8));
        coi.throwing.addAll(Set.of("Fixed", "Kept", "New"));
        PathwayReader reader = new PathwayReader(coi.api());
        Map<String, PlayerPathway> lastKnown = Map.of("Kept", new PlayerPathway("Kept", true, "sun", 0, 1.0, List.of()));

        List<String> retried = new ArrayList<>();
        RosterRefresh.Outcome outcome = RosterRefresh.run(List.of("Fixed", "Kept", "New", "Fine", "Mortal"), reader::load,
                names -> {
                    retried.addAll(names);
                    return Map.of("Fixed", PathwayLoad.loaded(new PlayerPathway("Fixed", true, "door", 5, 0.0, List.of())));
                },
                lastKnown::get, null, 15, "now");

        assertEquals(List.of("Fixed", "Kept", "New"), retried);
        assertTrue(outcome.isPublished());
        assertEquals(List.of("Fixed", "Kept", "Fine"), names(outcome.getRoster()));
        assertEquals(3, outcome.getRoster().getAmount());
        assertEquals(2, outcome.getLoaded());
        assertEquals(1, outcome.getRecovered());
        assertEquals(2, outcome.getFailed());
        assertEquals(1, outcome.getKept());
        assertEquals(1, outcome.getDropped());
        assertEquals(4, outcome.getKnown());
        assertEquals(List.of("Mortal"), outcome.getNotBeyonders());
        String summary = outcome.summary(5);
        assertTrue(summary.startsWith("Roster refresh: published 3 (loaded 2, failed 2"), summary);
        assertTrue(summary.contains("kept from last time 1"), summary);
    }

    @Test
    void aShrunkenRosterIsHeldBack() {
        FakeCoi coi = new FakeCoi();
        List<String> names = IntStream.range(0, 100).mapToObj(i -> "P" + i).toList();
        names.forEach(name -> coi.add(name, pathway("fool", 8)));
        PathwayReader reader = new PathwayReader(coi.api());
        RosterRefresh.Outcome first = RosterRefresh.run(names, reader::load, NO_RETRY, name -> null, null, 15, "t1");
        assertTrue(first.isPublished());
        RosterRefresh.Baseline baseline = new RosterRefresh.Baseline(first.getRoster(), first.getKnown());

        names.subList(0, 30).forEach(coi.throwing::add);
        RosterRefresh.Outcome second = RosterRefresh.run(names, reader::load, NO_RETRY, name -> null, baseline, 15, "t2");
        assertFalse(second.isPublished());
        assertEquals(70, second.getRoster().getAmount());
        assertTrue(second.summary(1).contains("still serving the last good roster of 100 instead of 70"), second.summary(1));

        Map<String, PlayerPathway> lastKnown = new HashMap<>();
        first.getFresh().forEach(pathway -> lastKnown.put(pathway.getPlayerName(), pathway));
        RosterRefresh.Outcome third = RosterRefresh.run(names, reader::load, NO_RETRY, lastKnown::get, baseline, 15, "t3");
        assertTrue(third.isPublished());
        assertEquals(100, third.getRoster().getAmount());
        assertEquals(30, third.getKept());
    }

    @Test
    void theShrinkCheckFollowsTheKnownBeyonderCount() {
        RosterRefresh.Baseline baseline = new RosterRefresh.Baseline(new BeyonderRoster(100, List.of(), "t1"), 100);
        // CoI itself knows fewer beyonders (for example after a wipe): publish
        assertNull(rejection(50, 0, 0, baseline, 15));
        // a small shrink within the limit: publish
        assertNull(rejection(86, 0, 14, baseline, 15));
        // failures covered by last known values: publish
        assertNull(rejection(60, 40, 0, baseline, 15));
        // too many unreadable players with nothing to fall back on: hold back
        assertTrue(rejection(84, 0, 16, baseline, 15).contains("would shrink from 100 to 84"));
        // the shrink rule on its own: 100 published last time, 70 now, known beyonders only fell from 100 to 95
        assertTrue(RosterRefresh.rejectionReason(70, 0, 95, baseline, 15).contains("would shrink from 100 to 70"));
        assertNull(RosterRefresh.rejectionReason(70, 0, 75, baseline, 15));
        // no earlier roster, or the check turned off: publish whatever was read
        assertNull(rejection(10, 0, 90, null, 15));
        assertNull(rejection(10, 0, 90, baseline, 0));
    }

    /** Runs a refresh where {@code loaded} players read fine, {@code kept} fail but have a last known value and {@code dropped} fail without one. */
    private static String rejection(int loaded, int kept, int dropped, RosterRefresh.Baseline baseline, int maxDrop) {
        List<String> names = new ArrayList<>();
        Map<String, PathwayLoad> loads = new HashMap<>();
        Map<String, PlayerPathway> lastKnown = new HashMap<>();
        for (int i = 0; i < loaded + kept + dropped; i++) {
            String name = "P" + i;
            names.add(name);
            PlayerPathway pathway = new PlayerPathway(name, true, "fool", 8, 0.0, List.of());
            if (i < loaded) {
                loads.put(name, PathwayLoad.loaded(pathway));
            } else {
                loads.put(name, PathwayLoad.failed(name, "boom"));
                if (i < loaded + kept) {
                    lastKnown.put(name, pathway);
                }
            }
        }
        RosterRefresh.Outcome outcome = RosterRefresh.run(names, loads::get, NO_RETRY, lastKnown::get, baseline, maxDrop, "t");
        assertEquals(loaded + kept, outcome.getRoster().getAmount());
        return outcome.getRejection();
    }

    private static List<String> names(BeyonderRoster roster) {
        return roster.getBeyonder().stream().map(PlayerPathway::getPlayerName).toList();
    }

    private static PathwayData pathway(String name, int sequence) {
        return new PathwayData(name, 100, sequence, 50, 100, false, List.of());
    }

    /** A CircleOfImaginationAPI with only the calls the reporter makes. */
    private static class FakeCoi {
        final Set<String> beyonders = new java.util.HashSet<>();
        final Set<String> throwing = new java.util.HashSet<>();
        final Map<String, List<PathwayData>> data = new HashMap<>();

        void add(String name, PathwayData... pathways) {
            beyonders.add(name);
            data.put(name, List.of(pathways));
        }

        CircleOfImaginationAPI api() {
            return (CircleOfImaginationAPI) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{CircleOfImaginationAPI.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "isBeyonder" -> beyonders.contains((String) args[0]);
                        case "isOuterPathway" -> BOONS.contains((String) args[0]);
                        case "getBeyonderData" -> {
                            String name = (String) args[0];
                            if (throwing.contains(name)) {
                                throw new java.util.ConcurrentModificationException("simulated CoI race");
                            }
                            List<PathwayData> pathways = data.get(name);
                            yield pathways == null ? null : new BeyonderData(UUID.randomUUID(), name, 0, 0, 20, 20,
                                    pathways.stream().mapToInt(PathwayData::lowestSequenceLevel).min().orElse(9),
                                    false, false, false, 0, 0, pathways, List.of());
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }
}
