package ru.netology.storage;

import java.util.Optional;

/**
 * Контракт хранилища сериализованных документов.
 *
 * <p>Хранилище <b>не знает</b> про формат —** **принимает** **уже**
 * сериализованную строку (JSON, CSV, XML). Формат —** **ответственность**
 * вызывающего кода ({@link ru.netology.service.DocumentService}).
 *
 * <p>Связь с аудитом —** **через** {@code hash}: имя файла = {@code <hash>.json}.
 * {@link ru.netology.audit.AuditEvent#documentHash()} указывает на этот hash.
 */
public interface DocumentStorage {

    /** Сохранить документ (уже сериализованный). */
    void save(String hash, String serializedContent);

    /** Найти по hash. Пустой —** **если** **нет**. */
    Optional<String> findByHash(String hash);

    /** Есть ли уже такой документ (для дедупликации). */
    boolean exists(String hash);
}