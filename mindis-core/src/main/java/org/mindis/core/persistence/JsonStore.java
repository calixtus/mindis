package org.mindis.core.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads and saves a list of records as one pretty-printed JSON file.
 * Writes are atomic (temp file + move). A missing file yields an empty list;
 * a corrupt file logs a warning and yields an empty list rather than crashing.
 */
public final class JsonStore<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(JsonStore.class);

    private final Path file;
    private final ObjectMapper objectMapper;
    private final TypeReference<List<T>> listType;

    public JsonStore(Path file, TypeReference<List<T>> listType) {
        this.file = file;
        this.listType = listType;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(SerializationFeature.INDENT_OUTPUT, true);
    }

    public List<T> load() {
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(file.toFile(), listType);
        } catch (IOException e) {
            LOGGER.warn("Could not read {}, starting empty", file, e);
            return List.of();
        }
    }

    public void save(List<T> items) {
        try {
            Files.createDirectories(file.getParent());
            Path tempFile = file.resolveSibling(file.getFileName() + ".tmp");
            objectMapper.writeValue(tempFile.toFile(), items);
            try {
                Files.move(tempFile, file,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save " + file, e);
        }
    }
}
