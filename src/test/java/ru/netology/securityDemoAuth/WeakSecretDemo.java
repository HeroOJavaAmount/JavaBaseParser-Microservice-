package ru.netology.securityDemoAuth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import ru.netology.auth.JwtTokenService;
import ru.netology.auth.TokenConfig;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Слабый секрет (из словаря) → brute-force за миллисекунды.
 *
 * <p>HMAC-SHA256 требует секрет ≥ 32 байт. Если секрет — из словаря
 * (password, secret, qwerty...) — атакующий перебирает по словарю.
 *
 * <p>Запуск: {@code mvn exec:java -Dexec.mainClass=ru.netology.demo.WeakSecretDemo}
 */
public class WeakSecretDemo {

    public static void main(String[] args) {
        // Создаём токен со слабым секретом
        String weakSecret = "secret123456789012345678901234567890";   // 32+ байта, но словарный
        TokenConfig cfg = new TokenConfig(weakSecret, Duration.ofHours(1));
        String token = new JwtTokenService(cfg).generate("admin");

        System.out.println("Token:    " + token);
        System.out.println("Brute-force по словарю...");

        String[] dictionary = {
                "secret", "password", "admin", "123456",
                "secret123456789012345678901234567890",   // ← настоящий
                "qwerty", "letmein"
        };

        for (String candidate : dictionary) {
            if (candidate.getBytes().length < 32) continue;   // JJWT требует ≥32
            if (verify(token, candidate)) {
                System.out.println("CRACKED with '" + candidate + "'");
                return;
            }
        }
        System.out.println("Not cracked (dictionary too small — real tools have millions)");
    }

    private static boolean verify(String token, String secret) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}