package net.mysterria.reporter.domain.profile.service;

import com.google.gson.Gson;
import dev.ua.ikeepcalm.coi.api.CircleOfImaginationAPI;
import dev.ua.ikeepcalm.coi.api.model.BeyonderData;
import dev.ua.ikeepcalm.coi.api.model.PathwayData;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import net.mysterria.reporter.domain.profile.model.source.PushOutcome;
import net.mysterria.reporter.util.file.FileStamp;
import net.mysterria.reporter.util.file.StableFiles;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.logging.Logger;

@RequiredArgsConstructor
public class ProfileScanService {

    private static final Path PLUGINS = Path.of("plugins");
    private static final Path COI = PLUGINS.resolve("CircleOfImagination").resolve("data");
    private static final Path MAP = PLUGINS.resolve("MysterriaMap");

    private final CircleOfImaginationAPI coi;
    private final ProfilePushService push;
    private final Logger logger;
    private final Gson gson = new Gson();
    private final Map<Path, ParsedFile> parsed = new HashMap<>();
    private final Map<Path, FileStamp> postedIncursions = new HashMap<>();
    private final AtomicBoolean scanning = new AtomicBoolean();

    public void scan() {
        if (!scanning.compareAndSet(false, true)) return;
        try {
            offerFolder("pathway", COI.resolve("beyonders"), ".yml", this::pathway);
            offerFolder("honorific", COI.resolve("honorifics"), ".yml", this::honorific);
            offerFolder("church", COI.resolve("churches"), ".yml", this::church);
            offerFile("uniqueness", COI.resolve("uniquenesses.yml"), this::uniqueness);
            offerFolder("order", PLUGINS.resolve("SacredOrder").resolve("organizations"), ".yml", this::order);
            offerFolder("faction", PLUGINS.resolve("MysterriaFactions").resolve("data").resolve("players"), ".yml", this::faction);
            offerFile("marks", MAP.resolve("marks.json"), this::marks);
            offerFolder("titles", PLUGINS.resolve("MysterriaTitles").resolve("playerdata"), ".json", this::titles);
            push.flush();
            postIncursions("cosmos-incursion", PLUGINS.resolve("CosmosIncursion").resolve("event_history.json"));
            postIncursions("mysterria-map", MAP.resolve("incursion.json"));
        } finally {
            scanning.set(false);
        }
    }

    public synchronized void reset() {
        parsed.clear();
        postedIncursions.clear();
    }

    private void offerFolder(String section, Path folder, String suffix, Function<Path, Map<UUID, Object>> parser) {
        if (!folder.toFile().isDirectory()) return;
        push.offerAll(section, collect(StableFiles.list(folder, suffix), parser));
    }

    private void offerFile(String section, Path file, Function<Path, Map<UUID, Object>> parser) {
        if (!file.toFile().isFile()) return;
        push.offerAll(section, collect(List.of(file), parser));
    }

    private synchronized Map<UUID, Object> collect(List<Path> files, Function<Path, Map<UUID, Object>> parser) {
        Map<UUID, Object> section = new HashMap<>();
        for (Path file : files) {
            ParsedFile known = parsed.get(file);
            FileStamp stamp = StableFiles.stamp(file).orElse(null);
            if (stamp != null && (known == null || !known.getStamp().equals(stamp))) {
                known = parse(file, stamp, parser).orElse(known);
            }
            if (known != null) section.putAll(known.getValues());
        }
        return section;
    }

