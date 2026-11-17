package ru.netology.petach;

import ru.netology.audit.AuditStorage;
import ru.netology.audit.FileAuditStorage;
import ru.netology.auth.JwtTokenService;
import ru.netology.auth.NoAuthTokenService;
import ru.netology.auth.TokenConfig;
import ru.netology.auth.TokenService;
import ru.netology.config.AppConfig;
import ru.netology.config.AppVersion;
import ru.netology.extract.ConvertByteToText;
import ru.netology.extract.PdfToText;
import ru.netology.extract.PdfToTextAdvanced;
import ru.netology.extract.PdfToTextBlockParser;
import ru.netology.extract.PdfToTextHighload;
import ru.netology.parser.BlockParser;
import ru.netology.parser.HeuristicRulesParser;
import ru.netology.parser.HeuristicStreamParser;
import ru.netology.serialize.CsvDocumentSerializer;
import ru.netology.serialize.DocumentSerializer;
import ru.netology.serialize.JsonDocumentSerializer;
import ru.netology.service.DocumentService;
import ru.netology.service.PdfDeclaredParser;
import ru.netology.storage.CompositeDocumentStorage;
import ru.netology.storage.DocumentStorage;
import ru.netology.storage.FileDocumentStorage;
import ru.netology.storage.HttpDocumentStorage;
import ru.netology.storage.NullDocumentStorage;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Точка входа и единственное место сборки графа объектов (DI вручную).
 *
 * <p>Порядок:
 * <ol>
 *   <li>Читает {@link AppConfig} из env.</li>
 *   <li>Создаёт реализаци: converter, parser, serializer, storage.</li>
 *   <li>Собирает сервисы через конструкторы.</li>
 *   <li>Создаёт {@link TokenService} (JWT или dev-заглушка).</li>
 *   <li>Запускает entry point по {@code PARSER_MODE}.</li>
 * </ol>
 */
public class Main {

    public static void main(String[] args) throws Exception {
        System.out.println(AppVersion.full());

        AppConfig cfg = AppConfig.fromEnv();

        // 1. Converter
        ConvertByteToText converter = switch (cfg.converter()) {
            case "default"  -> new PdfToText();
            case "advanced" -> new PdfToTextAdvanced();
            case "block"    -> new PdfToTextBlockParser();
            case "highload" -> new PdfToTextHighload();
            default -> throw new IllegalArgumentException("Unknown converter: " + cfg.converter());
        };

        // 2. BlockParser
        BlockParser blockParser = switch (cfg.parser()) {
            case "rules"  -> new HeuristicRulesParser();
            case "stream" -> new HeuristicStreamParser();
            default -> throw new IllegalArgumentException("Unknown parser: " + cfg.parser());
        };

        // 3. Serializer
        DocumentSerializer serializer = switch (cfg.serializer()) {
            case "json" -> new JsonDocumentSerializer();
            case "csv"  -> new CsvDocumentSerializer();
            default -> throw new IllegalArgumentException("Unknown serializer: " + cfg.serializer());
        };

        // 4. Storage (куда уходит результат)
        DocumentStorage storage = buildStorage(cfg);

        // 5. Сервисы через конструкторы
        PdfDeclaredParser pdfParser = new PdfDeclaredParser(converter);
        DocumentService service = new DocumentService(pdfParser, blockParser);
        AuditStorage audit = new FileAuditStorage(cfg.auditDir());

        // 6. TokenService
        TokenConfig tokenConfig = new TokenConfig(
                cfg.authSecret(),
                Duration.ofMinutes(cfg.authTtlMinutes())
        );
        TokenService tokenService = tokenConfig.enabled()
                ? new JwtTokenService(tokenConfig)
                : new NoAuthTokenService();

        String userId = System.getProperty("user.name", "anonymous");

        // 7. Entry point
        switch (cfg.mode()) {
            case "parse" -> {
                int code = new ParseCommand(service, serializer, storage, audit, cfg.outDir(), cfg.repairMojibake())
                        .run(args, userId, cfg.parser());
                System.exit(code);
            }
            case "batch" -> {
                int code = new BatchCommand(service, serializer, storage, audit, cfg.repairMojibake())
                        .run(args, userId, cfg.parser());
                System.exit(code);
            }
            case "watch" -> {
                int code = new WatchCommand(service, serializer, storage, audit, cfg.repairMojibake())
                        .run(args, userId, cfg.parser());
                System.exit(code);
            }
            case "http" -> new HttpServerRunner(service, serializer, storage, audit,
                    cfg.parser(), cfg.repairMojibake(), tokenService)
                    .run(cfg.httpPort());
            case "kafka" -> new KafkaConsumerRunner(service, serializer, storage, audit,
                    cfg.parser(), cfg.repairMojibake(),
                    cfg.kafkaBootstrapServers(), cfg.kafkaInputTopic(),
                    cfg.kafkaOutputTopic(), cfg.kafkaDlqTopic(), cfg.kafkaGroupId())
                    .run();
            default -> throw new IllegalArgumentException("Unknown mode: " + cfg.mode());
        }
    }

    /**
     * Собирает {@link DocumentStorage} по {@code PARSER_OUTPUT}.
     * file | http | composite | null
     */
    private static DocumentStorage buildStorage(AppConfig cfg) {
        return switch (cfg.outputType()) {
            case "file" -> new FileDocumentStorage(cfg.outDir());
            case "http" -> {
                if (cfg.outputHttpUrl() == null) {
                    throw new IllegalStateException(
                            "PARSER_OUTPUT=http requires PARSER_OUTPUT_HTTP_URL");
                }
                yield new HttpDocumentStorage(cfg.outputHttpUrl());
            }
            case "composite" -> {
                List<DocumentStorage> delegates = new ArrayList<>();
                for (String target : cfg.outputTargets().split(",")) {
                    switch (target.trim()) {
                        case "file" -> delegates.add(new FileDocumentStorage(cfg.outDir()));
                        case "http" -> {
                            if (cfg.outputHttpUrl() == null) {
                                throw new IllegalStateException(
                                        "PARSER_OUTPUT=composite with http requires PARSER_OUTPUT_HTTP_URL");
                            }
                            delegates.add(new HttpDocumentStorage(cfg.outputHttpUrl()));
                        }
                        case "null" -> delegates.add(new NullDocumentStorage(false));
                        default -> throw new IllegalArgumentException(
                                "Unknown output target: " + target);
                    }
                }
                yield new CompositeDocumentStorage(delegates);
            }
            case "null" -> new NullDocumentStorage();
            default -> throw new IllegalArgumentException(
                    "Unknown PARSER_OUTPUT: " + cfg.outputType());
        };
    }
}