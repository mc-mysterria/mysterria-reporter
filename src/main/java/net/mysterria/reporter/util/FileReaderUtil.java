package net.mysterria.reporter.util;

import org.bukkit.Bukkit;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class FileReaderUtil {

    private FileReaderUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Reads a YAML file and returns its contents as a string
     * @param relativePath Path relative to server root (e.g., "plugins/CircleOfImagination/data/beyonders/player.yml")
     * @return File contents as string, or null if file doesn't exist or error occurs
     */
    public static String readYamlFile(String relativePath) {
        try {
            Path serverRoot = Paths.get("").toAbsolutePath();
            Path filePath = serverRoot.resolve(relativePath);
            File file = filePath.toFile();

            if (!file.exists() || !file.isFile()) {
                Bukkit.getLogger().warning("[MysterriaReporter] File not found: " + filePath);
                return null;
            }

            return Files.readString(filePath);
        } catch (IOException e) {
            Bukkit.getLogger().severe("[MysterriaReporter] Error reading file: " + relativePath);
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Reads the last N lines from a log file
     * @param relativePath Path relative to server root (e.g., "plugins/CircleOfImagination/logs/player.log")
     * @param lineCount Number of lines to read from the end
     * @return List of last N lines, or empty list if file doesn't exist or error occurs
     */
    public static List<String> readLastLines(String relativePath, int lineCount) {
        List<String> result = new ArrayList<>();

        try {
            Path serverRoot = Paths.get("").toAbsolutePath();
            Path filePath = serverRoot.resolve(relativePath);
            File file = filePath.toFile();

            if (!file.exists() || !file.isFile()) {
                Bukkit.getLogger().warning("[MysterriaReporter] Log file not found: " + filePath);
                return result;
            }

            // Read all lines first (for simplicity, could optimize for very large files)
            List<String> allLines = Files.readAllLines(filePath);

            // Get last N lines
            int startIndex = Math.max(0, allLines.size() - lineCount);
            result = allLines.subList(startIndex, allLines.size());

        } catch (IOException e) {
            Bukkit.getLogger().severe("[MysterriaReporter] Error reading log file: " + relativePath);
            e.printStackTrace();
        }

        return result;
    }

    /**
     * Sanitizes a player name for file path usage
     * @param playerName Player name to sanitize
     * @return Sanitized player name safe for file paths
     */
    public static String sanitizePlayerName(String playerName) {
        // Remove any path traversal attempts and dangerous characters
        return playerName.replaceAll("[^a-zA-Z0-9_-]", "");
    }

    /**
     * Sanitizes an artifact ID for file path usage
     * @param artifactId Artifact ID to sanitize
     * @return Sanitized artifact ID safe for file paths
     */
    public static String sanitizeArtifactId(String artifactId) {
        // Remove any path traversal attempts and dangerous characters
        return artifactId.replaceAll("[^a-zA-Z0-9_-]", "");
    }
}
