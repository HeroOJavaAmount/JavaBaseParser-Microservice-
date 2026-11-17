package ru.netology.storage;

import ru.netology.exception.StorageException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * Хранилище документов в виде файлов {@code <hash>.json} в каталоге.
 *
 * <p>Именование по SHA-256 исходного PDF:
 * <ul>
 *   <li>дедупликация —** **один** **PDF** **=** **один** **файл**;</li>
 *   <li>имя** **не** **зависит** **от** **пользовательского** (пробелы, кириллица);</li>
 *   <li>повторная** **запись** **того** **же** **hash** —** **no-op**.</li>
 * </ul>
 *
 * <p>Атомарная запись: сначала во временный файл, потом move. Иначе** **риск**
 * прочитать недописанный JSON при параллельном чтении.
 *
 * <p>Потокобезопасность: запись атомарна (Files.move на одной ФС),
 * блокировки не нужны.
 */
public class FileDocumentStorage implements DocumentStorage {

    private final Path outputDir;

    public FileDocumentStorage(Path outputDir) {
        this.outputDir = outputDir;
        try {
            Files.createDirectories(outputDir);
        } catch (IOException ex) {
            throw new StorageException("Can not create output directory: " + outputDir, ex);
        }
    }

    @Override
    public void save(String hash, String serializedContent) {
        if (hash == null || hash.isBlank()) {
            throw new IllegalArgumentException("hash must not be null or blank");
        }
        Path target = fileFor(hash);
        if (Files.exists(target)) {
            return;   // уже сохранён (дедупликация)
        }

        Path tmp = outputDir.resolve(hash + ".json.tmp." + System.nanoTime());
        try {
            Files.writeString(tmp, serializedContent);
            // move атомарен на одной ФС; REPLACE_EXISTING — на случай гонки
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            // cleanup tmp при ошибке
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
            throw new StorageException("Can not save document " + hash, ex);
        }
    }

    @Override
    public Optional<String> findByHash(String hash) {
        Path file = fileFor(hash);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(file));
        } catch (IOException ex) {
            throw new StorageException("Can not read document " + hash, ex);
        }
    }

    @Override
    public boolean exists(String hash) {
        return Files.exists(fileFor(hash));
    }

    private Path fileFor(String hash) {
        return outputDir.resolve(hash + ".json");
    }
}