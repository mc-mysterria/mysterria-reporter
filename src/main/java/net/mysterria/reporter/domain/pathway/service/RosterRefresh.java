package net.mysterria.reporter.domain.pathway.service;

import lombok.Data;
import net.mysterria.reporter.domain.pathway.model.BeyonderRoster;
import net.mysterria.reporter.domain.pathway.model.PlayerPathway;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Builds a new roster without Bukkit so the rules can be tested. A player CoI could not read is retried once, then
 * kept from the last known value. A roster that shrinks without a matching drop in known beyonders is not published.
 */
public final class RosterRefresh {

    private RosterRefresh() {
    }

    /** The last published roster and how many beyonders CoI knew about when it was built. */
    public record Baseline(BeyonderRoster roster, int known) {
    }

    /**
     * @param names        every player name to check
     * @param load         reads one player, off the main thread
     * @param retry        reads the failed players again (on the main thread in production); missing names stay failed
     * @param lastKnown    the last good pathway of a player, or null
     * @param baseline     the last published roster, or null when none exists yet
     * @param maxDropPercent how far the roster may shrink before it is held back; 0 or less turns the check off
     */
    public static Outcome run(List<String> names,
                              Function<String, PathwayLoad> load,
                              Function<List<String>, Map<String, PathwayLoad>> retry,
                              Function<String, PlayerPathway> lastKnown,
                              Baseline baseline,
                              int maxDropPercent,
                              String generatedAt) {
        List<PathwayLoad> results = new ArrayList<>(names.size());
        List<String> failedNames = new ArrayList<>();
        for (String name : names) {
            PathwayLoad result = load.apply(name);
            results.add(result);
            if (result.isFailed()) {
                failedNames.add(name);
            }
        }

        int recovered = 0;
        if (!failedNames.isEmpty()) {
            Map<String, PathwayLoad> retried = retry.apply(List.copyOf(failedNames));
            for (int i = 0; i < results.size(); i++) {
                PathwayLoad again = retried.get(results.get(i).getPlayerName());
                if (results.get(i).isFailed() && again != null && !again.isFailed()) {
                    results.set(i, again);
                    recovered++;
                }
            }
        }

        List<PlayerPathway> published = new ArrayList<>();
        List<PlayerPathway> fresh = new ArrayList<>();
        List<String> notBeyonders = new ArrayList<>();
        List<PathwayLoad> stillFailed = new ArrayList<>();
        int kept = 0;
        for (PathwayLoad result : results) {
            switch (result.getStatus()) {
                case LOADED -> {
                    published.add(result.getPathway());
                    fresh.add(result.getPathway());
                }
                case NOT_BEYONDER -> notBeyonders.add(result.getPlayerName());
                case FAILED -> {
                    stillFailed.add(result);
                    PlayerPathway previous = lastKnown.apply(result.getPlayerName());
                    if (previous != null && previous.isBeyonder()) {
                        published.add(previous);
                        kept++;
                    }
                }
            }
        }

        int loaded = fresh.size();
        int failed = stillFailed.size();
        int known = loaded + failed;
        int dropped = failed - kept;
        BeyonderRoster roster = new BeyonderRoster(published.size(), List.copyOf(published), generatedAt);
        String rejection = rejectionReason(roster.getAmount(), dropped, known, baseline, maxDropPercent);
        return new Outcome(roster, rejection, List.copyOf(fresh), List.copyOf(notBeyonders), List.copyOf(stillFailed),
                loaded, failedNames.size(), recovered, failed, kept, dropped, known, baseline);
    }

    /**
     * Why a roster should be held back, or null to publish it. Two rules, both using {@code maxDropPercent}: the
     * roster shrank by more than that share of the last good roster beyond what a drop in known beyonders explains,
     * or more than that share of known beyonders could not be read and had no earlier entry to keep.
     */
    public static String rejectionReason(int published, int dropped, int known, Baseline baseline, int maxDropPercent) {
        if (baseline == null || baseline.roster() == null || maxDropPercent <= 0) {
            return null;
        }
        double allowed = Math.min(maxDropPercent, 100) / 100.0;
        int previous = baseline.roster().getAmount();
        int knownDrop = Math.max(0, baseline.known() - known);
        int unexplainedShrink = previous - published - knownDrop;
        if (unexplainedShrink > previous * allowed) {
            return "the roster would shrink from " + previous + " to " + published
                    + " while known beyonders only went from " + baseline.known() + " to " + known;
        }
        if (dropped > known * allowed) {
            return dropped + " of " + known + " known beyonders could not be read and had no earlier entry";
        }
        return null;
    }

    @Data
    public static class Outcome {

        /** The roster this refresh built. Only served when {@link #isPublished()}. */
        private final BeyonderRoster roster;
        /** Why the roster was held back, or null when it can be published. */
        private final String rejection;
        /** Players read successfully in this refresh. */
        private final List<PlayerPathway> fresh;
        /** Players CoI says are not beyonders. */
        private final List<String> notBeyonders;
        /** Players that still failed after the retry. */
        private final List<PathwayLoad> failures;
        private final int loaded;
        private final int retried;
        private final int recovered;
        private final int failed;
        private final int kept;
        private final int dropped;
        private final int known;
        private final Baseline baseline;

        public boolean isPublished() {
            return rejection == null;
        }

        public boolean isClean() {
            return isPublished() && failed == 0;
        }

        public String summary(long millis) {
            String counts = "loaded " + loaded + ", failed " + failed + " (retried " + retried + ", recovered " + recovered
                    + "), kept from last time " + kept + ", dropped " + dropped + ", known beyonders " + known
                    + ", took " + millis + " ms";
            if (isPublished()) {
                return "Roster refresh: published " + roster.getAmount() + " (" + counts + ")";
            }
            return "Roster refresh held back, still serving the last good roster of " + baseline.roster().getAmount()
                    + " instead of " + roster.getAmount() + " because " + rejection + " (" + counts + ")";
        }
    }
}
