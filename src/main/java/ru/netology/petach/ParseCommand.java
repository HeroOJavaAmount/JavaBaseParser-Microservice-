package ru.netology.petach;

import ru.netology.audit.AuditEvent;
import ru.netology.audit.AuditStorage;
import ru.netology.dto.Document;
import ru.netology.exception.PdfParserException;
import ru.netology.load.DeclaredLoadFile;
import ru.netology.serialize.DocumentSerializer;
import ru.netology.service.DocumentService;
import ru.netology.storage.DocumentStorage;

import java.nio.file.Files;
import java.nio.file.Path;

public class ParseCommand {

    private final DocumentService service;
    private final DocumentSerializer serializer;
    private final DocumentStorage storage;
    private final AuditStorage audit;
    private final Path outDir;
    private final boolean repairMojibake;

    public ParseCommand(DocumentService service,
                        DocumentSerializer serializer,
                        DocumentStorage storage,
                        AuditStorage audit,
                        Path outDir,
                        boolean repairMojibake) {
        this.service = service;
        this.serializer = serializer;
        this.storage = storage;
        this.audit = audit;
        this.outDir = outDir;
        this.repairMojibake = repairMojibake;
    }

    public int run(String[] args, String userId, String parserName) {
        if (args.length < 1) {
            System.err.println("Usage: parse <file.pdf> [output.json]");
            return 2;
        }
        Path input = Path.of(args[0]);
        if (!Files.exists(input)) {
            System.err.println("File not found: " + input);
            return 2;
        }

        try {
            // ★ передаём sourceName третьим аргументом
            Document doc = service.parse(
                    new DeclaredLoadFile(input.toString()),
                    repairMojibake,
                    input.getFileName().toString()
            );

            String json = serializer.serialize(doc);

            if (args.length >= 2) {
                Files.writeString(Path.of(args[1]), json);
                System.out.println("Saved to " + args[1]);
            } else {
                storage.save(doc.hash(), json);
                System.out.println("Saved to " + outDir.resolve(doc.hash() + ".json"));
            }

            audit.record(AuditEvent.parsed(doc.hash(), userId, parserName, null));
            System.out.printf("Parsed: hash=%s, blocks=%d, source=%s%n",
                    doc.hash(), doc.blocks().size(), doc.metadata().sourceName());
            return 0;

        } catch (PdfParserException ex) {
            System.err.println("Parse failed: " + ex.getMessage());
            audit.record(AuditEvent.failed("unknown", userId, parserName, null, ex.getMessage()));
            return 1;
        } catch (Exception ex) {
            System.err.println("Unexpected error: " + ex.getMessage());
            return 1;
        }
    }
}