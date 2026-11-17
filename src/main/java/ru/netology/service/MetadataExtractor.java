package ru.netology.service;

import ru.netology.dto.DocumentMetadata;

/**
 * Извлекает метаданные из PDF (автор, заголовок, число страниц).
 * Отдельный интерфейс — чтобы не тащить PDFBox в тесты DocumentService.
 */
public interface MetadataExtractor {

    /**
     * @param pdfBytes    байты PDF
     * @param sourceName  имя файла (для метаданных)
     * @return метаданные; не {@code null} (при ошибке — пустые поля)
     */
    DocumentMetadata extract(byte[] pdfBytes, String sourceName);
}