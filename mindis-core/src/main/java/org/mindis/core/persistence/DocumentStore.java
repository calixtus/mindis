package org.mindis.core.persistence;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/// Reads and writes a {@link MinDisDocument} as one pretty-printed JSON file.
/// Writes are atomic (temp file in the target's own directory, then move), so
/// an interrupted save never truncates the user's existing document.
///
/// <p>Unlike the per-entity store this replaced, a failed read is <em>not</em>
/// swallowed into an empty result: the file is one the user picked explicitly,
/// so the caller (and through it the user) must learn that it could not be
/// opened instead of silently facing an empty parish.
public final class DocumentStore {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.INDENT_OUTPUT, true);

    public MinDisDocument read(Path file) throws IOException {
        return objectMapper.readValue(file.toFile(), MinDisDocument.class);
    }

    public void write(Path file, MinDisDocument document) throws IOException {
        Path directory = file.toAbsolutePath().getParent();
        if (directory != null) {
            Files.createDirectories(directory);
        }
        Path tempFile = file.resolveSibling(file.getFileName() + ".tmp");
        objectMapper.writeValue(tempFile.toFile(), document);
        try {
            Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
