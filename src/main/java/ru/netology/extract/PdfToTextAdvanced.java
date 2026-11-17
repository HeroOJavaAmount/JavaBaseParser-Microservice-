package ru.netology.extract;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import ru.netology.exception.DeclaredLoaderException;

import java.io.IOException;
import java.util.*;

/**
 * Если готовый PDFTextStripper всё равно перемешивает строки, единственный надежный способ в рамках PDFBox — переопределить
 * метод processTextPosition. Так мы сможем вручную сгруппировать текст по точным координатам Y и X.
 */
public class PdfToTextAdvanced implements ConvertByteToText {

    @Override
    public String convert(byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("PDF content must not be null or empty");
        }

        try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(content)) {
            CustomPositionalStripper stripper = new CustomPositionalStripper();
            stripper.setSortByPosition(true); // Включаем базовую сортировку
            stripper.getText(document); // Запуск парсинга

            return stripper.getReconstructedText();
        } catch (IOException ex) {
            throw new DeclaredLoaderException("Advanced parsing failed", ex);
        }
    }

    // Внутренний кастомный парсер, перехватывающий каждую букву и её координаты
    private static class CustomPositionalStripper extends PDFTextStripper {
        // Карта: Координата Y -> Список символов на этой строке
        private final Map<Float, List<TextPosition>> pageLines = new TreeMap<>();

        public CustomPositionalStripper() throws IOException {
            super();
        }

        @Override
        protected void processTextPosition(TextPosition text) {
            // Округляем координату Y до 1 знака, чтобы компенсировать микро-сдвиги шрифта
            float y = Math.round(text.getYDirAdj() * 10.0f) / 10.0f;

            pageLines.computeIfAbsent(y, k -> new ArrayList<>()).add(text);
            super.processTextPosition(text);
        }

        public String getReconstructedText() {
            StringBuilder sb = new StringBuilder();

            for (Map.Entry<Float, List<TextPosition>> entry : pageLines.entrySet()) {
                List<TextPosition> positions = entry.getValue();

                // Сортируем символы строго по горизонтали (слева направо) внутри ОДНОЙ строки Y
                positions.sort(Comparator.comparing(TextPosition::getXDirAdj));

                float lastX = -1;
                for (TextPosition tp : positions) {
                    // Если между блоками текста по горизонтали большой разрыв — добавляем Табуляцию/Разделитель
                    if (lastX != -1 && (tp.getXDirAdj() - lastX) > 15.0f) {
                        sb.append("   "); // Помогает визуально разделить колонки таблицы
                    }
                    sb.append(tp.getUnicode());
                    lastX = tp.getXDirAdj() + tp.getWidthDirAdj();
                }
                sb.append("\n"); // Конец строки
            }
            return sb.toString();
        }
    }
}
