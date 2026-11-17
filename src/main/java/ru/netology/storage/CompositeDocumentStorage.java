package ru.netology.storage;

import ru.netology.exception.StorageException;

import java.util.List;
import java.util.Optional;

/**
 * Отправляет результат сразу в несколько хранилищ (веером).
 * Sends result to several storages at once.
 *
 * <p>Если один упал — логируем и продолжаем. Если упали все — бросаем {@link StorageException}.
 * При {@code findByHash} — берём из первого, кто ответил.
 */
public class CompositeDocumentStorage implements DocumentStorage {

    private final List<DocumentStorage> delegates;

    public CompositeDocumentStorage(List<DocumentStorage> delegates) {
        if (delegates == null || delegates.isEmpty()) {
            throw new IllegalArgumentException("delegates must not be empty");
        }
        this.delegates = List.copyOf(delegates);
    }

    @Override
    public void save(String hash, String serializedContent) {
        int ok = 0;
        Exception lastError = null;
        for (DocumentStorage d : delegates) {
            try {
                d.save(hash, serializedContent);
                ok++;
            } catch (Exception ex) {
                lastError = ex;
                System.err.printf("Composite: delegate %s failed for hash=%s: %s%n",
                        d.getClass().getSimpleName(), hash, ex.getMessage());
            }
        }
        if (ok == 0) {
            throw new StorageException(
                    "All delegates failed for hash=" + hash + ", last error: " + lastError.getMessage(),
                    lastError);
        }
    }

    @Override
    public Optional<String> findByHash(String hash) {
        for (DocumentStorage d : delegates) {
            Optional<String> r = d.findByHash(hash);
            if (r.isPresent()) return r;
        }
        return Optional.empty();
    }

    @Override
    public boolean exists(String hash) {
        return delegates.stream().anyMatch(d -> d.exists(hash));
    }
}