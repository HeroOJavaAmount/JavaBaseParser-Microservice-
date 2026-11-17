package ru.netology.parser;

import org.junit.jupiter.api.Test;
import ru.netology.dto.Block;
import ru.netology.enumConst.BlockType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты инвариантов {@link Block}.
 *
 * <p>Проверяет:
 * <ul>
 *   <li>фабрики создают блоки с правильным типом и полями;</li>
 *   <li>валидация в компактном конструкторе бросает {@link IllegalArgumentException}
 *       на некорректные комбинации полей;</li>
 *   <li>{@link Block#table(List)} копирует список строк (иммутабельность).</li>
 * </ul>
 */
class BlockTest {

    //  Фабрики создают корректные блоки

    /** HEADING уровня 1..6 с текстом. */
    @Test
    void heading_factory_createsValidHeading() {
        Block b = Block.heading(2, "Введение");

        assertEquals(BlockType.HEADING, b.type());
        assertEquals("Введение", b.text());
        assertEquals(2, b.headingLevel());
        assertNull(b.tableRows());
    }

    /** PARAGRAPH — только текст, без уровня и строк таблицы. */
    @Test
    void paragraph_factory_createsValidParagraph() {
        Block b = Block.paragraph("Обычный текст.");

        assertEquals(BlockType.PARAGRAPH, b.type());
        assertEquals("Обычный текст.", b.text());
        assertNull(b.headingLevel());
        assertNull(b.tableRows());
    }

    /** LIST_ITEM — элемент списка. */
    @Test
    void listItem_factory_createsValidListItem() {
        Block b = Block.listItem("- пункт");

        assertEquals(BlockType.LIST_ITEM, b.type());
        assertEquals("- пункт", b.text());
    }

    /** TABLE — строки сохраняются. */
    @Test
    void table_factory_createsValidTable() {
        List<List<String>> rows = List.of(
                List.of("A", "B"),
                List.of("1", "2")
        );

        Block b = Block.table(rows);

        assertEquals(BlockType.TABLE, b.type());
        assertNull(b.text());
        assertEquals(rows, b.tableRows());
    }

    /** UNKNOWN с null-текстом заменяется на пустую строку. */
    @Test
    void unknown_factory_replacesNullTextWithEmptyString() {
        Block b = Block.unknown(null);

        assertEquals(BlockType.UNKNOWN, b.type());
        assertEquals("", b.text());
    }

    //  Валидация: некорректные комбинации полей

    /** HEADING без уровня → исключение. */
    @Test
    void heading_withoutLevel_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new Block(BlockType.HEADING, "x", null, null));
    }

    /** HEADING с уровнем вне 1..6 → исключение. */
    @Test
    void heading_withLevelOutOfRange_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new Block(BlockType.HEADING, "x", 0, null));
        assertThrows(IllegalArgumentException.class,
                () -> new Block(BlockType.HEADING, "x", 7, null));
    }

    /** HEADING не может содержать tableRows. */
    @Test
    void heading_withTableRows_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new Block(BlockType.HEADING, "x", 1, List.of(List.of("a"))));
    }

    /** TABLE без строк → исключение. */
    @Test
    void table_withoutRows_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new Block(BlockType.TABLE, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new Block(BlockType.TABLE, null, null, List.of()));
    }

    /** PARAGRAPH без текста → исключение. */
    @Test
    void paragraph_withoutText_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new Block(BlockType.PARAGRAPH, null, null, null));
    }

    /** type == null → исключение. */
    @Test
    void nullType_throws() {
        assertThrows(NullPointerException.class,
                () -> new Block(null, "x", null, null));
    }

    //  Иммутабельность

    /** {@code table()} копирует список — внешние изменения не влияют на Block. */
    @Test
    void table_copiesRows_immunable() {
        List<List<String>> mutable = new java.util.ArrayList<>();
        mutable.add(List.of("a"));

        Block b = Block.table(mutable);
        mutable.add(List.of("b"));

        assertEquals(1, b.tableRows().size(), "внешнее добавление не должно попасть в Block");
    }
}