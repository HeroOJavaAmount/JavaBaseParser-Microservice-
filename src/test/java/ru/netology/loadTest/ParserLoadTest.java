package ru.netology.loadtest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * HTTP-нагрузочный тест эндпоинта /api/parse.
 * Отправляет N запросов с контролируемой конкурентностью, меряет RPS,
 * перцентили времени выполнения и разбивку ошибок.
 */
public class ParserLoadTest {

    private static final int WARMUP_REQUESTS = 10;

    private final LoadTestConfig cfg;
    private final List<byte[]> pdfPayloads = new ArrayList<>();
    private final AtomicInteger success = new AtomicInteger();
    private final Map<String, AtomicInteger> errors = new ConcurrentHashMap<>();
    private final List<Long> latenciesNanos = Collections.synchronizedList(new ArrayList<>());

    private long totalFileBytes = 0;
    private int minFileBytes = Integer.MAX_VALUE;
    private int maxFileBytes = 0;
    private int concurrency = 1;

    public ParserLoadTest(LoadTestConfig cfg) {
        this.cfg = cfg;
    }

    public static void main(String[] args) throws Exception {
        LoadTestConfig cfg = LoadTestConfig.load();

        Path folder = args.length > 0 ? Path.of(args[0]) : cfg.pdfFolder();
        int total = args.length > 1 ? Integer.parseInt(args[1]) : cfg.loadRequests();
        int concurrency = args.length > 2 ? Integer.parseInt(args[2]) : cfg.loadConcurrency();

        ParserLoadTest test = new ParserLoadTest(cfg);
        test.loadPdfs(folder);
        test.run(total, concurrency);
    }

