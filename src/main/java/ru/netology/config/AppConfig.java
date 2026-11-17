package ru.netology.config;

import java.nio.file.Path;

/**
 * Конфигурация приложения из env-переменных.
 * Все параметры имеют дефолты — приложение запускается без настройки.
 * Секреты задаются только через env, не коммитятся в код.
 */
public record AppConfig(
        String mode,
        String parser,
        String converter,
        String serializer,
        Path inPath,
        Path outDir,
        Path auditDir,
        int httpPort,
        boolean repairMojibake,
        String authSecret,
        long authTtlMinutes,
        String kafkaBootstrapServers,
        String kafkaInputTopic,
        String kafkaOutputTopic,
        String kafkaDlqTopic,
        String kafkaGroupId,
        // ─── куда сохранять результат ───
        String outputType,         // file | http | composite | null
        String outputHttpUrl,      // для http и composite
        String outputTargets       // для composite: "file,http"
) {

    public static AppConfig fromEnv() {
        return new AppConfig(
                env("PARSER_MODE", "parse"),
                env("PARSER_PARSER", "rules"),
                env("PARSER_CONVERTER", "default"),
                env("PARSER_SERIALIZER", "json"),
                Path.of(env("PARSER_IN", ".")),
                Path.of(env("PARSER_OUT", "out")),
                Path.of(env("PARSER_AUDIT", "audit")),
                Integer.parseInt(env("PARSER_HTTP_PORT", "8080")),
                Boolean.parseBoolean(env("PARSER_REPAIR_MOJIBAKE", "true")),
                envOrNull("PARSER_AUTH_SECRET"),
                Long.parseLong(env("PARSER_AUTH_TTL_MINUTES", "60")),
                env("PARSER_KAFKA_BOOTSTRAP", "localhost:9092"),
                env("PARSER_KAFKA_INPUT", "pdf-input"),
                env("PARSER_KAFKA_OUTPUT", "pdf-output"),
                env("PARSER_KAFKA_DLQ", "pdf-dlq"),
                env("PARSER_KAFKA_GROUP", "pdf-parser"),
                env("PARSER_OUTPUT", "file"),
                envOrNull("PARSER_OUTPUT_HTTP_URL"),
                env("PARSER_OUTPUT_TARGETS", "file,http")
        );
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }

    private static String envOrNull(String key) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? null : v;
    }
}