    private Optional<ParsedFile> parse(Path file, FileStamp stamp, Function<Path, Map<UUID, Object>> parser) {
        try {
            ParsedFile result = new ParsedFile(stamp, parser.apply(file));
            parsed.put(file, result);
            return Optional.of(result);
        } catch (UnstableFileException e) {
            return Optional.empty();
        } catch (RuntimeException e) {
            logger.warning("Could not read " + file + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    private Map<UUID, Object> pathway(Path file) {
        String raw = text(yaml(file), "player-id");
        if (raw == null) return Map.of();
        UUID uuid = UUID.fromString(raw);
        BeyonderData data = coi.getBeyonderData(uuid);
        if (data == null) return Map.of();
        return data.pathways().stream()
                .min(Comparator.comparingInt(PathwayData::lowestSequenceLevel))
                .<Map<UUID, Object>>map(primary -> Map.of(uuid, Map.of(
                        "name", primary.name(),
                        "sequence", primary.lowestSequenceLevel(),
                        "acting", primary.getActingPercentage())))
                .orElse(Map.of());
    }

    private Map<UUID, Object> honorific(Path file) {
        Map<?, ?> yaml = yaml(file);
        List<String> lines = strings(yaml, "lines");
        if (Boolean.TRUE.equals(yaml.get("cleared")) || lines.isEmpty()) return Map.of();
        return Map.of(uuidOf(file), Map.of("lines", lines));
    }

    private Map<UUID, Object> church(Path file) {
        Map<?, ?> yaml = yaml(file);
        String leader = text(yaml, "leader-id");
        if (leader == null) return Map.of();
        long foundedAt = longOf(section(yaml, "admin-long-overrides"), "founded-at", longOf(yaml, "founded-at", 0));
        Map<String, String> roles = new HashMap<>();
        Map<String, Long> joinedAt = new HashMap<>();
        section(yaml, "members").forEach((id, member) -> {
            Map<?, ?> fields = member instanceof Map<?, ?> map ? map : Map.of();
            roles.put(id.toString(), Objects.requireNonNullElse(text(fields, "role"), "FOLLOWER"));
            if (fields.get("joined-at") instanceof Number joined) joinedAt.put(id.toString(), joined.longValue());
        });
        section(yaml, "authorities").forEach((id, authority) -> {
            String role = authority instanceof Map<?, ?> fields ? text(fields, "role") : null;
            roles.put(id.toString(), Objects.requireNonNullElse(role, "CO_OWNER"));
        });
        roles.put(leader, "LEADER");
        Map<String, Set<String>> offices = offices(section(yaml, "sites"));
        Map<UUID, Object> church = new HashMap<>();
        roles.forEach((id, role) -> {
            Map<String, Object> entry = new HashMap<>();
            entry.put("leaderId", leader);
            entry.put("name", Objects.requireNonNullElse(text(yaml, "church-name"), ""));
            entry.put("pathway", Objects.requireNonNullElse(text(yaml, "pathway-name"), ""));
            entry.put("role", role);
            entry.put("foundedAt", foundedAt);
            entry.put("joinedAt", joinedAt.getOrDefault(id, foundedAt));
            entry.put("offices", List.copyOf(new TreeSet<>(offices.getOrDefault(id, Set.of()))));
            church.put(UUID.fromString(id), entry);
        });
        return church;
    }

    private static Map<String, Set<String>> offices(Map<?, ?> sites) {
        Map<String, Set<String>> offices = new HashMap<>();
        for (Object site : sites.values()) {
            if (!(site instanceof Map<?, ?> fields)) continue;
            String deacon = text(fields, "head-deacon-id");
            if (deacon != null) offices.computeIfAbsent(deacon, key -> new HashSet<>()).add("HEAD_DEACON");
            for (String bishop : strings(fields, "bishop-ids")) {
                offices.computeIfAbsent(bishop, key -> new HashSet<>()).add("BISHOP");
            }
        }
        return offices;
    }

    private Map<UUID, Object> uniqueness(Path file) {
        Map<UUID, Object> uniqueness = new HashMap<>();
        yaml(file).forEach((id, holder) -> {
            String type = holder instanceof Map<?, ?> fields ? text(fields, "type") : null;
            if (type == null) return;
            uniqueness.put(UUID.fromString(id.toString()), Map.of(
                    "pathway", type.toLowerCase(Locale.ROOT),
                    "acquiredAt", longOf((Map<?, ?>) holder, "acquisition-timestamp", 0)));
        });
        return uniqueness;
    }

    private Map<UUID, Object> order(Path file) {
        Map<?, ?> yaml = yaml(file);
        String leader = text(yaml, "leader");
        List<String> viceLeaders = strings(yaml, "viceLeaders");
        Map<UUID, Object> order = new HashMap<>();
        for (String member : strings(yaml, "members")) {
            String role = member.equals(leader) ? "LEADER" : viceLeaders.contains(member) ? "VICE_LEADER" : "MEMBER";
            order.put(UUID.fromString(member), Map.of(
                    "id", Objects.requireNonNullElse(text(yaml, "id"), ""),
                    "name", Objects.requireNonNullElse(text(yaml, "name"), ""),
                    "level", (int) longOf(yaml, "level", 1),
                    "role", role));
        }
        return order;
    }

    private Map<UUID, Object> faction(Path file) {
        Map<?, ?> yaml = yaml(file);
        String member = text(yaml, "member");
        if (member == null || member.isBlank()) return Map.of();
        return Map.of(uuidOf(file), Map.of("id", member, "joinedAt", longOf(yaml, "joined-at", 0)));
    }

    private Map<UUID, Object> titles(Path file) {
        Map<?, ?> json = json(file);
        Object active = json.get("activeTitle");
        Object unlocked = json.get("unlocked");
        return Map.of(uuidOf(file), Map.of(
                "active", active == null ? "" : active.toString(),
                "unlocked", unlocked instanceof List<?> list ? list.stream().map(Object::toString).sorted().toList() : List.of()));
    }

    private Map<UUID, Object> marks(Path file) {
        Map<?, ?> json = json(file);
        Map<?, ?> seasonConfig = section(section(yaml(MAP.resolve("config.yml")), "incursion"), "season");
        Object start = seasonConfig.get("start");
        String season = start == null ? "" : start.toString();
        Map<?, ?> badges = section(seasonConfig, "badges");
        Map<?, ?> balances = json.get("players") instanceof Map<?, ?> map ? map : Map.of();
        Map<?, ?> earned = json.get("earned") instanceof Map<?, ?> map ? map : Map.of();
        Set<Object> holders = new HashSet<>(balances.keySet());
        holders.addAll(earned.keySet());
        Map<UUID, Object> section = new HashMap<>();
        for (Object id : holders) {
            section.put(UUID.fromString(id.toString()), Map.of(
                    "balance", number(balances.get(id)),
                    "seasonEarned", number(earned.get(id)),
                    "badge", badge(badges, number(earned.get(id))),
                    "season", season));
        }
        return section;
    }

    private static String badge(Map<?, ?> thresholds, double earned) {
        return thresholds.entrySet().stream()
                .filter(threshold -> threshold.getValue() instanceof Number needed && earned >= needed.doubleValue())
                .max(Comparator.comparingDouble(threshold -> ((Number) threshold.getValue()).doubleValue()))
                .map(threshold -> threshold.getKey().toString().toUpperCase(Locale.ROOT))
                .orElse("NONE");
    }

    private void postIncursions(String source, Path file) {
        FileStamp stamp = StableFiles.stamp(file).orElse(null);
        if (stamp == null || stamp.equals(postedIncursions.get(file))) return;
        Map<?, ?> json;
        try {
            json = json(file);
        } catch (UnstableFileException e) {
            return;
        } catch (RuntimeException e) {
            logger.warning("Could not read " + file + ": " + e.getMessage());
            return;
        }
        List<Map<String, Object>> results = new ArrayList<>();
        if (json.get("history") instanceof List<?> history) {
            for (Object entry : history) {
                if (entry instanceof Map<?, ?> result && result.get("timestampMillis") instanceof Number endedAt) {
                    results.add(Map.of("endedAt", endedAt.longValue(), "result", result));
                }
            }
        }
        PushOutcome outcome = push.post("/api/internal/incursions", Map.of("source", source, "results", results));
        if (outcome == PushOutcome.SENT) postedIncursions.put(file, stamp);
    }

    private static Map<?, ?> yaml(Path file) {
        LoaderOptions options = new LoaderOptions();
        options.setCodePointLimit(64 * 1024 * 1024);
        Object root = new Yaml(new SafeConstructor(options)).load(read(file));
        return root instanceof Map<?, ?> map ? map : Map.of();
    }

    private static Map<?, ?> section(Map<?, ?> map, String key) {
        return map.get(key) instanceof Map<?, ?> section ? section : Map.of();
    }

    private static String text(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value == null ? null : value.toString();
    }

    private static long longOf(Map<?, ?> map, String key, long fallback) {
        return map.get(key) instanceof Number number ? number.longValue() : fallback;
    }

    private static List<String> strings(Map<?, ?> map, String key) {
        return map.get(key) instanceof List<?> list ? list.stream().map(Object::toString).toList() : List.of();
    }

    private Map<?, ?> json(Path file) {
        Map<?, ?> json = gson.fromJson(read(file), Map.class);
        return json == null ? Map.of() : json;
    }

    private static String read(Path file) {
        FileStamp stamp = StableFiles.stamp(file).orElseThrow(UnstableFileException::new);
        return StableFiles.read(file, stamp).orElseThrow(UnstableFileException::new);
    }

    private static UUID uuidOf(Path file) {
        String name = file.getFileName().toString();
        return UUID.fromString(name.substring(0, name.indexOf('.')));
    }

    private static double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0;
    }

    @Data
    private static class ParsedFile {
        private final FileStamp stamp;
        private final Map<UUID, Object> values;
    }

    private static class UnstableFileException extends RuntimeException {
    }
}
