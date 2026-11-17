package ru.netology.exception;

/**
 * Ошибка хранилища (файл не записался, каталог не создался и т.п.).
 * Наследует {@link PdfParserException} —** **можно** **ловить** **всё** **разом**.
 */
public class StorageException extends PdfParserException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }

    public StorageException(Throwable cause) {
        super(cause);
    }
}