package ru.netology.service;

import org.junit.jupiter.api.Test;
import ru.netology.dto.Block;
import ru.netology.dto.Document;
import ru.netology.dto.DocumentMetadata;
import ru.netology.extract.ConvertByteToText;
import ru.netology.parser.BlockParser;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты оркестрации {@link DocumentService}.
 *
 * <p>Используются фейковые реализации {@link ConvertByteToText},
 * {@link BlockParser} и {@link MetadataExtractor} — без PDFBox, без диска.
 * Проверяется только логика склейки: hash, schemaVersion, проброс sourceName,
 * порядок вызовов.
 *
 * <p>Это unit-тест: миллисекунды, никаких зависимостей от I/O.
 */
class DocumentServiceTest {

    private static final byte[] ANY_BYTES = "fake pdf bytes".getBytes(StandardCharsets.UTF_8);

    // ────────────────────────────────────────────────────────────
    //  Фейки — свои реализации интерфейсов, ничего не делают по-настоящему
    // ────────────────────────────────────────────────────────────

    /** Возвращает заранее заданный текст. */
    private static ConvertByteToText fakeConverter(String text) {
        return bytes -> text;
    }

    /** Возвращает заранее заданные блоки. */
    private static BlockParser fakeParser(List<Block> blocks) {
        return text -> blocks;
    }

    /** Возвращает заранее заданные метаданные (sourceName пробрасывается). */
    private static MetadataExtractor fakeMetadata(int pages) {
        return (bytes, sourceName) -> new DocumentMetadata(
                "Test Author", "Test Title", pages, Instant.now(), sourceName
        );
    }

    /** Собирает сервис на фейках. */
    private static DocumentService newService(String text, List<Block> blocks, int pages) {
        PdfDeclaredParser pdfParser = new PdfDeclaredParser(fakeConverter(text));
        return new DocumentService(pdfParser, fakeParser(blocks), fakeMetadata(pages));
    }

    // ────────────────────────────────────────────────────────────
    //  Контракт результата
    // ────────────────────────────────────────────────────────────

    /** parse возвращает Document с актуальной schemaVersion. */
    @Test
    void parse_returnsDocumentWithSchemaVersion() {
        DocumentService service = newService("text", List.of(Block.paragraph("text")), 3);

        Document doc = service.parse(ANY_BYTES, false, "cert.pdf");

        assertEquals("1.0", doc.schemaVersion());
    }

    /** id и hash не null, hash = SHA-256 в hex (64 символа). */
    @Test
    void parse_generatesIdAndValidSha256Hash() {
        DocumentService service = newService("text", List.of(), 1);

        Document doc = service.parse(ANY_BYTES, false, null);

        assertNotNull(doc.id());
        assertNotNull(doc.hash());
        assertEquals(64, doc.hash().length(), "SHA-256 = 64 hex-символа");
        assertTrue(doc.hash().matches("[0-9a-f]{64}"), "hash — hex lowercase");
    }

    /** Один и тот же вход → одинаковый hash, но разные id. */
    @Test
    void parse_sameBytes_sameHash_differentIds() {
        DocumentService service = newService("text", List.of(), 1);

        Document a = service.parse(ANY_BYTES, false, "a.pdf");
        Document b = service.parse(ANY_BYTES, false, "b.pdf");

        assertEquals(a.hash(), b.hash(), "одинаковые байты → одинаковый hash");
        assertNotEquals(a.id(), b.id(), "каждый parse() — новый UUID");
    }

    /** sourceName пробрасывается в metadata.sourceName. */
    @Test
    void parse_propagatesSourceNameToMetadata() {
        DocumentService service = newService("text", List.of(), 1);

        Document doc = service.parse(ANY_BYTES, false, "certificate.pdf");

        assertEquals("certificate.pdf", doc.metadata().sourceName());
    }

    /** pageCount из metadataExtractor попадает в Document.metadata. */
    @Test
    void parse_propagatesPageCountFromExtractor() {
        DocumentService service = newService("text", List.of(), 5);

        Document doc = service.parse(ANY_BYTES, false, "x.pdf");

        assertEquals(5, doc.metadata().pageCount());
    }

    /** text из converter попадает в rawText. */
    @Test
    void parse_propagatesTextFromConverter() {
        DocumentService service = newService("Привет, мир", List.of(), 1);

        Document doc = service.parse(ANY_BYTES, false, null);

        assertEquals("Привет, мир", doc.rawText());
    }

    /** Блоки из BlockParser попадают в Document.blocks. */
    @Test
    void parse_propagatesBlocksFromParser() {
        List<Block> blocks = List.of(
                Block.heading(1, "H"),
                Block.paragraph("p")
        );
        DocumentService service = newService("text", blocks, 1);

        Document doc = service.parse(ANY_BYTES, false, null);

        assertEquals(2, doc.blocks().size());
        assertEquals(blocks, doc.blocks());
    }

    /** Null loader → NullPointerException (fail fast). */
    @Test
    void parse_nullLoader_throws() {
        DocumentService service = newService("text", List.of(), 1);

        assertThrows(NullPointerException.class,
                () -> service.parse((ru.netology.load.DeclaredLoader) null, false));
    }
}