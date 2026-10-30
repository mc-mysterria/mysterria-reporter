package net.mysterria.reporter.util.file;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class StableFiles {

    private static final long SETTLE_MILLIS = 2_000;

    public static Optional<FileStamp> stamp(Path file) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
            return Optional.of(new FileStamp(attributes.lastModifiedTime().toMillis(), attributes.size()));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public static Optional<String> read(Path file, FileStamp before) {
        if (before.getSize() == 0 || System.currentTimeMillis() - before.getModifiedAt() < SETTLE_MILLIS) {
            return Optional.empty();
        }
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            return stamp(file).filter(before::equals).map(after -> content);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public static List<Path> list(Path folder, String suffix) {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(file -> file.getFileName().toString().endsWith(suffix) && Files.isRegularFile(file)).toList();
        } catch (IOException e) {
            return List.of();
        }
    }
}
