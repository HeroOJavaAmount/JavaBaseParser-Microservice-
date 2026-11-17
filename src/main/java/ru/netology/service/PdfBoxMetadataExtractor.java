package ru.netology.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import ru.netology.dto.DocumentMetadata;

import java.io.IOException;
import java.time.Instant;

/**
 * Извлечение метаданных через PDFBox.
 * используется PDFBox напрямую.
 */
public class PdfBoxMetadataExtractor implements MetadataExtractor {

    @Override
    public DocumentMetadata extract(byte[] pdfBytes, String sourceName) {
        try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
            PDDocumentInformation info = pdf.getDocumentInformation();
            return new DocumentMetadata(
                    info.getAuthor(),
                    info.getTitle(),
                    pdf.getNumberOfPages(),
                    Instant.now(),
                    sourceName
            );
        } catch (IOException ex) {
            return new DocumentMetadata(null, null, 0, Instant.now(), sourceName);
        }
    }
}