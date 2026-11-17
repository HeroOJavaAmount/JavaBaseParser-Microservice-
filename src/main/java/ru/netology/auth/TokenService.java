package ru.netology.auth;

/** Контракт: генерировать и проверять токены. */
public interface TokenService {

    /** Создать токен для subject (userId, clientId...). */
    String generate(String subject);

    /** Проверить токен. Возвращает AuthResult с subject и expiresAt. */
    AuthResult validate(String token);
}