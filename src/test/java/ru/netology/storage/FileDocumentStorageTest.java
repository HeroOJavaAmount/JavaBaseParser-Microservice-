package ru.netology.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты {@link FileDocumentStorage}: сохранение, чтение, дедупликация.
 *
 * <p>Используется {@link TempDir} — JUnit создаёт и удаляет временную папку
 * для каждого теста. Диск реален, но изолирован.
 */
class FileDocumentStorageTest {

    @TempDir
    Path tempDir;

    private FileDocumentStorage storage;

    @BeforeEach
    void setUp() {
        storage = new FileDocumentStorage(tempDir);
    }

    /** save → findByHash возвращает тот же JSON. */
    @Test
    void save_thenFindByHash_returnsContent() {
        String hash = "abc123";
        String json = "{\"id\":\"1\"}";

        storage.save(hash, json);

        Optional<String> found = storage.findByHash(hash);
        assertTrue(found.isPresent());
        assertEquals(json, found.get());
    }

    /** exists() → true после save. */
    @Test
    void exists_returnsTrueAfterSave() {
        storage.save("h1", "content");

        assertTrue(storage.exists("h1"));
    }

    /** exists() → false для несуществующего hash. */
    @Test
    void exists_returnsFalseForUnknown() {
        assertFalse(storage.exists("unknown"));
    }

    /** findByHash для неизвестного → пустой Optional. */
    @Test
    void findByHash_unknown_returnsEmpty() {
        assertTrue(storage.findByHash("unknown").isEmpty());
    }

    /** Дедупликация: повторный save того же hash не перезаписывает файл. */
    @Test
    void save_sameHash_doesNotOverwrite() {
        storage.save("h1", "first");
        storage.save("h1", "second");

        assertEquals("first", storage.findByHash("h1").orElseThrow(),
                "второй save не должен перезаписывать");
    }

    /** save с пустым hash → IllegalArgumentException. */
    @Test
    void save_blankHash_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> storage.save("", "content"));
        assertThrows(IllegalArgumentException.class,
                () -> storage.save(null, "content"));
    }

    /** Файл создаётся с именем {@code <hash>.json}. */
    @Test
    void save_createsFileWithHashName() {
        storage.save("abc123", "content");

        Path expected = tempDir.resolve("abc123.json");
        assertTrue(Files.exists(expected), "файл должен называться <hash>.json");
    }
}