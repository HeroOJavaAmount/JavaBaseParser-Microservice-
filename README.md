Build status: [![CI](https://github.com/HeroOJavaAmount/pdf-base-parser/actions/workflows/ci.yml/badge.svg)](https://github.com/HeroOJavaAmount/pdf-base-parser/actions) </br>
[![CI](https://github.com/HeroOJavaAmount/pdf-base-parser/actions/workflows/ci.yml/badge.svg)](https://github.com/HeroOJavaAmount/pdf-base-parser/actions/workflows/ci.yml)</br>
✅ Continuous Integration: Configured via GitHub Actions. Every commit triggers a full build and test run (55+ tests passed). Build status: [[![CI](https://github.com/HeroOJavaAmount/pdf-base-parser/actions/workflows/ci.yml/badge.svg)](https://github.com/HeroOJavaAmount/pdf-base-parser/actions/workflows/ci.yml)].

# Simple(Base) Parser — PDF Parsing Microservice
Java-приложение для извлечения структурированных данных из PDF-документов.
Принимает PDF (сертификаты, выписки, договоры), возвращает JSON с текстом,
блоками и метаданными. Поддерживает 5 способов запуска: CLI, batch, watch,
HTTP, Kafka.
Программа не однопоточная, но и не параллельная по умолчанию. (базовая) v 0.0.1-SNAPSHOT
# Не входит: 
улучшенная эвристика(улучшенные блоки, типы документов, параграфи...), многопоточность, высокая нагрузка, 
докер, оркестрация и прочее.

## Характеристики

- 5 entry points: CLI, batch, watch, HTTP, Kafka — через env PARSER_MODE
- 4 стратегии извлечения текста: PdfToText, PdfToTextAdvanced,
  PdfToTextBlockParser, PdfToTextHighload
- 2 парсера текста в блоки: Rules (цикл), Stream (Stream API)
- JWT-аутентификация для HTTP (HS256, TTL, constant-time сравнение)
- Аудит событий в JSONL: кто, когда, какой парсер, успех или ошибка
- Ремонт кодировки (MojibakeRepair): CP1251 → UTF-8 для русских PDF
- Дедупликация по SHA-256: повторный парсинг не создаёт новый файл
- Docker: multi-stage build, образ ~180 МБ, non-root пользователь
- 55 unit- и integration-тестов + два нагрузочных benchmark
- 4 демо атак на JWT: timing, tampering, expired, weak secret

## Требования

- Java 21 или выше
- Apache Maven 3.6+
- Docker (опционально, для контейнера)

## Установка

```bash
git clone https://github.com/ТВОЙ_НИК/pdf-base-parser.git
cd pdf-base-parser
mvn clean package -DskipTests
```

Готовый fat-jar: `target/pdf-statement-parser-0.1.0-SNAPSHOT.jar`.

## Использование

Режим работы задаётся переменной окружения PARSER_MODE.

### CLI — один PDF

```bash
PARSER_MODE=parse java -jar target/pdf-statement-parser-0.1.0-SNAPSHOT.jar certificate.pdf
```

Результат: `out/<sha256>.json` + запись в `audit/YYYY-MM-DD.jsonl`.

### Batch — папка PDF

```bash
PARSER_MODE=batch java -jar target/pdf-statement-parser-0.1.0-SNAPSHOT.jar src/main/resources
```

Один битый файл не останавливает batch. В конце — отчёт: сколько OK, сколько failed.

### Watch — следить за папкой

```bash
PARSER_MODE=watch java -jar target/pdf-statement-parser-0.1.0-SNAPSHOT.jar src/main/resources
```

Новый PDF в папке → автоматический парсинг. Ctrl+C для остановки.

### HTTP — REST для 1С

Без auth (dev):

```bash
PARSER_MODE=http java -jar target/pdf-statement-parser-0.1.0-SNAPSHOT.jar
```

С JWT:

```bash
PARSER_MODE=http \
PARSER_AUTH_SECRET="<32+ случайных байт>" \
PARSER_AUTH_TTL_MINUTES=60 \
java -jar target/pdf-statement-parser-0.1.0-SNAPSHOT.jar
```

Эндпоинты:

| Метод | Путь | Что делает |
|-------|------|------------|
| POST  | /api/parse | PDF → JSON (headers: X-User-Id, X-File-Name, Authorization) |
| GET   | /api/documents/{hash} | документ по SHA-256 |
| GET   | /api/documents/{hash}/audit | история событий по документу |
| GET   | /health | проверка живости (без auth) |
| GET   | /version | версия сервиса (без auth) |

Токен для JWT выпускается через утилиту:

```bash
PARSER_AUTH_SECRET="<тот же>" \
mvn exec:java "-Dexec.mainClass=ru.netology.auth.TokenGenerator" "-Dexec.args=ivanov"
```

Пример запроса:

```bash
curl -X POST -H "Authorization: Bearer <token>" \
     -H "X-User-Id: ivanov" \
     --data-binary "@certificate.pdf" \
     http://localhost:8080/api/parse
```

### Kafka — поток из топика

```bash
PARSER_MODE=kafka \
PARSER_KAFKA_BOOTSTRAP=localhost:9092 \
java -jar target/pdf-statement-parser-0.1.0-SNAPSHOT.jar
```

Читает из `pdf-input`, публикует JSON в `pdf-output`, ошибки — в `pdf-dlq`.

### Docker

```bash
docker build -t pdf-parser:0.1.0 .
docker run --rm \
    -v /data/in:/in \
    -v /data/out:/out \
    -v /data/audit:/audit \
    -e PARSER_MODE=batch \
    pdf-parser:0.1.0 /in
```

## Конфигурация

Все параметры — через env. Дефолты позволяют запустить без настройки.

| Переменная | Дефолт | Значения |
|-----------|--------|----------|
| PARSER_MODE | parse | parse / batch / watch / http / kafka |
| PARSER_CONVERTER | default | default / advanced / block / highload |
| PARSER_PARSER | rules | rules / stream |
| PARSER_SERIALIZER | json | json / csv |
| PARSER_REPAIR_MOJIBAKE | true | true / false |
| PARSER_OUT | out | путь к папке |
| PARSER_AUDIT | audit | путь к папке |
| PARSER_HTTP_PORT | 8080 | любой порт |
| PARSER_AUTH_SECRET | — | 32+ байта, JWT-секрет |
| PARSER_AUTH_TTL_MINUTES | 60 | время жизни токена |
| PARSER_OUTPUT | file | file / http / composite / null |
| PARSER_OUTPUT_HTTP_URL | — | URL для отправки JSON (например, 1С) |
| PARSER_KAFKA_BOOTSTRAP | localhost:9092 | адреса брокеров |
| PARSER_KAFKA_INPUT | pdf-input | входной топик |
| PARSER_KAFKA_OUTPUT | pdf-output | выходной топик |
| PARSER_KAFKA_DLQ | pdf-dlq | топик ошибок |
| PARSER_KAFKA_GROUP | pdf-parser | consumer group |

## Пример выходного JSON

```json
{
  "schemaVersion": "1.0",
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "hash": "bffbc94e248a0313aad99ac0708a98e4fa54b876db693c43dce68825a46bf784",
  "rawText": "Свидетельство об обучении\n...",
  "blocks": [
    { "type": "HEADING", "text": "Свидетельство", "headingLevel": 1, "tableRows": null },
    { "type": "PARAGRAPH", "text": "Подтверждает, что...", "headingLevel": null, "tableRows": null }
  ],
  "metadata": {
    "author": "ООО Ромашка",
    "title": "Свидетельство",
    "pageCount": 1,
    "createdAt": "2026-01-15T10:30:00Z",
    "sourceName": "certificate.pdf"
  }
}
```

Поле `schemaVersion` позволяет 1С-интеграции отслеживать эволюцию формата.
Старые поля не удаляются, новые — добавляются.

## Тесты

Unit и integration тесты запускаются через `mvn test` без подготовки —
PDF для интеграционного теста генерируется программно через PDFBox.

| Класс | Что проверяет |
|-------|---------------|
| BlockTest | валидация DTO, иммутабельность |
| DocumentTest | schemaVersion, копирование коллекций |
| DocumentServiceTest | оркестрация через фейковые реализации |
| DocumentServiceIntegrationTest | реальный PDF → Document (PDF генерируется в тесте) |
| JwtTokenServiceTest | TTL, tampering, чужой секрет |
| JsonDocumentSerializerTest | сериализация и round-trip |
| CsvDocumentSerializerTest | заголовки, экранирование |
| FileDocumentStorageTest | save/find/exists/дедупликация |
| FileAuditStorageTest | запись, поиск по hash и user |

```bash
mvn test        # unit + integration (~55 тестов)
mvn verify      # полная сборка + все проверки
```

### Нагрузочные тесты

`ComponentBenchmark` и `ParserLoadTest` — инструменты разработчика, не часть
`mvn test`. Требуют папку `test-in/` с PDF (в .gitignore, не коммитится).

```bash
mkdir test-in
# положите свои PDF
mvn exec:java "-Dexec.mainClass=ru.netology.loadtest.ComponentBenchmark" "-Dexec.classpathScope=test"
mvn exec:java "-Dexec.mainClass=ru.netology.loadtest.ParserLoadTest" "-Dexec.classpathScope=test"
```

Конфиг нагрузочных — в `src/test/resources/benchmark.properties`.

## Структура проекта

```
pdf-base-parser/
├── src/
│   ├── main/
│   │   ├── java/ru/netology/
│   │   │   ├── audit/          AuditEvent, AuditStorage, FileAuditStorage
│   │   │   ├── auth/           TokenService, JwtTokenService, TokenGenerator
│   │   │   ├── config/         AppConfig, AppVersion
│   │   │   ├── demo/           KafkaSendDemo
│   │   │   ├── dto/            Block, Document, DocumentMetadata
│   │   │   ├── enumConst/      BlockType, AuditEventType, DeclaredLoadErrorType
│   │   │   ├── exception/      PdfParserException + 5 наследников
│   │   │   ├── extract/        ConvertByteToText, PdfToText*, MojibakeRepair
│   │   │   ├── hackerwar/      TimingAttack, JwtTampering, Expired, WeakSecret
│   │   │   ├── load/           DeclaredLoader, File, Http, Resource
│   │   │   ├── parser/         BlockParser, HeuristicRules, HeuristicStream
│   │   │   ├── petach/         Main, Parse, Batch, Watch, HttpServer, KafkaConsumer
│   │   │   ├── serialize/      DocumentSerializer, Json, Csv
│   │   │   ├── service/        DocumentService, PdfDeclaredParser, MetadataExtractor
│   │   │   └── storage/        DocumentStorage, File, Http, Composite, Null
│   │   └── resources/
│   │       └── logback.xml
│   └── test/
│       ├── java/ru/netology/
│       │   ├── audit/          FileAuditStorageTest
│       │   ├── auth/           JwtTokenServiceTest
│       │   ├── dto/            DocumentTest
│       │   ├── loadtest/       ComponentBenchmark, ParserLoadTest, LoadTestConfig
│       │   ├── parser/         BlockTest
│       │   ├── serialize/      JsonDocumentSerializerTest, CsvDocumentSerializerTest
│       │   ├── service/        DocumentServiceTest, DocumentServiceIntegrationTest
│       │   └── storage/        FileDocumentStorageTest
│       └── resources/
│           └── benchmark.properties
├── .github/workflows/ci.yml
├── Dockerfile
├── docker-compose.yml
├── pom.xml
├── ACTION.md
├── RunProgramm.md
└── README.md
```

## Архитектура

Слоистая архитектура, зависимости идут строго вниз:

```
petach      entry points (CLI, batch, watch, HTTP, Kafka)
   ↓
service     оркестрация (DocumentService, PdfDeclaredParser)
   ↓
load / extract / parser / serialize / storage / audit
   ↓
dto / enumConst / exception
```

- DI вручную: единственное место с new для сервисов — Main
- Все реализации через интерфейсы: DeclaredLoader, ConvertByteToText,
  BlockParser, DocumentSerializer, DocumentStorage, AuditStorage, TokenService
- Каждый интерфейс имеет 2+ реализации — подмена через env без пересборки
- Нижние пакеты (dto, enumConst, exception) не знают про верхние

## Зависимости

- Apache PDFBox 3.0.3 — извлечение текста из PDF
- Caffeine 3.1.8 — кэш прочитанных файлов
- Jackson 2.17.2 — сериализация JSON
- Commons CSV 1.11.0 — формат CSV
- Kafka clients 3.9.0 — entry point kafka
- JJWT 0.12.6 — JWT-аутентификация
- JUnit 5.11.3 — тесты (test scope)
- Logback 1.5.12 — логирование

Сценарии использования:
-  1С-интеграции.
-  Docker — изолированный запуск.
-  Docker Compose — простая оркестрация.
-  Kubernetes — кластер.
-  Kafka — поток событий.
-  Batch/Watch — обработка.

## Лицензия

MIT. Проект для портфолио и учебных целей.

## Автор

[HeroOJavaAmount] — [github.com/HeroOJavaAmount/JavaBaseParser-Microservice-.git / LinkedIn]
```



