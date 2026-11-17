package ru.netology.storage;

import java.util.Optional;

/**
 * Заглушка: не сохраняет, но печатает JSON в stdout.
 * Null storage: печатает JSON в консоль, используется для CLI-режима без файлов.
 *
 * <p>Удобно для {@code java -jar app.jar cert.pdf}, когда результат хотят сразу увидеть.
 */
public class NullDocumentStorage implements DocumentStorage {

    private final boolean printToStdout;

    public NullDocumentStorage() {
        this(true);
    }

    public NullDocumentStorage(boolean printToStdout) {
        this.printToStdout = printToStdout;
    }

    @Override
    public void save(String hash, String serializedContent) {
        if (printToStdout) {
            System.out.println(serializedContent);
        }
    }

    @Override
    public Optional<String> findByHash(String hash) {
        return Optional.empty();
    }

    @Override
    public boolean exists(String hash) {
        return false;
    }
}