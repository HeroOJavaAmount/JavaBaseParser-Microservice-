package ru.netology.dto;

import java.util.List;

/**
 * Документ, извлечённый из PDF.
 *
 * <p>{@code schemaVersion} — версия формата JSON. При добавлении полей
 * увеличивается MINOR (1.0 → 1.1), старые поля остаются для обратной совместимости.
 *
 * <p>Пример: 1С парсит {@code blocks}, в 1.1 добавили {@code tables} —
 * старый код продолжает работать, новый использует оба.
 */
public record Document(
        String schemaVersion,
        String id,
        String hash,
        String rawText,
        List<Block> blocks,
        DocumentMetadata metadata
) {
    public Document {
        blocks = List.copyOf(blocks);
    }

    /** Создаёт документ с актуальной версией схемы. */
    public static Document of(String id, String hash, String rawText,
                              List<Block> blocks, DocumentMetadata metadata) {
        return new Document("1.0", id, hash, rawText, blocks, metadata);
    }
}