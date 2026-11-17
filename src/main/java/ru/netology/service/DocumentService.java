package ru.netology.service;

import ru.netology.dto.Block;
import ru.netology.dto.Document;
import ru.netology.dto.DocumentMetadata;
import ru.netology.load.DeclaredLoader;
import ru.netology.parser.BlockParser;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Оркестратор полного пайплайна: PDF-байты → {@link Document}.
 *
 * <p>Склеивает:
 * <ul>
 *   <li>{@link PdfDeclaredParser} — байты → текст (с опц. ремонтом mojibake);</li>
 *   <li>{@link BlockParser} — текст → блоки;</li>
 *   <li>SHA-256 от исходных байтов (дедупликация);</li>
 *   <li>{@link MetadataExtractor} — метаданные PDF (author/title/pages).</li>
 * </ul>
 *
 * <p>Зависимости приходят через конструктор (DI). В тестах подставляются
 * фейки — без PDFBox и без диска.
 */
public class DocumentService {

    private final PdfDeclaredParser pdfParser;
    private final BlockParser blockParser;
    private final MetadataExtractor metadataExtractor;

    /**
     * Боевой конструктор: PDFBox для метаданных.
     */
    public DocumentService(PdfDeclaredParser pdfParser, BlockParser blockParser) {
        this(pdfParser, blockParser, new PdfBoxMetadataExtractor());
    }

    /**
     * Конструктор с кастомным {@link MetadataExtractor} — для тестов.
     */
    public DocumentService(PdfDeclaredParser pdfParser,
                           BlockParser blockParser,
                           MetadataExtractor metadataExtractor) {
        this.pdfParser = Objects.requireNonNull(pdfParser, "pdfParser");
        this.blockParser = Objects.requireNonNull(blockParser, "blockParser");
        this.metadataExtractor = Objects.requireNonNull(metadataExtractor, "metadataExtractor");
    }


    //  Без ремонта mojibake — быстрый путь

    public Document parse(DeclaredLoader loader) {
        return parse(loader, false, null);
    }

    public Document parse(byte[] pdfBytes) {
        return parse(pdfBytes, false, null);
    }

    //  С флагом, без sourceName

    public Document parse(DeclaredLoader loader, boolean repairMojibake) {
        return parse(loader, repairMojibake, null);
    }

    public Document parse(byte[] pdfBytes, boolean repairMojibake) {
        return parse(pdfBytes, repairMojibake, null);
    }

    //  Основной метод — с флагом и sourceName

    public Document parse(DeclaredLoader loader, boolean repairMojibake, String sourceName) {
        Objects.requireNonNull(loader, "loader must not be null");
        return parse(loader.getContent(), repairMojibake, sourceName);
    }

    public Document parse(byte[] pdfBytes, boolean repairMojibake, String sourceName) {
        Objects.requireNonNull(pdfBytes, "pdfBytes must not be null");

        String text = pdfParser.parseText(pdfBytes, repairMojibake);
        List<Block> blocks = blockParser.parse(text);
        String hash = sha256(pdfBytes);
        DocumentMetadata metadata = metadataExtractor.extract(pdfBytes, sourceName);

        return Document.of(
                UUID.randomUUID().toString(),
                hash,
                text,
                blocks,
                metadata
        );
    }

    //  SHA-256

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(bytes);
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}