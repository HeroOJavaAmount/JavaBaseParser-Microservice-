package ru.netology.auth;

import java.time.Instant;

/**
 * Результат проверки токена.
 *
 * @param valid     флаг успешности проверки
 * @param subject   идентификатор владельца токена (если валиден)
 * @param expiresAt время истечения срока действия токена
 * @param error     описание ошибки (если токен невалиден)
 */
public record AuthResult(boolean valid, String subject, Instant expiresAt, String error) {

    public static AuthResult ok(String subject, Instant expiresAt) {
        return new AuthResult(true, subject, expiresAt, null);
    }

    public static AuthResult fail(String error) {
        return new AuthResult(false, null, null, error);
    }
}
