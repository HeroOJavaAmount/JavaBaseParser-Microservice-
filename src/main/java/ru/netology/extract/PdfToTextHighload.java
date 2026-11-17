package ru.netology.extract;

import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import ru.netology.exception.DeclaredLoaderException;

import java.io.IOException;

/**
 * Высоконагруженная (Highload) реализация конвертера PDF в текст.
 *
 * <p>Использует явное управление буферами через {@link PDFParser}.
 * Подходит для обработки больших файлов внутри Docker-контейнеров с жесткими лимитами по RAM,
 * так как минимизирует пиковые нагрузки на сборщик мусора (GC).
 */
public class PdfToTextHighload implements ConvertByteToText {

    @Override
    public String convert(byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("PDF content must not be null or empty");
        }

        // Создаем эффективный буфер чтения.
        // При необходимости здесь можно настроить MaxServiceSize для сброса на диск.
        try (RandomAccessReadBuffer buffer = new RandomAccessReadBuffer(content)) {

            // Ручная инициализация парсера без избыточных оберток
            PDFParser parser = new PDFParser(buffer);

            try (PDDocument document = parser.parse()) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);

                String text = stripper.getText(document);

                // Защита от сканов и фото (отсутствие текстового слоя)
                if (text == null || text.isBlank()) {
                    throw new DeclaredLoaderException(
                            "PDF has no text layer (probably a scanned image). "
                                    + "OCR is required. Bytes: " + content.length);
                }
                return text;
            }

        } catch (IOException ex) {
            throw new DeclaredLoaderException(
                    "Highload parsing failed. Can not extract text from PDF (" + content.length + " bytes)", ex);
        }
    }
}

