# 🚀 PDF Simple Parser — Запуск и проверка
**Что делает**: PDF → структурированный JSON (текст + блоки + метаданные) с аудитом.

## 📦 Шаг 0. Сборка

```powershell
mvn clean package -DskipTests
```
✅ Ожидаемо: `BUILD SUCCESS` → в `target/` появится `pdf-statement-parser-1.0-SNAPSHOT.jar`.

## 🎛️ Режимы работы (`PARSER_MODE`)

| Режим | Что делает | Кому |
|-------|-----------|------|
| `parse` | Один PDF → один JSON | Разработчик (CLI) |
| `batch` | Папка PDF → папка JSON | 1С (пачка) |
| `watch` | Следит за папкой, парсит новые | 1С (поток) |
| `http` | REST API для 1С | 1С (синхронно) |

**Общие env-переменные** (все опциональны, есть дефолты):

```powershell
$env:PARSER_OUT="out"                     # куда писать JSON
$env:PARSER_AUDIT="audit"                 # куда писать аудит (JSONL)
$env:PARSER_REPAIR_MOJIBAKE="true"        # чинить CP1251→UTF-8
```

---

## 🖥️ Режим 1: `parse` — один файл

```powershell
$env:PARSER_MODE="parse"
java -jar target\pdf-statement-parser-1.0-SNAPSHOT.jar certificate.pdf
```

✅ **Ожидаемо**:

```
Saved to out\bffbc94e...json
Parsed: hash=bffbc94e..., blocks=11, source=certificate.pdf
```

📁 **Результат**:
- `out\<sha256>.json` — структура документа
- `audit\YYYY-MM-DD.jsonl` — одна строка события

---

## 📂 Режим 2: `batch` — папка файлов

```powershell
$env:PARSER_MODE="batch"
java -jar target\pdf-statement-parser-1.0-SNAPSHOT.jar src\main\resources
```

✅ **Ожидаемо**:

```
Found 2 PDF files in src\main\resources
  OK  certificate.pdf → bffbc94e248a (11 blocks, 1444 ms)
  OK  MinObrServlet.pdf → eea20fd33f1c (9799 blocks, 4983 ms)
=== Done: 2 OK, 0 failed ===
```

💡 **Дедупликация**: если два PDF с одинаковым `hash` — один JSON на диске, два события в аудите.

---

## 👁️ Режим 3: `watch` — следит за папкой

**Терминал 1** — запуск (висит до `Ctrl+C`):

```powershell
$env:PARSER_MODE="watch"
java -jar target\pdf-statement-parser-1.0-SNAPSHOT.jar src\main\resources
```

**Терминал 2** — кидаем новый файл:

```powershell
Copy-Item C:\Downloads\new.pdf src\main\resources\
```

✅ **В Терминале 1** через ~1 сек:

```
  OK new.pdf → <новый hash>
```

🛑 **Остановить**: `Ctrl+C` (физически в Терминале 1).

---

## 🌐 Режим 4: `http` — REST API для 1С

### 🟢 Запуск без auth (dev)

```powershell
$env:PARSER_MODE="http"
$env:PARSER_HTTP_PORT="8080"
Remove-Item Env:\PARSER_AUTH_SECRET -ErrorAction SilentlyContinue
java -jar target\pdf-statement-parser-1.0-SNAPSHOT.jar
```

✅ **Ожидаемо**: `Auth: DISABLED (dev mode)`.

### 🔐 Запуск с auth (JWT)

```powershell
$env:PARSER_MODE="http"
$env:PARSER_HTTP_PORT="8080"
$env:PARSER_AUTH_SECRET="k8fJ2hP9qLxN4vR7tY1wZ3mB6cD0aE5gH8iK2jL4nM6pO="
$env:PARSER_AUTH_TTL_MINUTES="60"
java -jar target\pdf-statement-parser-1.0-SNAPSHOT.jar
```

✅ **Ожидаемо**: `Auth: JWT (HS256)`.

🛑 **Остановить**: `Ctrl+C`.

---

## 🔑 Генерация токена (для JWT) ($TOKEN —** переменная PowerShell в памяти сессии).

**В новом терминале** (пока сервер работает):

```powershell
$env:PARSER_AUTH_SECRET="k8fJ2hP9qLxN4vR7tY1wZ3mB6cD0aE5gH8iK2jL4nM6pO="
$env:PARSER_AUTH_TTL_MINUTES="60"
mvn -q exec:java "-Dexec.mainClass=ru.netology.auth.TokenGenerator" "-Dexec.args=ivanov"
```

✅ **Ожидаемо**:

```
Subject:   ivanov
Expires in:PT1H
Token:
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJpdmFub3Yi...
```

📋 **Скопируй** строку после `Token:` и присвой:

```powershell
$TOKEN = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJpdmFub3Yi..."
echo $TOKEN   # проверить, что не пусто
```

⚠️ **Секрет** `PARSER_AUTH_SECRET` **должен быть одинаковым** у сервера и `TokenGenerator`. Иначе `401`.

⚠️ **Не коммить** секрет в git. В проде — сгенерируй свой:

