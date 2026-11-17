package ru.netology.auth;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты {@link JwtTokenService}: генерация, валидация, истечение, подделка.
 *
 * <p>Секрет — 32+ байта (требование HMAC-SHA256).
 * Время жизни для истечения — 1 секунда + пауза перед проверкой.
 *
 * <p>Проверяется контракт безопасности, а не реализация. При смене
 * библиотеки (JJWT → Nimbus) тесты останутся зелёными.
 */
class JwtTokenServiceTest {

    private static final String SECRET = "VeryLongSecretKeyForTestingOnly-1234567890";

    private static JwtTokenService newService(Duration ttl) {
        return new JwtTokenService(new TokenConfig(SECRET, ttl));
    }

    /** generate → validate: свежий токен валиден, subject совпадает. */
    @Test
    void generate_thenValidate_succeeds() {
        JwtTokenService svc = newService(Duration.ofHours(1));

        String token = svc.generate("ivanov");
        AuthResult result = svc.validate(token);

        assertTrue(result.valid());
        assertEquals("ivanov", result.subject());
        assertNotNull(result.expiresAt());
    }

    /** Пустой токен → fail с понятной ошибкой. */
    @Test
    void validate_emptyToken_fails() {
        JwtTokenService svc = newService(Duration.ofHours(1));

        assertFalse(svc.validate(null).valid());
        assertFalse(svc.validate("").valid());
        assertFalse(svc.validate("   ").valid());
    }

    /** Токен, подписанный другим секретом → fail (подпись не сходится). */
    @Test
    void validate_tokenSignedWithDifferentSecret_fails() {
        JwtTokenService svcA = newService(Duration.ofHours(1));
        JwtTokenService svcB = new JwtTokenService(
                new TokenConfig("AnotherVeryLongSecretKeyForTesting-0987654321", Duration.ofHours(1))
        );

        String tokenFromB = svcB.generate("ivanov");
        AuthResult result = svcA.validate(tokenFromB);

        assertFalse(result.valid(), "токен с чужим секретом не должен проходить");
    }

    /** Истёкший токен (TTL 1 сек, пауза 1.5 сек) → fail. */
    @Test
    void validate_expiredToken_fails() throws InterruptedException {
        JwtTokenService svc = newService(Duration.ofSeconds(1));

        String token = svc.generate("ivanov");
        assertTrue(svc.validate(token).valid(), "сразу после generate — валиден");

        Thread.sleep(1500);

        AuthResult result = svc.validate(token);
        assertFalse(result.valid(), "через 1.5 сек токен должен истечь");
        assertTrue(result.error().toLowerCase().contains("expired"),
                "ошибка должна говорить об истечении, got: " + result.error());
    }

    /** Подделка payload → подпись не сходится → fail. */
    @Test
    void validate_tamperedPayload_fails() {
        JwtTokenService svc = newService(Duration.ofHours(1));
        String token = svc.generate("user1");

        // Меняем payload (вторая часть JWT), подпись оставляем старой
        String[] parts = token.split("\\.");
        String tamperedPayload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"admin\"}".getBytes());
        String tampered = parts[0] + "." + tamperedPayload + "." + parts[2];

        assertFalse(svc.validate(tampered).valid(),
                "изменённый payload не должен проходить без новой подписи");
    }

    /** generate() с null subject → исключение (fail fast). */
    @Test
    void generate_nullSubject_throws() {
        JwtTokenService svc = newService(Duration.ofHours(1));

        assertThrows(Exception.class, () -> svc.generate(null));
    }
}