package ru.netology.service;

import ru.netology.extract.mojibake.MojibakeRepair;
import ru.netology.extract.ConvertByteToText;
import ru.netology.extract.PdfToText;
import ru.netology.load.DeclaredLoader;

import java.util.Objects;

/**
 * Оркестратор извлечения текста из PDF.
 *
 * <p>Объединяет слой "байты → текст" ({@link ConvertByteToText})
 * и опциональный ремонт mojibake.
 */
public class PdfDeclaredParser {

    private final ConvertByteToText text;

    /** PDFBox по умолчанию. */
    public PdfDeclaredParser() {
        this(new PdfToText());
    }

    public PdfDeclaredParser(ConvertByteToText toText) {
        this.text = Objects.requireNonNull(toText, "toText must not be null");
    }

    /**
     * Без ремонта mojibake — быстрый путь.
     */
    public String parseText(byte[] content) {
        return parseText(content, false);
    }

    /**
     * @param repairMojibake {@code true} — применить {@link MojibakeRepair};
     *                       {@code false} — вернуть как есть (быстрее)
     */
    public String parseText(byte[] content, boolean repairMojibake) {
        String result = text.convert(content);
        return repairMojibake ? MojibakeRepair.repair(result) : result;
    }

    /** Без ремонта. */
    public String parseText(DeclaredLoader loader) {
        Objects.requireNonNull(loader, "loader must not be null");
        return parseText(loader.getContent());
    }

    /** С ремонтом (если флаг {@code true}). */
    public String parseText(DeclaredLoader loader, boolean repairMojibake) {
        Objects.requireNonNull(loader, "loader must not be null");
        return parseText(loader.getContent(), repairMojibake);
    }
}