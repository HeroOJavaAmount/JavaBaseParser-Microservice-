package ru.netology.extract;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import ru.netology.exception.DeclaredLoaderException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Реализация, которая разбивает PDF на логические блоки строк,
 * сохраняет их в List и затем последовательно переводит в текст.
 */
public class PdfToTextBlockParser implements ConvertByteToText {

    @Override
    public String convert(byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("PDF content must not be null or empty");
        }

        try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(content)) {
            BlockStripper stripper = new BlockStripper();

            // Включаем встроенную группировку по позициям, чтобы блоки
            // не перепутывались, если они записаны в файле в случайном порядке
            stripper.setSortByPosition(true);

            // Заставляем PDFBox обработать документ и заполнить наш List блоков
            stripper.getText(document);

            // Получаем наш List со всеми блоками/строками текста
            List<String> textBlocks = stripper.getExtractedBlocks();

            // Переводим List обратно в сплошной текст в правильном порядке
            StringBuilder finalSubstitutedText = new StringBuilder();
            for (String block : textBlocks) {
                finalSubstitutedText.append(block).append("\n");
            }

            String text = finalSubstitutedText.toString();
            if (text.isBlank()) {
                throw new DeclaredLoaderException("No text layers found in blocks.");
            }
            return text;

        } catch (IOException ex) {
            throw new DeclaredLoaderException("Block-based parsing failed", ex);
        }
    }

    /**
     * Кастомный стриппер, который вместо вывода в консоль/файл
     * нарезает текст на блоки и складывает в List<String>
     */
    private static class BlockStripper extends PDFTextStripper {
        private final List<String> extractedBlocks = new ArrayList<>();

        public BlockStripper() throws IOException {
            super();
        }

        @Override
        protected void writeString(String text, List<org.apache.pdfbox.text.TextPosition> textPositions) {
            // Метод вызывается каждый раз, когда PDFBox находит законченную строку или блок текста
            if (text != null && !text.isBlank()) {
                extractedBlocks.add(text.trim());
            }
        }

        public List<String> getExtractedBlocks() {
            return extractedBlocks;
        }
    }
}
