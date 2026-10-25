package net.mysterria.reporter;

import net.mysterria.reporter.util.file.ServerFiles;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerFilesTest {

    @Test
    void namesThatCouldLeaveTheFolderAreRejected() {
        assertTrue(ServerFiles.isSafeName("ikeepcalm"));
        assertTrue(ServerFiles.isSafeName(".BedrockPlayer"));
        assertFalse(ServerFiles.isSafeName("../server"));
        assertFalse(ServerFiles.isSafeName("a/b"));
        assertFalse(ServerFiles.isSafeName(".."));
        assertFalse(ServerFiles.isSafeName(""));
        assertThrows(IllegalArgumentException.class, () -> ServerFiles.readCoiFile("../../server.properties"));
    }

    @Test
    void tailReturnsTheLastLinesInOrder() throws Exception {
        Path logs = Path.of("plugins", "CircleOfImagination", "logs");
        Files.createDirectories(logs);
        Files.write(logs.resolve("steve.log"), IntStream.rangeClosed(1, 50).mapToObj(i -> "line " + i).toList());

        assertEquals(List.of("line 48", "line 49", "line 50"), ServerFiles.tailCoiFile("logs/steve.log", 3).orElseThrow());
        assertTrue(ServerFiles.tailCoiFile("logs/missing.log", 3).isEmpty());
    }
}