```powershell
[Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }))
```

---

## 🧪 Проверка HTTP-режима (`curl.exe`, Терминал 2)

### Health (без токена, всегда 200)

```powershell
curl.exe -i http://localhost:8080/health
```

✅ `HTTP/1.1 200 OK` + тело `OK`.

### Парсинг без токена → 401

```powershell
curl.exe -i -X POST --data-binary "@certificate.pdf" http://localhost:8080/api/parse
```

✅ `HTTP/1.1 401 Unauthorized` + `{"error":"Unauthorized"}`.

### Парсинг с токеном → 200

```powershell
curl.exe -i -X POST -H "Authorization: Bearer $TOKEN" `
    -H "X-User-Id: ivanov" `
    -H "X-File-Name: certificate.pdf" `
    --data-binary "@certificate.pdf" `
    http://localhost:8080/api/parse
```

✅ `HTTP/1.1 200 OK` + JSON Document.

💡 **Только статус** (без тела):

```powershell
curl.exe -s -o NUL -w "%{http_code}" -X POST -H "Authorization: Bearer $TOKEN" --data-binary "@certificate.pdf" http://localhost:8080/api/parse
# → 200
```

### История по документу

```powershell
curl.exe -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/documents/bffbc94e248a0313aad99ac0708a98e4fa54b876db693c43dce68825a46bf784/audit
```

✅ JSON-массив событий с полями `eventId`, `timestamp`, `userId`, `eventType`.

### Документ по hash

```powershell
curl.exe -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/documents/bffbc94e248a0313aad99ac0708a98e4fa54b876db693c43dce68825a46bf784
```

✅ JSON Document (как при парсинге).

---

## 📁 Что появляется на диске

```
out/
└── bffbc94e248a...json        ← Document (id, hash, blocks, metadata)

audit/
└── 2026-11-13.jsonl           ← по строке на каждое событие
```

**Пример записи аудита**:

```json
{"eventId":"...","documentHash":"bffbc94e...","timestamp":"2026-11-13T15:44:01.65Z","userId":"ivanov","parser":"rules","eventType":"PARSED","details":null}
```

💡 `timestamp` — **ISO-8601** (`2026-11-13T15:44:01.65Z`), не epoch-число.

---

## ✅ Быстрый чек-лист

- [ ] `mvn clean package` → `BUILD SUCCESS`
- [ ] `parse` на одном PDF → `Saved to out/...json`
- [ ] `batch` на папке → `N OK, 0 failed`
- [ ] `watch` → новый файл в папке → `OK new.pdf`
- [ ] `http` без секрета → `Auth: DISABLED`, запросы без токена работают
- [ ] `http` с секретом → `Auth: JWT (HS256)`, без токена `401`, с токеном `200`
- [ ] `audit/*.jsonl` содержит записи с ISO `timestamp`
- [ ] `out/*.json` содержит правильный русский текст (**не** `РЎРІРё...`)

---

## 🩺 Если что-то не так

| Симптом | Причина | Фикс |
|---------|---------|------|
| `РЎРІРё...` в JSON | Старый файл (дедупликация не перезаписала) | `Remove-Item -Recurse out` |
| `timestamp: 1794...` (число) | `FileAuditStorage` без `.disable(WRITE_DATES_AS_TIMESTAMPS)` | Добавить в конструктор + `mvn package` |
| `401` с токеном | Секрет у сервера ≠ у `TokenGenerator` | Перезапустить сервер с тем же `PARSER_AUTH_SECRET` |
| `Address already in use` | Порт 8080 занят | `$env:PARSER_HTTP_PORT="8081"` или `Get-Process java \| Stop-Process -Force` |
| `no main manifest attribute` | Jar без shade-плагина | Проверить `<mainClass>` в pom.xml |
| Крякозябры в консоли | PowerShell не UTF-8 (файлы правильные) | `chcp 65001` перед запуском |

---

## 🚦 Переменные окружения — сводка

| Переменная | Дефолт | Значения |
|-----------|--------|----------|
| `PARSER_MODE` | `parse` | `parse` / `batch` / `watch` / `http` |
| `PARSER_PARSER` | `rules` | `rules` / `stream` |
| `PARSER_CONVERTER` | `default` | `default` / `advanced` / `block` / `highload` |
| `PARSER_SERIALIZER` | `json` | `json` / `csv` |
| `PARSER_OUT` | `out` | любой путь |
| `PARSER_AUDIT` | `audit` | любой путь |
| `PARSER_REPAIR_MOJIBAKE` | `true` | `true` / `false` |
| `PARSER_HTTP_PORT` | `8080` | любой свободный порт |
| `PARSER_AUTH_SECRET` | — (auth выкл) | ≥32 байта (HMAC-SHA256) |
| `PARSER_AUTH_TTL_MINUTES` | `60` | минуты жизни JWT |

---

## 📚 См. также

- `README.md` — архитектура, диаграммы, интеграция с 1С
- `demo/README.md` — демонстрация атак (timing, tampering, expired, weak secret)
- `Dockerfile` — запуск в контейнере

