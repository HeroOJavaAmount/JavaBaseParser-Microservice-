package ru.netology.extract;

import ru.netology.exception.DeclaredLoaderException;
/**
 * Преобразует содержимое statement (например, PDF-файл) в текст.
 * Абстракция, чтобы парсер не зависел от формата входных данных.
 */
public interface ConvertByteToText {

    /**
     * @param content сырые байты документа (например, PDF)
     * @return извлечённый текст
     * @throws DeclaredLoaderException при любой ошибке чтения/парсинга
     */
    String convert(byte[] content);
}