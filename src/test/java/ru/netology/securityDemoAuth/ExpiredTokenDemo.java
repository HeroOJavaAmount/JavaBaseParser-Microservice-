package ru.netology.securityDemoAuth;

import ru.netology.auth.JwtTokenService;
import ru.netology.auth.TokenConfig;

import java.time.Duration;

/**
 * TTL токена: через 3 секунды токен с TTL=2 сек становится невалидным.
 *
 * <p>Защита: даже если токен утечёт, он работает ограниченное время.
 *
 * <p>Запуск: {@code mvn exec:java -Dexec.mainClass=ru.netology.demo.ExpiredTokenDemo}
 * (занимает ~3 сек)
 */
public class ExpiredTokenDemo {
    /**
     * <p><b>Внимание:</b> секрет здесь захардкожен только для демонстрации.
     * В реальном приложении секрет берётся из переменной окружения —
     * см. {@link TokenConfig#fromEnv()}.
     */
    public static void main(String[] args) throws InterruptedException {
        TokenConfig cfg = new TokenConfig(
                "VeryLongSecretKeyForDemoOfTampering-1234567890", Duration.ofSeconds(2));
        JwtTokenService service = new JwtTokenService(cfg);

        String token = service.generate("user1");
        System.out.println("Just issued:  valid=" + service.validate(token).valid());

        System.out.println("Waiting 3 seconds...");
        Thread.sleep(3000);

        var result = service.validate(token);
        System.out.println("After 3s:     valid=" + result.valid());
        System.out.println("Error:        " + result.error());

        System.out.println();
        System.out.println("ВЫВОД: TTL закрывает утечку токена — через час он мёртв.");
        System.out.println("       Для долгих сессий — refresh token (не в этом проекте).");
    }
}