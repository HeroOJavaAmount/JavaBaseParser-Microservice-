# 📋 ACTION — команды запуска PDF Statement Parser

**Сборка**:
```powershell
mvn clean package -DskipTests
# → target/pdf-statement-parser-1.0-SNAPSHOT.jar
```

### Нагрузочные (loadtest) интграционные тесты. 

# 1. Запусти сервер в другом терминале
$env:PARSER_MODE="http"
$env:PARSER_HTTP_PORT="8080"
java -jar target\pdf-statement-parser-1.0-SNAPSHOT.jar
# → Auth: DISABLED (dev mode)

# 3. Запусти тест в терминале или через IDE.  LoadTestConfig для переменных окружения папка и прочее, 
# запускаем ComponentBenchmark для теста модулей программы, нагружаем нужный модуль ParserLoadTest.
mvn exec:java "-Dexec.mainClass=ru.netology.loadtest.ParserLoadTest" "-Dexec.classpathScope=test" "-Dexec.args=test-in 1000 50"

Конфигурация в env
PARSER_OUTPUT=file                          # file | http | composite | null
PARSER_OUTPUT_FILE_DIR=out                  # для file
PARSER_OUTPUT_HTTP_URL=http://1c/import     # для http
PARSER_OUTPUT_TARGETS=file,http             # для composite

# Тесты
mvn verify зелёный

~~~
Как использовать из консоли
По умолчанию — в папку out/:
powershell
~~~
$env:PARSER_MODE="parse"
java -jar target/simple-parser-1.0-SNAPSHOT.jar .....pdf

~~~
Отправить в 1С по HTTP:
powershell
~~~
$env:PARSER_MODE="http"
$env:PARSER_OUTPUT="http"
$env:PARSER_OUTPUT_HTTP_URL="http://1c-server:8080/import/pdf"
java -jar target/simple-parser-1.0-SNAPSHOT.jar

~~~
И в файл, и в 1С (надёжность):
powershell
~~~
$env:PARSER_OUTPUT="composite"
$env:PARSER_OUTPUT_TARGETS="file,http"
$env:PARSER_OUTPUT_HTTP_URL="http://1c-server:8080/import/pdf"

---

## 🖥️ Локально (без Docker)

### 1. `parse` — один PDF

```powershell
$env:PARSER_MODE="parse"
java -jar target\pdf-statement-parser-1.0-SNAPSHOT.jar .....pdf
```

→ `out\<sha256>.json` + `audit\YYYY-MM-DD.jsonl`

### 2. `batch` — папка PDF

```powershell
$env:PARSER_MODE="batch"
java -jar target\название.jar src\main\resources
```

→ `N OK, 0 failed`, все JSON в `out/`

### 3. `watch` — следит за папкой

```powershell
$env:PARSER_MODE="watch"
java -jar target\название.jar src\main\resources
# В другом терминале: Copy-Item some.pdf src\main\resources\
```

→ при появлении файла: `OK some.pdf → <hash>`

### 4. `http` — REST для 1С

**Без auth (dev)**:
```powershell
$env:PARSER_MODE="http"
$env:PARSER_HTTP_PORT="8080"
java -jar target\название.jar
# → Auth: DISABLED (dev mode)
```

**С auth (JWT)**:
```powershell
$env:PARSER_MODE="http"
$env:PARSER_HTTP_PORT="8080"
$env:PARSER_AUTH_SECRET="<32+ random bytes>"
$env:PARSER_AUTH_TTL_MINUTES="60"
java -jar target\название.jar
# → Auth: JWT (HS256)
```

**Токен** (в другом терминале):
```powershell
$env:PARSER_AUTH_SECRET="<тот же>"
mvn -q exec:java "-Dexec.mainClass=ru.netology.auth.TokenGenerator" "-Dexec.args=ivanov"
$TOKEN = "<вставить сюда>"
```

**Запрос**:
```powershell
curl.exe -X POST -H "Authorization: Bearer $TOKEN" -H "X-User-Id: ivanov" `
    --data-binary "название.pdf" http://localhost:8080/api/parse
```

**Эндпоинты**:
| Метод | Путь | Что |
|-------|------|-----|
| POST  | `/api/parse` | PDF → JSON |
| GET   | `/api/documents/{hash}` | документ по hash |
| GET   | `/api/documents/{hash}/audit` | история событий |
| GET   | `/health` | 200 OK (без auth) |

### 5. `kafka` — поток из Kafka

```powershell
$env:PARSER_MODE="kafka"
$env:PARSER_KAFKA_BOOTSTRAP="localhost:9092"
java -jar target\название.jar
```

**Отправить PDF** (для теста):
```powershell
mvn -q exec:java "-Dexec.mainClass=ru.netology.demo.KafkaSendDemo" "-Dexec.args=certificate.pdf"
```

---

## 🐳 Docker

### Сборка образа

```powershell
docker build -t pdf-parser:1.0 .
```

### Запуск: `parse` (один файл)

```powershell
docker run --rm `
    -v ${PWD}:/in `
    -v ${PWD}/out:/out `
    -v ${PWD}/audit:/audit `
    pdf-parser:1.0 parse /in/название.pdf
```

### Запуск: `batch` (папка)

```powershell
docker run --rm `
    -v /data/incoming:/in `
    -v /data/processed:/out `
    -v /data/audit:/audit `
    -e PARSER_MODE=batch `
    pdf-parser:1.0 /in
```

### Запуск: `watch` (следит за папкой)

```powershell
docker run -d --name parser-watch `
    -v /data/incoming:/in `
    -v /data/processed:/out `
    -v /data/audit:/audit `
    -e PARSER_MODE=watch `
    pdf-parser:1.0 /in
```

### Запуск: `http` (REST с auth)

```powershell
docker run -d --name parser-http `
    -p 8080:8080 `
    -v /data/out:/out `
    -v /data/audit:/audit `
    -e PARSER_MODE=http `
    -e PARSER_AUTH_SECRET="<32+ random bytes>" `
    pdf-parser:1.0
```

### Запуск: `kafka`

```powershell
docker run -d --name parser-kafka `
    -e PARSER_MODE=kafka `
    -e PARSER_KAFKA_BOOTSTRAP="broker1.company.local:9092,broker2:9092" `
    -v /data/out:/out `
    -v /data/audit:/audit `
    pdf-parser:1.0
```

---

