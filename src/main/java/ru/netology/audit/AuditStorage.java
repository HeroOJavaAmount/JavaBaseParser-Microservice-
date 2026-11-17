package ru.netology.audit;

import java.util.List;
import java.util.Optional;

/**
 * Контракт хранилища событий аудита.
 *
 * <p>Реализации:
 * <ul>
 *   <li>{@link "FileAuditStorage"} — jsonl-файлы по дням (простой)</li>
 *   <li>(потом) JdbcAuditStorage — таблица в БД</li>
 *   <li>(потом) ElasticAuditStorage — для ELK-стека</li>
 * </ul>
 */
public interface AuditStorage {

    /** Записать событие. */
    void record(AuditEvent event);

    /** Все события по документу (история). */
    List<AuditEvent> findByDocumentHash(String hash);

    /** Все события пользователя (что он делал). */
    List<AuditEvent> findByUser(String userId);

    /** Последнее событие по документу (например, «последний парсинг»). */
    Optional<AuditEvent> findLatestByHash(String hash);
}