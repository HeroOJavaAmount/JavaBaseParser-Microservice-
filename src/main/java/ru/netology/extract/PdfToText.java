package ru.netology.extract;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import ru.netology.exception.DeclaredLoaderException;

import java.io.IOException;

/**
 * Извлекает текст из PDF-документа с помощью Apache PDFBox.
 *
 * <p>Класс только <b>читает</b> PDF и возвращает текст. Никакого парсинга
 * транзакций здесь нет — это задача следующего звена (StatementParser).
 *
 * <p><b>Ограничение:</b> если PDF — это <i>скан</i> (картинка без текстового
 * слоя), метод бросит {@link DeclaredLoaderException} с явным сообщением.
 * Для сканов нужен OCR (Tesseract, EasyOCR и т.п.) — это отдельный шаг,
 * которого в текущем коде нет.
 */
public class PdfToText implements ConvertByteToText {

    @Override
    public String convert(byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("PDF content must not be null or empty");
        }

        try (PDDocument document = Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            /** Тонкая настройка PDFTextStripper
             * 1. setSortByPosition(boolean):
             * 	• true: PDFBox берет все текстовые блоки страницы, геометрически сортирует их сверху-вниз, слева-направо и пытается выстроить в линии по координате Y.
             * 	• false: Читает блоки строго в том порядке, в котором их записал генератор PDF (потоком). Попробуйте поменять этот флаг на false. Как ни странно, для колоночных документов false часто работает лучше, так как PDFBox пытается читать текст по так называемым "статьям" (articles/beads), не пересекая границы колонок по горизонтали.
             * 2. setSpacingTolerance(float) (По умолчанию 0.5f): Регулирует, какое расстояние между буквами считать пробелом. Если текст разбит на блоки с большими отступами, увеличение этого значения склеит их в одну строку, а уменьшение — разобьет на отдельные части.
             * Вариант 2: Вот пример реализации PdfToTextAdvanced, который вы можете добавить в интерфейс DeclaredToText.Если готовый PDFTextStripper всё равно перемешивает строки, единственный надежный способ в рамках PDFBox — переопределить метод processTextPosition. Так мы сможем вручную сгруппировать текст по точным координатам Y и X.
             * Вариант 3: Использование специализированных библиотек под таблицы (Tabula-Java)
             * Вариант 4: сделать правильно разбиение по блокам (List) PdfToTextBlockParser
             */

            String text = stripper.getText(document);

            // PDF без текстового слоя (скан/фото) — это НЕ пустая выписка,
            // а ситуация, которую вызывающий код должен явно обработать.
            if (text == null || text.isBlank()) {
                throw new DeclaredLoaderException(
                        "PDF has no text layer (probably a scanned image). "
                                + "OCR is required. Bytes: " + content.length);
            }
            return text;

        } catch (IOException ex) {
            throw new DeclaredLoaderException(
                    "Can not extract text from PDF (" + content.length + " bytes)", ex);
        }
    }
}