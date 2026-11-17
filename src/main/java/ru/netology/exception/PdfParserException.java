package ru.netology.exception;

/**
 * Базовое unchecked-исключение для всех ошибок PDF-парсера.
 *
 * <p>Клиент может ловить:
 * <ul>
 *   <li>{@code catch (PdfParserException e)} — все ошибки разом</li>
 *   <li>{@code catch (DeclaredLoaderException e)} — только ошибки загрузки</li>
 *   <li>{@code catch (DeclaredLoadHttpException e)} — только HTTP-ошибки (со statusCode/url)</li>
 *   <li>{@code catch (ParseException e)} — только ошибки парсинга (когда появятся)</li>
 * </ul>
 */
public class PdfParserException extends RuntimeException {

    public PdfParserException(String message) {
        super(message);
    }

    public PdfParserException(String message, Throwable cause) {
        super(message, cause);
    }

    public PdfParserException(Throwable cause) {
        super(cause);
    }
}