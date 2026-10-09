package net.mysterria.reporter.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

public class FileReaderUtil {

    // Captured at startup so HTTP request threads never call into Bukkit to log.
    private static volatile Logger logger = Logger.getLogger("MysterriaReporter");

    private FileReaderUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** Main thread, during enable: routes file-read diagnostics through the plugin logger. */
    public static void init(Logger pluginLogger) {
        if (pluginLogger != null) logger = pluginLogger;
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
                logger.warning("File not found: " + filePath);
                return null;
            }

            return Files.readString(filePath);
        } catch (IOException e) {
            logger.severe("Error reading file: " + relativePath);
            e.printStackTrace();
            return null;
        }
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
