package ru.netology.exception;

/**
 * Ошибка разбора текста в блоки.
 * Наследует {@link PdfParserException} — можно ловить как общую ошибку парсера.
 *
 * <p>Примеры: пустой текст после конвертера, битая кодировка, неподдерживаемая структура.
 */
public class ParseException extends PdfParserException {

    public ParseException(String message) {
        super(message);
    }

    public ParseException(String message, Throwable cause) {
        super(message, cause);
    }

    public ParseException(Throwable cause) {
        super(cause);
    }
}