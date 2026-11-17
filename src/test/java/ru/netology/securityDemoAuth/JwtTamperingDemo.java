package ru.netology.securityDemoAuth;

import ru.netology.auth.JwtTokenService;
import ru.netology.auth.TokenConfig;

import java.time.Duration;
import java.util.Base64;

/**
 * Попытка изменить payload JWT (subject) без знания секрета.
 *
 * <p>JWT payload — это открытый JSON (Base64URL). Его можно раскодировать,
 * изменить, закодировать обратно. <b>Но</b> подпись HMAC знает секрет →
 * изменил payload → подпись не сойдётся → 401.
 *
 * <p>Запуск: {@code mvn exec:java -Dexec.mainClass=ru.netology.demo.JwtTamperingDemo}
 */
public class JwtTamperingDemo {

    public static void main(String[] args) {
        TokenConfig cfg = new TokenConfig(
                "VeryLongSecretKeyForDemoOfTampering-1234567890", Duration.ofHours(1));
        JwtTokenService service = new JwtTokenService(cfg);

        String token = service.generate("user1");
        System.out.println("Original token: " + token);
        System.out.println("Validate:       " + service.validate(token).valid());

        // Пытаемся заменить subject на "admin"
        String tampered = tamper(token, "admin");
        System.out.println();
        System.out.println("Tampered token: " + tampered);
        var result = service.validate(tampered);
        System.out.println("Validate:       " + result.valid());
        System.out.println("Error:          " + result.error());

        System.out.println();
        System.out.println("ВЫВОД: подпись HMAC знает секрет; изменил payload —");
        System.out.println("       подпись не сойдётся → 401. JWT tamper-proof.");
    }

    /**
     * Меняет sub в payload, оставляя старую подпись.
     * Если payload не содержит {@code "sub":"<oldSub>"} — возвращает исходный.
     */
    private static String tamper(String token, String newSubject) {
        String[] parts = token.split("\\.");
        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
        String forged = payloadJson.replace("\"sub\":\"user1\"", "\"sub\":\"" + newSubject + "\"");
        String forgedB64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(forged.getBytes());
        return parts[0] + "." + forgedB64 + "." + parts[2];   // подпись старая
    }
}