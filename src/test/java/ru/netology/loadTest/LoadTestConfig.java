package ru.netology.loadtest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Конфиг тестов из {@code benchmark.properties}.
 * Приоритет: CLI args > env > properties > defaults.
 */
public record LoadTestConfig(
        Path pdfFolder,
        int benchmarkIterations,
        int benchmarkWarmup,
        String loadTargetUrl,
        String loadAuthToken,
        int loadRequests,
        int loadConcurrency,
        int loadTimeoutSeconds,
        int loadSlaP95Seconds
) {

    public static LoadTestConfig load() {
        Properties props = new Properties();
        try (InputStream in = LoadTestConfig.class.getClassLoader()
                .getResourceAsStream("benchmark.properties")) {
            if (in != null) props.load(in);
        } catch (IOException ex) {
            System.err.println("Не удалось загрузить benchmark.properties: " + ex.getMessage());
        }

        return new LoadTestConfig(
                Path.of(resolve("PDF_FOLDER", props, "pdf.folder", "test-in")),
                parseInt(resolve("BENCHMARK_ITERATIONS", props, "benchmark.iterations", "5")),
                parseInt(resolve("BENCHMARK_WARMUP", props, "benchmark.warmup", "1")),
                resolve("LOAD_TARGET_URL", props, "load.target.url", "http://localhost:8080/api/parse"),
                resolve("LOAD_AUTH_TOKEN", props, "load.auth.token", ""),
                parseInt(resolve("LOAD_REQUESTS", props, "load.requests", "200")),
                parseInt(resolve("LOAD_CONCURRENCY", props, "load.concurrency", "5")),
                parseInt(resolve("LOAD_TIMEOUT_SECONDS", props, "load.timeout.seconds", "90")),
                parseInt(resolve("LOAD_SLA_P95_SECONDS", props, "load.sla.p95.seconds", "10"))
        );
    }

    private static String resolve(String envName, Properties props, String propName, String def) {
        String env = System.getenv(envName);
        if (env != null && !env.isBlank()) return env;
        String p = props.getProperty(propName);
        return (p != null && !p.isBlank()) ? p : def;
    }

    private static int parseInt(String s) {
        return Integer.parseInt(s.trim());
    }
}