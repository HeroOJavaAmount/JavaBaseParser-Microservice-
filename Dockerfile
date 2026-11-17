# ───── Стадия 1: сборка (Maven + JDK 21) ─────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Сначала pom.xml — кэш зависимостей между сборками
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Потом исходники — кэш ломается только при изменении кода
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ───── Стадия 2: runtime (только JRE) ─────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Non-root пользователь (безопасность)
RUN addgroup -S app && adduser -S app -G app

# Jar из стадии сборки
COPY --from=build /build/target/pdf-statement-parser-*.jar app.jar

# Рабочие директории (bind mount снаружи)
RUN mkdir -p /in /out /audit && chown -R app:app /app /in /out /audit
USER app

# Дефолтные env (переопределяются через docker run -e)
ENV PARSER_MODE=parse \
    PARSER_PARSER=rules \
    PARSER_CONVERTER=default \
    PARSER_SERIALIZER=json \
    PARSER_REPAIR_MOJIBAKE=true \
    PARSER_OUT=/out \
    PARSER_AUDIT=/audit \
    PARSER_HTTP_PORT=8080 \
    PARSER_KAFKA_BOOTSTRAP=localhost:9092 \
    PARSER_KAFKA_INPUT=pdf-input \
    PARSER_KAFKA_OUTPUT=pdf-output \
    PARSER_KAFKA_DLQ=pdf-dlq \
    PARSER_KAFKA_GROUP=pdf-parser

VOLUME ["/in", "/out", "/audit"]

ENTRYPOINT ["java", "-jar", "/app/app.jar"]