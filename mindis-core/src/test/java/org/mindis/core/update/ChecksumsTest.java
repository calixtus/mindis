package org.mindis.core.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChecksumsTest {

    @TempDir
    Path tempDir;

    /// The published SHA-256 of "abc" - a known-answer test, so a broken
    /// digest cannot agree with itself and pass.
    private static final String ABC_SHA256 =
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @Test
    void computesTheKnownDigest() throws IOException {
        Path file = Files.writeString(tempDir.resolve("abc.txt"), "abc");

        assertEquals(ABC_SHA256, Checksums.sha256(file));
    }

    @Test
    void acceptsTheExpectedChecksumWhateverItsCase() throws IOException {
        Path file = Files.writeString(tempDir.resolve("abc.txt"), "abc");

        assertDoesNotThrow(() -> Checksums.verify(file, ABC_SHA256.toUpperCase(java.util.Locale.ROOT)));
    }

    @Test
    void rejectsAFileThatDoesNotMatch() throws IOException {
        Path file = Files.writeString(tempDir.resolve("abd.txt"), "abd");

        IOException failure = assertThrows(IOException.class, () -> Checksums.verify(file, ABC_SHA256));
        assertTrue(failure.getMessage().contains("Checksum mismatch"));
    }
}
