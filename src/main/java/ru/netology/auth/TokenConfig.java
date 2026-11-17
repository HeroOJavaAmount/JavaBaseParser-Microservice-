package ru.netology.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Конфигурация модуля аутентификации на основе JWT.
 * <p>
 * Инкапсулирует секретный ключ для алгоритма {@code HS256} и время жизни токена (TTL).
 * Параметры могут быть загружены из переменных окружения:
 * <ul>
 *     <li>{@code PARSER_AUTH_SECRET} — секретный ключ (минимум {@value #MIN_SECRET_BYTES} байта в UTF-8);</li>
 *     <li>{@code PARSER_AUTH_TTL_MINUTES} — время жизни токена в минутах (по умолчанию 60).</li>
 * </ul>
 *
 * @param secret секретная строка для HMAC-подписи; если {@code null} или пуста, аутентификация считается отключенной
 * @param ttl    время жизни выпускаемых токенов (должно быть строго положительным)
 */
public record TokenConfig(String secret, Duration ttl) {

    public static final int MIN_SECRET_BYTES = 32;

    /**
     * Компактный конструктор с валидацией инвариантов конфигурации.
     *
     * @throws IllegalArgumentException если заданный секрет короче {@value #MIN_SECRET_BYTES} байт
     *                                  или если {@code ttl} равен {@code null}, нулю или отрицателен
     */
    public TokenConfig {
        if (secret != null && !secret.isBlank()) {
            int byteLength = secret.getBytes(StandardCharsets.UTF_8).length;
            if (byteLength < MIN_SECRET_BYTES) {
                throw new IllegalArgumentException(
                        "Secret must be at least " + MIN_SECRET_BYTES
                                + " bytes for HS256, got: " + byteLength);
            }
        }
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("TTL must be a positive duration");
        }
    }

    /**
     * Проверяет, активирована ли защита токенами.
     *
     * @return {@code true}, если секретный ключ задан и не состоит из пробелов; иначе {@code false}
     */
    public boolean enabled() {
        return secret != null && !secret.isBlank();
    }

    /**
     * Считывает настройки аутентификации из переменных окружения операционной системы.
     *
     * @return настроенный и провалидированный экземпляр {@link TokenConfig}
     * @throws IllegalArgumentException если значение {@code PARSER_AUTH_TTL_MINUTES} не является целым числом
     *                                  или нарушены требования к длине секрета и положительности TTL
     */
    public static TokenConfig fromEnv() {
        String secret = env("PARSER_AUTH_SECRET", null);
        String rawTtl = env("PARSER_AUTH_TTL_MINUTES", "60");
        long ttlMin;
        try {
            ttlMin = Long.parseLong(rawTtl);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "PARSER_AUTH_TTL_MINUTES must be a valid number, got: " + rawTtl, e);
        }
        return new TokenConfig(secret, Duration.ofMinutes(ttlMin));
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }
}
