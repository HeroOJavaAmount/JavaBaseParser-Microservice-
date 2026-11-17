package ru.netology.loadtest;

import ru.netology.dto.Block;
import ru.netology.extract.ConvertByteToText;
import ru.netology.extract.PdfToText;
import ru.netology.extract.PdfToTextAdvanced;
import ru.netology.extract.PdfToTextBlockParser;
import ru.netology.extract.PdfToTextHighload;
import ru.netology.parser.BlockParser;
import ru.netology.parser.HeuristicRulesParser;
import ru.netology.parser.HeuristicStreamParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Сравнение стратегий ConvertByteToText и BlockParser напрямую.
 * Каждый PDF разбирается N раз с прогревом. Меряет convert, parse и total.
 */
public class ComponentBenchmark {

    public static void main(String[] args) throws Exception {
        LoadTestConfig cfg = LoadTestConfig.load();

        Path folder = args.length > 0 ? Path.of(args[0]) : cfg.pdfFolder();
        int iterations = args.length > 1 ? Integer.parseInt(args[1]) : cfg.benchmarkIterations();
        int warmup = cfg.benchmarkWarmup();

        List<byte[]> pdfs = loadPdfs(folder);
        long totalBytes = pdfs.stream().mapToLong(b -> b.length).sum();

        System.out.println();
        System.out.println("=== Сравнение стратегий парсинга ===");
        System.out.printf("Папка:              %s%n", folder.toAbsolutePath());
        System.out.printf("PDF файлов:         %d%n", pdfs.size());
        System.out.printf("Суммарный размер:   %s%n", humanBytes(totalBytes));
        System.out.printf("Средний размер:     %s%n", humanBytes(totalBytes / pdfs.size()));
        System.out.printf("Итераций на PDF:    %d%n", iterations);
        System.out.printf("Прогрев на PDF:     %d%n", warmup);
        System.out.println();

        Map<String, ConvertByteToText> converters = new LinkedHashMap<>();
        converters.put("PdfToText", new PdfToText());
        converters.put("PdfToTextAdvanced", new PdfToTextAdvanced());
        converters.put("PdfToTextBlockParser", new PdfToTextBlockParser());
        converters.put("PdfToTextHighload", new PdfToTextHighload());

        Map<String, BlockParser> parsers = new LinkedHashMap<>();
        parsers.put("Rules", new HeuristicRulesParser());
        parsers.put("Stream", new HeuristicStreamParser());

        printHeader();
        for (var c : converters.entrySet()) {
            for (var p : parsers.entrySet()) {
                runOne(c.getKey(), c.getValue(), p.getKey(), p.getValue(), pdfs, iterations, warmup);
            }
        }

        System.out.println();
        System.out.println("convert = байты→текст (PDFBox), parse = текст→блоки (регулярки), total = сумма");
    }

    private static void printHeader() {
        System.out.printf("%-22s %-7s %5s %5s  %9s %9s  %9s %9s  %9s %9s%n",
                "Конвертер", "Парсер", "OK", "Ошиб",
                "conv p50", "conv p95", "parse p50", "parse p95",
                "total p50", "total p95");
        System.out.println("-".repeat(124));
    }

    private static void runOne(String convName, ConvertByteToText conv,
                               String parseName, BlockParser parser,
                               List<byte[]> pdfs, int iterations, int warmup) {
        for (byte[] pdf : pdfs) {
            for (int i = 0; i < warmup; i++) {
                try {
                    parser.parse(conv.convert(pdf));
                } catch (Exception ignored) {}
            }
        }

        int ok = 0, fail = 0;
        List<Long> convNanos = new ArrayList<>();
        List<Long> parseNanos = new ArrayList<>();
        List<Long> totalNanos = new ArrayList<>();

        for (byte[] pdf : pdfs) {
            for (int i = 0; i < iterations; i++) {
                try {
                    long t0 = System.nanoTime();
                    String text = conv.convert(pdf);
                    long t1 = System.nanoTime();
                    List<Block> ignored = parser.parse(text);
                    long t2 = System.nanoTime();

                    convNanos.add(t1 - t0);
                    parseNanos.add(t2 - t1);
                    totalNanos.add(t2 - t0);
                    ok++;
                } catch (Exception ex) {
                    fail++;
                }
            }
        }

        convNanos.sort(Long::compare);
        parseNanos.sort(Long::compare);
        totalNanos.sort(Long::compare);

        System.out.printf("%-22s %-7s %5d %5d  %9s %9s  %9s %9s  %9s %9s%n",
                convName, parseName, ok, fail,
                fmtMs(percentile(convNanos, 50)),
                fmtMs(percentile(convNanos, 95)),
                fmtMs(percentile(parseNanos, 50)),
                fmtMs(percentile(parseNanos, 95)),
                fmtMs(percentile(totalNanos, 50)),
                fmtMs(percentile(totalNanos, 95)));
    }

    private static List<byte[]> loadPdfs(Path folder) throws IOException {
        List<byte[]> result = new ArrayList<>();
        try (Stream<Path> files = Files.list(folder)) {
            files.filter(p -> p.toString().toLowerCase().endsWith(".pdf"))
                    .sorted()
                    .forEach(p -> {
                        try {
                            result.add(Files.readAllBytes(p));
                        } catch (IOException ex) {
                            System.err.printf("Пропуск %s: %s%n", p, ex.getMessage());
                        }
                    });
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("В папке нет PDF: " + folder.toAbsolutePath());
        }
        return result;
    }

    private static long percentile(List<Long> sorted, int p) {
        if (sorted.isEmpty()) return 0;
        int idx = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, idx));
    }

    private static String fmtMs(long nanos) {
        double ms = nanos / 1_000_000.0;
        if (ms < 1) return String.format("%.2f ms", ms);
        if (ms < 1000) return String.format("%.0f ms", ms);
        return String.format("%.2f сек", ms / 1000.0);
    }

    private static String humanBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.0f КБ", bytes / 1024.0);
        return String.format("%.1f МБ", bytes / 1024.0 / 1024.0);
    }
}