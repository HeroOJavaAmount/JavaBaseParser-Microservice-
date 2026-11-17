package ru.netology.audit;

import ru.netology.enumConst.AuditEventType;

import java.time.Instant;
import java.util.Objects;

/**
 * Одно событие с документом (создан, скачан, ошибка и т.п.).
 *
 * <p>Связь с {@link ru.netology.dto.Document} — только через {@link #documentHash()}
 * (FK, не вложенная ссылка). Сам документ здесь не хранится — он в
 * {@link ru.netology.storage.DocumentStorage}.
 *
 * <p>Иммутабелен: после создания не меняется. События только добавляются.
 */
public record AuditEvent(
        String eventId,            // UUID события
        String documentHash,       // ← FK: связь с Document.hash
        Instant timestamp,         // когда произошло
        String userId,             // кто (из X-User-Id / токена / CLI)
        String parser,             // каким парсером (rules/stream/auto)
        String documentType,       // тип (bank_statement/contract/... или null)
        AuditEventType eventType,  // PARSED, FAILED, DOWNLOADED, ...
        String details             // детали (текст ошибки, diff и т.п.) или null
) {
    public AuditEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(documentHash, "documentHash");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(eventType, "eventType");
        // userId, parser, documentType, details — могут быть null
    }

    /** Удобный фабричный метод для события «успешный парсинг». */
    public static AuditEvent parsed(String hash, String userId,
                                     String parser, String documentType) {
        return new AuditEvent(
                java.util.UUID.randomUUID().toString(),
                hash, Instant.now(), userId, parser, documentType,
                AuditEventType.PARSED, null
        );
    }

    /** Фабрика для «ошибка парсинга». */
    public static AuditEvent failed(String hash, String userId,
                                     String parser, String documentType,
                                     String errorMessage) {
        return new AuditEvent(
                java.util.UUID.randomUUID().toString(),
                hash, Instant.now(), userId, parser, documentType,
                AuditEventType.FAILED, errorMessage
        );
    }
}