    private void loadPdfs(Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            files.filter(p -> p.toString().toLowerCase().endsWith(".pdf"))
                    .sorted()
                    .forEach(p -> {
                        try {
                            byte[] data = Files.readAllBytes(p);
                            pdfPayloads.add(data);
                            totalFileBytes += data.length;
                            minFileBytes = Math.min(minFileBytes, data.length);
                            maxFileBytes = Math.max(maxFileBytes, data.length);
                        } catch (IOException ex) {
                            System.err.printf("Пропуск %s: %s%n", p, ex.getMessage());
                        }
                    });
        }
        if (pdfPayloads.isEmpty()) {
            throw new IllegalStateException("В папке нет PDF: " + folder.toAbsolutePath());
        }
    }

    private void run(int total, int concurrency) throws Exception {
        this.concurrency = concurrency;
        printConfiguration(total);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        System.out.printf("Прогрев: %d запросов...%n", WARMUP_REQUESTS);
        execute(client, WARMUP_REQUESTS, concurrency, false);

        success.set(0);
        errors.clear();
        latenciesNanos.clear();

        System.out.printf("Нагрузка: %d запросов, одновременно %d...%n", total, concurrency);
        long start = System.nanoTime();
        execute(client, total, concurrency, true);
        long elapsed = System.nanoTime() - start;

        report(elapsed, total);
    }

    private void printConfiguration(int total) {
        long avgFileBytes = totalFileBytes / pdfPayloads.size();
        System.out.println();
        System.out.println("=== Конфигурация теста ===");
        System.out.printf("  Целевой URL:        %s%n", cfg.loadTargetUrl());
        System.out.printf("  PDF файлов:         %d%n", pdfPayloads.size());
        System.out.printf("  Суммарный размер:   %s%n", humanBytes(totalFileBytes));
        System.out.printf("  Средний размер:     %s%n", humanBytes(avgFileBytes));
        System.out.printf("  Диапазон размеров:  %s – %s%n",
                humanBytes(minFileBytes), humanBytes(maxFileBytes));
        System.out.printf("  Всего запросов:     %d%n", total);
        System.out.printf("  Одновременно:       %d%n", concurrency);
        System.out.printf("  Таймаут запроса:    %d сек%n", cfg.loadTimeoutSeconds());
        System.out.printf("  Прогрев:            %d запросов%n", WARMUP_REQUESTS);
        System.out.printf("  Ядер CPU:           %d%n", Runtime.getRuntime().availableProcessors());
        System.out.println("==========================");
        System.out.println();
    }

    private void execute(HttpClient client, int count, int concurrency, boolean collectStats)
            throws Exception {
        Semaphore inFlight = new Semaphore(concurrency);
        AtomicInteger done = new AtomicInteger();
        long startNanos = System.nanoTime();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < count; i++) {
                inFlight.acquire();
                executor.submit(() -> {
                    try {
                        sendOne(client, collectStats);
                    } finally {
                        int n = done.incrementAndGet();
                        inFlight.release();
                        if (collectStats && (n % 50 == 0 || n == count)) {
                            double elapsedSec = (System.nanoTime() - startNanos) / 1_000_000_000.0;
                            double rate = n / elapsedSec;
                            long eta = rate > 0 ? (long) ((count - n) / rate) : 0;
                            System.out.printf("[%3d%%] %d/%d запросов  |  %.1f зап/сек  |  осталось: %ds%n",
                                    (int) (100.0 * n / count), n, count, rate, eta);
                        }
                    }
                });
            }
        }
    }

    private void sendOne(HttpClient client, boolean collectStats) {
        byte[] pdf = pdfPayloads.get(ThreadLocalRandom.current().nextInt(pdfPayloads.size()));
        long t0 = collectStats ? System.nanoTime() : 0;

        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(cfg.loadTargetUrl()))
                .header("Content-Type", "application/pdf")
                .timeout(Duration.ofSeconds(cfg.loadTimeoutSeconds()))
                .POST(HttpRequest.BodyPublishers.ofByteArray(pdf));

        if (cfg.loadAuthToken() != null && !cfg.loadAuthToken().isBlank()) {
            b.header("Authorization", "Bearer " + cfg.loadAuthToken());
        }

        try {
            HttpResponse<Void> r = client.send(b.build(), HttpResponse.BodyHandlers.discarding());
            if (r.statusCode() == 200) {
                success.incrementAndGet();
            } else {
                bump("HTTP " + r.statusCode());
            }
        } catch (HttpTimeoutException ex) {
            bump("TIMEOUT");
        } catch (java.net.ConnectException ex) {
            bump("CONNECTION_REFUSED");
        } catch (Exception ex) {
            bump(ex.getClass().getSimpleName());
        } finally {
            if (collectStats) latenciesNanos.add(System.nanoTime() - t0);
        }
    }

    private void bump(String key) {
        errors.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
    }

    private void report(long elapsedNanos, int total) {
        double seconds = elapsedNanos / 1_000_000_000.0;
        int ok = success.get();
        int err = errors.values().stream().mapToInt(AtomicInteger::get).sum();
        int done = ok + err;

        List<Long> sorted = latenciesNanos.stream().sorted().toList();
        long min = sorted.isEmpty() ? 0 : sorted.get(0);
        long p50 = percentile(sorted, 50);
        long p95 = percentile(sorted, 95);
        long p99 = percentile(sorted, 99);
        long max = sorted.isEmpty() ? 0 : sorted.get(sorted.size() - 1);

        System.out.println();
        System.out.println("=== Результат нагрузочного теста ===");
        System.out.println();
        System.out.println("Итоги:");
        System.out.printf("  Всего запросов:     %d%n", total);
        System.out.printf("  Успешно:            %d (%.1f%%)%n", ok, pct(ok, done));
        System.out.printf("  Ошибок:             %d (%.1f%%)%n", err, pct(err, done));
        System.out.printf("  Общее время:        %.1f сек%n", seconds);
        System.out.printf("  Скорость:           %.1f запросов/сек%n", done / seconds);
        System.out.println();

        System.out.println("Время выполнения запроса (от отправки до ответа):");
        System.out.printf("  Лучший:             %s  — самый быстрый%n", fmtLatency(min));
        System.out.printf("  Типичный (p50):     %s  — половина запросов быстрее%n", fmtLatency(p50));
        System.out.printf("  Плохой (p95):       %s  — 5%% запросов медленнее%n", fmtLatency(p95));
        System.out.printf("  Худший (p99):       %s  — 1%% запросов медленнее%n", fmtLatency(p99));
        System.out.printf("  Максимум:           %s  — самый медленный за прогон%n", fmtLatency(max));
        System.out.println();

        long slaNanos = Duration.ofSeconds(cfg.loadSlaP95Seconds()).toNanos();
        boolean slaOk = p95 <= slaNanos;
        System.out.printf("SLA: 95%% запросов быстрее %d сек — %s (p95 = %s)%n",
                cfg.loadSlaP95Seconds(),
                slaOk ? "выполнено" : "НЕ выполнено",
                fmtLatency(p95));
        System.out.println();

        if (!errors.isEmpty()) {
            System.out.println("Ошибки:");
            errors.forEach((type, cnt) ->
                    System.out.printf("  %-20s %d%n", type, cnt.get()));
            System.out.println();
        }

        double sr = pct(ok, done);
        if (sr >= 99.0) {
            System.out.println("Вердикт: сервер справляется с этой нагрузкой.");
        } else if (sr >= 95.0) {
            System.out.println("Вердикт: почти справляется, есть сбои.");
        } else {
            System.out.println("Вердикт: сервер НЕ справляется с этой нагрузкой.");
        }
    }

    private static double pct(int part, int total) {
        return total == 0 ? 0 : part * 100.0 / total;
    }

    private static long percentile(List<Long> sorted, int p) {
        if (sorted.isEmpty()) return 0;
        int idx = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, idx));
    }

    private static String fmtLatency(long nanos) {
        double ms = nanos / 1_000_000.0;
        if (ms < 1000) return String.format("%.0f ms", ms);
        return String.format("%.2f сек", ms / 1000.0);
    }

    private static String humanBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.0f КБ", bytes / 1024.0);
        return String.format("%.1f МБ", bytes / 1024.0 / 1024.0);
    }
}