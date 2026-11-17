package ru.netology.securityDemoAuth;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/**
 * Атака по времени (timing attack).
 *
 * <p>{@link String#equals} прерывается на первом несовпадении →
 * время ответа утекает информацию о префиксе токена. Атакующий
 * подбирает токен посимвольно.
 *
 * <p>{@link MessageDigest#isEqual} сравнивает все байты всегда →
 * время фиксировано, атакующий ничего не узнаёт.
 *
 * <p>Запуск: {@code mvn exec:java -Dexec.mainClass=ru.netology.demo.TimingAttackDemo}
 */
public class TimingAttackDemo {

    public static void main(String[] args) {
        String secret = "SuperSecretToken-12345";
        int N = 10_000;

        System.out.println("=== 1. String.equals (уязвимо) ===");
        long t1 = bench(N, () -> "XuperSecretToken-12345".equals(secret));   // 0 совпало
        long t2 = bench(N, () -> "SuperSecretToken-1234X".equals(secret));   // 22 совпало
        System.out.printf("  0 совпало:    %,d ns%n", t1);
        System.out.printf("  22 совпало:   %,d ns  ← дольше!%n", t2);
        System.out.printf("  разница:      %,d ns%n", t2 - t1);

        System.out.println();
        System.out.println("=== 2. MessageDigest.isEqual (constant-time) ===");
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        long t3 = bench(N, () -> MessageDigest.isEqual(
                "XuperSecretToken-12345".getBytes(StandardCharsets.UTF_8), secretBytes));
        long t4 = bench(N, () -> MessageDigest.isEqual(
                "SuperSecretToken-1234X".getBytes(StandardCharsets.UTF_8), secretBytes));
        System.out.printf("  0 совпало:    %,d ns%n", t3);
        System.out.printf("  22 совпало:   %,d ns  ← одинаково%n", t4);
        System.out.printf("  разница:      %,d ns%n", Math.abs(t4 - t3));

        System.out.println();
        System.out.println("ВЫВОД:");
        System.out.println("  equals: разница заметна → атакующий подбирает посимвольно");
        System.out.println("  isEqual: разница ~0 → атакующий ничего не узнаёт");
    }

    private static long bench(int n, Runnable r) {
        for (int i = 0; i < n / 10; i++) r.run();   // прогрев JIT
        long start = System.nanoTime();
        for (int i = 0; i < n; i++) r.run();
        return (System.nanoTime() - start) / n;
    }
}