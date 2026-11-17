package ru.netology.serialize;

import org.junit.jupiter.api.Test;
import ru.netology.dto.Block;
import ru.netology.dto.Document;
import ru.netology.dto.DocumentMetadata;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты {@link CsvDocumentSerializer}: заголовок, строки, экранирование.
 *
 * <p>Проверяет:
 * <ul>
 *   <li>первая строка — заголовки колонок;</li>
 *   <li>по одной строке на каждый блок;</li>
 *   <li>экранирование запятых в тексте (обёртка в кавычки);</li>
 *   <li>пустой Document → только заголовок.</li>
 * </ul>
 */
class CsvDocumentSerializerTest {

    private final CsvDocumentSerializer serializer = new CsvDocumentSerializer();

    private static Document sampleDocument() {
        return Document.of(
                "id", "hash", "raw",
                List.of(
                        Block.heading(1, "Заголовок"),
                        Block.paragraph("Абзац"),
                        Block.listItem("- пункт")
                ),
                new DocumentMetadata(null, null, 1, Instant.now(), "cert.pdf")
        );
    }

    /** Первая строка — заголовки колонок. */
    @Test
    void serialize_containsHeaderRow() {
        String csv = serializer.serialize(sampleDocument());
        String header = csv.split("\\R")[0];

        assertTrue(header.contains("type"), "колонка type, got: " + header);
        assertTrue(header.contains("text"));
        assertTrue(header.contains("headingLevel"));
    }

    /** По одной строке на каждый блок (+1 строка заголовка). */
    @Test
    void serialize_containsOneRowPerBlock() {
        String csv = serializer.serialize(sampleDocument());
        String[] lines = csv.trim().split("\\R");

        assertEquals(4, lines.length, "1 заголовок + 3 блока");
    }

    /** CSV содержит данные блоков. */
    @Test
    void serialize_containsBlockData() {
        String csv = serializer.serialize(sampleDocument());

        assertTrue(csv.contains("HEADING"));
        assertTrue(csv.contains("PARAGRAPH"));
        assertTrue(csv.contains("LIST_ITEM"));
        assertTrue(csv.contains("Заголовок"));
    }

    /** Текст с запятой оборачивается в кавычки (экранирование CSV). */
    @Test
    void serialize_quotesTextWithComma() {
        Document doc = Document.of(
                "id", "hash", "raw",
                List.of(Block.paragraph("Привет, мир")),
                new DocumentMetadata(null, null, 1, Instant.now(), null)
        );

        String csv = serializer.serialize(doc);

        assertTrue(csv.contains("\"Привет, мир\""),
                "текст с запятой должен быть в кавычках, got: " + csv);
    }

    /** Пустой Document → только строка заголовка. */
    @Test
    void serialize_emptyDocument_onlyHeader() {
        Document empty = Document.of(
                "id", "hash", "raw", List.of(),
                new DocumentMetadata(null, null, 0, Instant.now(), null)
        );

        String csv = serializer.serialize(empty);
        String[] lines = csv.trim().split("\\R");

        assertEquals(1, lines.length, "только заголовок");
    }

    /** contentType и fileExtension — правильные. */
    @Test
    void contentType_and_fileExtension() {
        assertEquals("text/csv", serializer.contentType());
        assertEquals("csv", serializer.fileExtension());
    }
}