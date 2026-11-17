package ru.netology.petach;

import ru.netology.audit.AuditEvent;
import ru.netology.audit.AuditStorage;
import ru.netology.dto.Document;
import ru.netology.exception.PdfParserException;
import ru.netology.load.DeclaredLoadFile;
import ru.netology.serialize.DocumentSerializer;
import ru.netology.service.DocumentService;
import ru.netology.storage.DocumentStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class BatchCommand {

    private final DocumentService service;
    private final DocumentSerializer serializer;
    private final DocumentStorage storage;
    private final AuditStorage audit;
    private final boolean repairMojibake;

    public BatchCommand(DocumentService service,
                        DocumentSerializer serializer,
                        DocumentStorage storage,
                        AuditStorage audit,
                        boolean repairMojibake) {
        this.service = service;
        this.serializer = serializer;
        this.storage = storage;
        this.audit = audit;
        this.repairMojibake = repairMojibake;
    }

    public int run(String[] args, String userId, String parserName) {
        if (args.length < 1) {
            System.err.println("Usage: batch <inDir>");
            return 2;
        }
        Path inDir = Path.of(args[0]);
        if (!Files.isDirectory(inDir)) {
            System.err.println("Not a directory: " + inDir);
            return 2;
        }

        List<Path> pdfs = listPdfs(inDir);
        System.out.printf("Found %d PDF files in %s%n", pdfs.size(), inDir);

        int success = 0;
        List<String> failed = new ArrayList<>();

        for (Path pdf : pdfs) {
            long start = System.currentTimeMillis();
            try {
                Document doc = service.parse(
                        new DeclaredLoadFile(pdf.toString()),
                        repairMojibake,
                        pdf.getFileName().toString()   // ← sourceName
                );
                String json = serializer.serialize(doc);
                storage.save(doc.hash(), json);
                audit.record(AuditEvent.parsed(doc.hash(), userId, parserName, null));

                long ms = System.currentTimeMillis() - start;
                System.out.printf("  OK  %s → %s (%d blocks, %d ms)%n",
                        pdf.getFileName(), doc.hash().substring(0, 12),
                        doc.blocks().size(), ms);
                success++;
            } catch (PdfParserException ex) {
                failed.add(pdf.getFileName() + ": " + ex.getMessage());
                audit.record(AuditEvent.failed("unknown", userId, parserName, null, ex.getMessage()));
                System.err.printf("  FAIL %s: %s%n", pdf.getFileName(), ex.getMessage());
            }
        }

        System.out.printf("%n=== Done: %d OK, %d failed ===%n", success, failed.size());
        return failed.isEmpty() ? 0 : 1;
    }

    private List<Path> listPdfs(Path dir) {
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.toString().toLowerCase().endsWith(".pdf"))
                    .sorted()
                    .toList();
        } catch (IOException ex) {
            throw new RuntimeException("Can not list " + dir, ex);
        }
    }
}