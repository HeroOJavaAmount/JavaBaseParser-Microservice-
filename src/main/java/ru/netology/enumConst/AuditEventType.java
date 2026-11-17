package ru.netology.enumConst;

/** Тип события аудита. */
public enum AuditEventType {
    PARSED,       // успешный парсинг
    FAILED,       // ошибка парсинга
    DOWNLOADED,   // скачивание результата (GET /documents/{hash})
    REPARSED,     // повторный парсинг с другим парсером
    EDITED        // редактирование (если появится)
}