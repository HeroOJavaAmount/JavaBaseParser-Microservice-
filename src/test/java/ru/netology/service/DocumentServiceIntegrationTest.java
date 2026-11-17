package ru.netology.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.netology.dto.Document;
import ru.netology.enumConst.BlockType;
import ru.netology.extract.PdfToText;
import ru.netology.parser.HeuristicRulesParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционный тест: настоящий PDF → Document.
 *
 * PDF генерируется программно через PDFBox во временной папке —
 * тест не зависит от файлов в репозитории. После git clone работает
 * у любого разработчика без подготовки.
 */
class DocumentServiceIntegrationTest {

    private static void createSamplePdf(Path path) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                // Заголовок: с заглавной, без точки → HEADING
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 20);
                cs.newLineAtOffset(50, 700);
                cs.showText("Test Document Heading");
                cs.endText();

                // Параграф: с точкой → PARAGRAPH
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 650);
                cs.showText("This is a test paragraph.");
                cs.endText();
            }

            doc.save(path.toFile());
        }
    }

    private static DocumentService newRealService() {
        return new DocumentService(
                new PdfDeclaredParser(new PdfToText()),
                new HeuristicRulesParser()
        );
    }

    @Test
    void parsesRealPdf_endToEnd(@TempDir Path tempDir) throws Exception {
        Path pdf = tempDir.resolve("sample.pdf");
        createSamplePdf(pdf);
        byte[] bytes = Files.readAllBytes(pdf);

        Document doc = newRealService().parse(bytes, false, "sample.pdf");

        assertNotNull(doc);
        assertEquals("1.0", doc.schemaVersion());
        assertEquals(64, doc.hash().length());

        String text = doc.rawText();
        assertTrue(text.contains("Test Document Heading"),
                "PDFBox должен извлечь заголовок, got: " + text);
        assertTrue(text.contains("This is a test paragraph"),
                "PDFBox должен извлечь параграф, got: " + text);

        assertTrue(doc.blocks().size() >= 2);
        assertTrue(doc.blocks().stream().anyMatch(b -> b.type() == BlockType.HEADING));
        assertTrue(doc.blocks().stream().anyMatch(b -> b.type() == BlockType.PARAGRAPH));

        assertEquals(1, doc.metadata().pageCount());
        assertEquals("sample.pdf", doc.metadata().sourceName());
    }
}