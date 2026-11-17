package ru.netology.exception;

/**
 * Исключение, сигнализирующее об ошибке загрузки statement (файл, сеть, БД и т.п.).
 *
 * <p>Наследуется от {@link RuntimeException}, поэтому является <b>unchecked</b>:
 * вызывающий код не обязан оборачивать вызовы в try/catch.
 * Ошибка всё равно долетит до глобального обработчика.
 */
public class DeclaredLoaderException extends PdfParserException {

    /**
     * @param message человекочитаемое описание ошибки
     */
    public DeclaredLoaderException(String message) {
        super(message);
    }

    /**
     * @param message человекочитаемое описание
     * @param cause   первопричина (например, {@link java.io.IOException}).
     *                Сохраняется для отладки — не теряйте стектрейс оригинала!
     */
    public DeclaredLoaderException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * @param cause первопричина; сообщение будет взято из {@code cause.getMessage()}.
     */
    public DeclaredLoaderException(Throwable cause) {
        super(cause);
    }
}