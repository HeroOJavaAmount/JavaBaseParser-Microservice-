package ru.netology.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.netology.enumConst.AuditEventType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты {@link FileAuditStorage}: запись, поиск по hash и пользователю,
 * последнее событие, разбивка по дням.
 *
 * <p>Использует {@link TempDir} — изолированная папка на каждый тест,
 * автоудаление после.
 */
class FileAuditStorageTest {

    @TempDir
    Path tempDir;

    private FileAuditStorage storage;

    @BeforeEach
    void setUp() {
        storage = new FileAuditStorage(tempDir);
    }

    /** Утилита: событие с уникальным eventId. */
    private static AuditEvent event(String hash, String user, AuditEventType type) {
        return new AuditEvent(
                "evt-" + System.nanoTime(),
                hash,
                Instant.now(),
                user,
                "rules",
                null,
                type,
                null
        );
    }

    // ────────────────────────────────────────────────────────────
    //  Запись и поиск
    // ────────────────────────────────────────────────────────────

    /** record → findByDocumentHash возвращает все события по документу. */
    @Test
    void record_thenFindByDocumentHash_returnsAllEvents() {
        storage.record(event("hash-1", "user-a", AuditEventType.PARSED));
        storage.record(event("hash-1", "user-b", AuditEventType.DOWNLOADED));

        List<AuditEvent> events = storage.findByDocumentHash("hash-1");

        assertEquals(2, events.size());
        assertTrue(events.stream().allMatch(e -> e.documentHash().equals("hash-1")));
    }

    /** record → findByUser возвращает все события пользователя. */
    @Test
    void record_thenFindByUser_returnsUserEvents() {
        storage.record(event("hash-1", "user-a", AuditEventType.PARSED));
        storage.record(event("hash-2", "user-a", AuditEventType.PARSED));
        storage.record(event("hash-3", "user-b", AuditEventType.PARSED));

        List<AuditEvent> events = storage.findByUser("user-a");

        assertEquals(2, events.size());
        assertTrue(events.stream().allMatch(e -> e.userId().equals("user-a")));
    }

    /** Поиск по неизвестному hash → пустой список. */
    @Test
    void findByDocumentHash_unknown_returnsEmpty() {
        assertTrue(storage.findByDocumentHash("unknown").isEmpty());
    }

    /** Поиск по неизвестному user → пустой список. */
    @Test
    void findByUser_unknown_returnsEmpty() {
        assertTrue(storage.findByUser("ghost").isEmpty());
    }

    // ────────────────────────────────────────────────────────────
    //  Последнее событие
    // ────────────────────────────────────────────────────────────

    /** findLatestByHash возвращает самое свежее событие по времени. */
    @Test
    void findLatestByHash_returnsNewest() throws InterruptedException {
        AuditEvent older = event("hash-1", "user-a", AuditEventType.PARSED);
        storage.record(older);
        Thread.sleep(10);   // чтобы timestamp отличался
        AuditEvent newer = event("hash-1", "user-b", AuditEventType.REPARSED);
        storage.record(newer);

        var latest = storage.findLatestByHash("hash-1");

        assertTrue(latest.isPresent());
        assertEquals(newer.eventId(), latest.get().eventId());
    }

    /** findLatestByHash по неизвестному → empty. */
    @Test
    void findLatestByHash_unknown_returnsEmpty() {
        assertTrue(storage.findLatestByHash("unknown").isEmpty());
    }

    // ────────────────────────────────────────────────────────────
    //  Формат файлов
    // ────────────────────────────────────────────────────────────

    /** record создаёт файл с расширением .jsonl. */
    @Test
    void record_createsJsonlFile() throws Exception {
        storage.record(event("h", "u", AuditEventType.PARSED));

        try (var files = Files.list(tempDir)) {
            long jsonlCount = files.filter(p -> p.toString().endsWith(".jsonl")).count();
            assertEquals(1, jsonlCount, "должен быть один .jsonl файл");
        }
    }

    /** FAILED-событие сохраняет текст ошибки в details. */
    @Test
    void record_failedEvent_preservesDetails() {
        AuditEvent failed = new AuditEvent(
                "evt-1", "hash-x", Instant.now(), "user-z",
                "rules", null, AuditEventType.FAILED, "broken pdf");
        storage.record(failed);

        var found = storage.findByDocumentHash("hash-x");
        assertEquals(1, found.size());
        assertEquals(AuditEventType.FAILED, found.get(0).eventType());
        assertEquals("broken pdf", found.get(0).details());
    }
}