package ru.netology.serialize;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import ru.netology.dto.Block;
import ru.netology.dto.Document;
import ru.netology.dto.DocumentMetadata;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты {@link JsonDocumentSerializer}: сериализация и round-trip.
 *
 * <p>Проверяет:
 * <ul>
 *   <li>сериализованный JSON содержит schemaVersion, id, hash, blocks;</li>
 *   <li>JSON валиден (можно распарсить обратно);</li>
 *   <li>round-trip: Document → JSON → Document сохраняет данные.</li>
 * </ul>
 */
class JsonDocumentSerializerTest {

    private final JsonDocumentSerializer serializer = new JsonDocumentSerializer();

    private static Document sampleDocument() {
        return Document.of(
                "id-1",
                "abc123",
                "raw text",
                List.of(
                        Block.heading(1, "Заголовок"),
                        Block.paragraph("Абзац")
                ),
                new DocumentMetadata("author", "title", 3,
                        Instant.parse("2026-01-01T00:00:00Z"), "cert.pdf")
        );
    }

    /** JSON содержит schemaVersion и базовые поля. */
    @Test
    void serialize_containsSchemaVersionAndFields() {
        String json = serializer.serialize(sampleDocument());

        assertTrue(json.contains("\"schemaVersion\" : \"1.0\""),
                "должна быть schemaVersion 1.0, got: " + json);
        assertTrue(json.contains("\"id\" : \"id-1\""));
        assertTrue(json.contains("\"hash\" : \"abc123\""));
        assertTrue(json.contains("\"blocks\""));
    }

    /** JSON — валидный (парсится обратно без ошибок). */
    @Test
    void serialize_producesValidJson() throws Exception {
        String json = serializer.serialize(sampleDocument());

        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        assertDoesNotThrow(() -> mapper.readTree(json));
    }

    /** contentType и fileExtension — правильные. */
    @Test
    void contentType_and_fileExtension() {
        assertEquals("application/json", serializer.contentType());
        assertEquals("json", serializer.fileExtension());
    }

    /** Round-trip: Document → JSON → Document без потерь. */
    @Test
    void roundTrip_preservesDocument() throws Exception {
        Document original = sampleDocument();
        String json = serializer.serialize(original);

        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        Document back = mapper.readValue(json, Document.class);

        assertEquals(original.schemaVersion(), back.schemaVersion());
        assertEquals(original.id(), back.id());
        assertEquals(original.hash(), back.hash());
        assertEquals(original.rawText(), back.rawText());
        assertEquals(original.blocks().size(), back.blocks().size());
        assertEquals(original.metadata().pageCount(), back.metadata().pageCount());
    }
}