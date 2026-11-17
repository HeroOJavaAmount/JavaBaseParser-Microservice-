package ru.netology.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import ru.netology.exception.StorageException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Хранилище аудита в JSONL-файлах: {@code /audit/YYYY-MM-DD.jsonl}.
 *
 * <p>Формат JSONL (JSON Lines): по одному JSON на строку. Плюсы:
 * <ul>
 *   <li>append-only — не читаем весь файл при записи;</li>
 *   <li>легко парсится (построчно);</li>
 *   <li>совместим с ELK/Loki (каждая строка — отдельное событие).</li>
 * </ul>
 *
 * <p>Потокобезопасность: записи синхронизированы (append атомарен),
 * чтения не блокируются (append-only, читатель видит законченные строки;
 * частичную последнюю строку отбрасываем при парсинге).
 */
public class FileAuditStorage implements AuditStorage {

    private final Path auditDir;
    private final ObjectMapper mapper;
    /** Блокировка для записи в файл (append должен быть атомарным). */
    private final Object writeLock = new Object();

    public FileAuditStorage(Path auditDir) {
        this.auditDir = auditDir;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);;
        try {
            Files.createDirectories(auditDir);
        } catch (IOException ex) {
            throw new StorageException("Can not create audit directory: " + auditDir, ex);
        }
    }

    @Override
    public void record(AuditEvent event) {
        String line;
        try {
            line = mapper.writeValueAsString(event) + "\n";
        } catch (IOException ex) {
            throw new StorageException("Can not serialize AuditEvent", ex);
        }

        Path file = fileForDate(LocalDate.ofInstant(event.timestamp(), ZoneOffset.UTC));
        synchronized (writeLock) {
            try {
                Files.writeString(file, line,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
            } catch (IOException ex) {
                throw new StorageException("Can not append audit event to " + file, ex);
            }
        }
    }

    @Override
    public List<AuditEvent> findByDocumentHash(String hash) {
        List<AuditEvent> result = new ArrayList<>();
        for (AuditEvent event : readAll()) {
            if (event.documentHash().equals(hash)) {
                result.add(event);
            }
        }
        return result;
    }

    @Override
    public List<AuditEvent> findByUser(String userId) {
        List<AuditEvent> result = new ArrayList<>();
        for (AuditEvent event : readAll()) {
            if (userId.equals(event.userId())) {
                result.add(event);
            }
        }
        return result;
    }

    @Override
    public Optional<AuditEvent> findLatestByHash(String hash) {
        AuditEvent latest = null;
        for (AuditEvent event : readAll()) {
            if (event.documentHash().equals(hash)) {
                if (latest == null || event.timestamp().isAfter(latest.timestamp())) {
                    latest = event;
                }
            }
        }
        return Optional.ofNullable(latest);
    }

    // ────────────────────────────────────────────────────────────────
    //  Внутренние методы
    // ────────────────────────────────────────────────────────────────

    private Path fileForDate(LocalDate date) {
        return auditDir.resolve(date + ".jsonl");
    }

    /** Прочитать все события из всех файлов /audit/*.jsonl. */
    private List<AuditEvent> readAll() {
        List<AuditEvent> all = new ArrayList<>();
        try (var stream = Files.list(auditDir)) {
            for (Path file : stream.filter(p -> p.toString().endsWith(".jsonl")).toList()) {
                readFileInto(file, all);
            }
        } catch (IOException ex) {
            throw new StorageException("Can not list audit files in " + auditDir, ex);
        }
        return all;
    }

    /** Прочитать один файл построчно. Неполные строки (в процессе append) пропускаем. */
    private void readFileInto(Path file, List<AuditEvent> target) {
        try {
            List<String> lines = Files.readAllLines(file);
            for (String line : lines) {
                if (line.isBlank()) continue;
                try {
                    target.add(mapper.readValue(line, AuditEvent.class));
                } catch (IOException ex) {
                    // частично записанная строка (append в процессе) — пропускаем
                }
            }
        } catch (IOException ex) {
            throw new StorageException("Can not read audit file " + file, ex);
        }
    }
}