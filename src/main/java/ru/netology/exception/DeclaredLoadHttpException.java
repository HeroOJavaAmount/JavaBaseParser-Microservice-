package ru.netology.exception;

import ru.netology.enumConst.DeclaredLoadErrorType;

import java.io.Serial;
import java.util.OptionalInt;

public class DeclaredLoadHttpException extends DeclaredLoaderException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final OptionalInt statusCode;
    private final DeclaredLoadErrorType errorType;
    private final String url;

    public DeclaredLoadHttpException(String message,
                                     int statusCode,
                                     DeclaredLoadErrorType errorType,
                                     String url) {
        super(buildMessage(message, url, OptionalInt.of(validateStatusCode(statusCode)), errorType));
        this.statusCode = OptionalInt.of(statusCode);
        this.errorType = requireNonNull(errorType, "errorType");
        this.url = requireNonNull(url, "url");
    }

    public DeclaredLoadHttpException(String message,
                                     DeclaredLoadErrorType errorType,
                                     String url,
                                     Throwable cause) {
        super(buildMessage(message, url, OptionalInt.empty(), errorType), cause);
        this.statusCode = OptionalInt.empty();
        this.errorType = requireNonNull(errorType, "errorType");
        this.url = requireNonNull(url, "url");
        requireNonNull(cause, "cause");
    }

    public OptionalInt getStatusCode() {
        return statusCode;
    }

    public DeclaredLoadErrorType getErrorType() {
        return errorType;
    }

    public String getUrl() {
        return url;
    }

    public boolean shouldRetry() {
        if (errorType == DeclaredLoadErrorType.NETWORK_FAILURE
                || errorType == DeclaredLoadErrorType.TIMEOUT) {
            return true;
        }
        if (errorType == DeclaredLoadErrorType.INTERRUPTED) {
            return false;
        }
        return statusCode.stream()
                .anyMatch(code -> code >= 500 || code == 408 || code == 429);
    }

    private static String buildMessage(String message,
                                       String url,
                                       OptionalInt statusCode,
                                       DeclaredLoadErrorType errorType) {
        String statusPart = statusCode.isPresent()
                ? "HTTP " + statusCode.getAsInt()
                : "network failure (no HTTP status)";
        return String.format("%s [url=%s, %s, type=%s]",
                message, url, statusPart, errorType);
    }

    private static int validateStatusCode(int statusCode) {
        if (statusCode < 100 || statusCode > 599) {
            throw new IllegalArgumentException(
                    "HTTP status code must be in 100..599, got: " + statusCode);
        }
        return statusCode;
    }

    private static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }
}