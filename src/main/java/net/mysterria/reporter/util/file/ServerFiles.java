package net.mysterria.reporter.util.file;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class ServerFiles {

    private static final Path COI_FOLDER = Path.of("plugins", "CircleOfImagination").toAbsolutePath().normalize();
    private static final Pattern SAFE_NAME = Pattern.compile("\\.?[A-Za-z0-9_-]{1,64}");

    public static boolean isSafeName(String name) {
        return name != null && SAFE_NAME.matcher(name).matches();
    }

    public static Optional<String> readCoiFile(String relativePath) {
        Path file = resolve(relativePath);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + file, e);
        }
    }

    public static Optional<List<String>> tailCoiFile(String relativePath, int lineCount) {
        Path file = resolve(relativePath);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        Deque<String> tail = new ArrayDeque<>(lineCount);
        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            lines.forEach(line -> {
                if (tail.size() == lineCount) {
                    tail.removeFirst();
                }
                tail.addLast(line);
            });
        } catch (IOException | UncheckedIOException e) {
            throw new UncheckedIOException("Failed to read " + file, e instanceof IOException io ? io : ((UncheckedIOException) e).getCause());
        }
        return Optional.of(List.copyOf(tail));
    }

    private static Path resolve(String relativePath) {
        Path file = COI_FOLDER.resolve(relativePath).normalize();
        if (!file.startsWith(COI_FOLDER)) {
            throw new IllegalArgumentException("Path escapes the CircleOfImagination folder: " + relativePath);
        }
        return file;
    }
}
