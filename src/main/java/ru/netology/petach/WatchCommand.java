package ru.netology.petach;

import ru.netology.audit.AuditEvent;
import ru.netology.audit.AuditStorage;
import ru.netology.dto.Document;
import ru.netology.load.DeclaredLoadFile;
import ru.netology.serialize.DocumentSerializer;
import ru.netology.service.DocumentService;
import ru.netology.storage.DocumentStorage;

import java.io.IOException;
import java.nio.file.*;

public class WatchCommand {

    private final DocumentService service;
    private final DocumentSerializer serializer;
    private final DocumentStorage storage;
    private final AuditStorage audit;
    private final boolean repairMojibake;

    public WatchCommand(DocumentService service,
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

    /**
     * @metod   Встроенный java.nio.file.WatchService (JDK 7+) Использует системные механизмы (inotify на Linux, ReadDirectoryChangesW на Windows, на macOS — опрос). Минусы: рекурсивное наблюдение нужно организовывать самому (регистрировать каждый подкаталог), события теряются при переполнении очереди (OVERFLOW), возможны гонки между регистрацией каталога и созданием файлов внутри, на Linux ENTRY_MODIFY может сыпаться пачками на одну операцию.
     * @param args
     * @param userId
     * @param parserName
     * @return
     */
    public int run(String[] args, String userId, String parserName) {
        if (args.length < 1) {
            System.err.println("Usage: watch <inDir>");
            return 2;
        }
        Path inDir = Path.of(args[0]);
        if (!Files.isDirectory(inDir)) {
            System.err.println("Not a directory: " + inDir);
            return 2;
        }

        System.out.println("Watching " + inDir.toAbsolutePath() + " (Ctrl+C to stop)");
        try (WatchService ws = FileSystems.getDefault().newWatchService()) {
            inDir.register(ws, StandardWatchEventKinds.ENTRY_CREATE);
            while (true) {
                WatchKey key = ws.take();
                for (WatchEvent<?> ev : key.pollEvents()) {
                    if (ev.kind() != StandardWatchEventKinds.ENTRY_CREATE) continue;
                    Path name = (Path) ev.context();
                    if (!name.toString().toLowerCase().endsWith(".pdf")) continue;
                    Path file = inDir.resolve(name);
                    Thread.sleep(500);
                    handle(file, userId, parserName);
                }
                key.reset();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return 0;
        } catch (IOException ex) {
            System.err.println("Watch failed: " + ex.getMessage());
            return 1;
        }
    }

    private void handle(Path file, String userId, String parserName) {
        try {
            Document doc = service.parse(
                    new DeclaredLoadFile(file.toString()),
                    repairMojibake,
                    file.getFileName().toString()   // ← sourceName
            );
            storage.save(doc.hash(), serializer.serialize(doc));
            audit.record(AuditEvent.parsed(doc.hash(), userId, parserName, null));
            System.out.printf("  OK %s → %s%n", file.getFileName(), doc.hash().substring(0, 12));
        } catch (Exception ex) {
            audit.record(AuditEvent.failed("unknown", userId, parserName, null, ex.getMessage()));
            System.err.printf("  FAIL %s: %s%n", file.getFileName(), ex.getMessage());
        }
    }
}