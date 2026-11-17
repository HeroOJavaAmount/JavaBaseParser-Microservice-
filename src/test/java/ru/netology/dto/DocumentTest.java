package ru.netology.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты инвариантов {@link Document}.
 *
 * <p>Проверяет:
 * <ul>
 *   <li>фабрика {@link Document#of} ставит {@code schemaVersion = "1.0"};</li>
 *   <li>список блоков копируется в конструкторе ({@link List#copyOf});</li>
 *   <li>попытка изменить исходный список не влияет на документ.</li>
 * </ul>
 */
class DocumentTest {

    private static DocumentMetadata sampleMetadata() {
        return new DocumentMetadata("author", "title", 3, Instant.now(), "cert.pdf");
    }

    /** Фабрика {@code of(...)} ставит актуальную версию схемы "1.0". */
    @Test
    void of_setsSchemaVersion10() {
        Document doc = Document.of("id-1", "hash-1", "text", List.of(), sampleMetadata());

        assertEquals("1.0", doc.schemaVersion());
        assertEquals("id-1", doc.id());
        assertEquals("hash-1", doc.hash());
        assertEquals("text", doc.rawText());
    }

    /** Список блоков копируется — изменение исходного не влияет на документ. */
    @Test
    void blocks_areCopiedInConstructor() {
        List<Block> mutable = new ArrayList<>();
        mutable.add(Block.paragraph("one"));

        Document doc = Document.of("id", "hash", "text", mutable, sampleMetadata());
        mutable.add(Block.paragraph("two"));

        assertEquals(1, doc.blocks().size(),
                "внешнее добавление не должно попасть в Document");
    }

    /** Попытка изменить возвращённый список блоков → {@link UnsupportedOperationException}. */
    @Test
    void blocks_listIsImmutable() {
        Document doc = Document.of("id", "hash", "text", List.of(), sampleMetadata());

        assertThrows(UnsupportedOperationException.class,
                () -> doc.blocks().add(Block.paragraph("x")));
    }
}