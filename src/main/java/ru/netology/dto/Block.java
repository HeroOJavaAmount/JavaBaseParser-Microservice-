package ru.netology.dto;

import ru.netology.enumConst.BlockType;

import java.util.List;
import java.util.Objects;

/**
 * Один логический блок документа, извлечённый из PDF.
 *
 * <p>Поля {@code headingLevel} и {@code tableRows} — взаимоисключающие
 * и зависят от {@link #type()}:
 * <ul>
 *   <li>{@link BlockType#HEADING} — задан {@code headingLevel} (1..6), {@code tableRows == null}</li>
 *   <li>{@link BlockType#TABLE}   — задан {@code tableRows}, {@code headingLevel == null}</li>
 *   <li>остальные               — задан {@code text}, оба других поля {@code null}</li>
 * </ul>
 *
 * <p>Создавать удобнее через фабричные методы: {@link #heading(int, String)},
 * {@link #paragraph(String)}, {@link #listItem(String)}, {@link #table(List)},
 * {@link #unknown(String)}.
 *
 * <p>Класс иммутабельный: все поля финальные, список строк таблицы копируется.
 */
public record Block(

        BlockType type,

        String text,

        Integer headingLevel,

        List<List<String>> tableRows
) {

    /**
     * Компактный конструктор — валидация инвариантов сразу при создании.
     */
    public Block {
        Objects.requireNonNull(type, "type must not be null");

        switch (type) {
            case HEADING -> {
                if (headingLevel == null || headingLevel < 1 || headingLevel > 6) {
                    throw new IllegalArgumentException(
                            "HEADING requires headingLevel 1..6, got: " + headingLevel);
                }
                if (tableRows != null) {
                    throw new IllegalArgumentException("HEADING must not have tableRows");
                }
            }
            case TABLE -> {
                if (tableRows == null || tableRows.isEmpty()) {
                    throw new IllegalArgumentException("TABLE requires non-empty rows");
                }
                if (headingLevel != null) {
                    throw new IllegalArgumentException("TABLE must not have headingLevel");
                }
                tableRows = List.copyOf(tableRows);   // защита от внешних модификаций
            }
            case PARAGRAPH, LIST_ITEM, UNKNOWN -> {
                if (text == null) {
                    throw new IllegalArgumentException(type + " requires non-null text");
                }
                if (headingLevel != null || tableRows != null) {
                    throw new IllegalArgumentException(
                            type + " must not have headingLevel/tableRows");
                }
            }
        }
    }

    /** Заголовок уровня 1..6. */
    public static Block heading(int level, String text) {
        return new Block(BlockType.HEADING, text, level, null);
    }

    /** Абзац текста. */
    public static Block paragraph(String text) {
        return new Block(BlockType.PARAGRAPH, text, null, null);
    }

    /** Элемент списка. */
    public static Block listItem(String text) {
        return new Block(BlockType.LIST_ITEM, text, null, null);
    }

    /** Таблица: список строк, каждая строка — список ячеек. */
    public static Block table(List<List<String>> rows) {
        return new Block(BlockType.TABLE, null, null, rows);
    }

    /** Неопознанный блок (текст сохраняется на всякий случай). */
    public static Block unknown(String text) {
        return new Block(BlockType.UNKNOWN, text == null ? "" : text, null, null);
    }
}