package ru.netology.petach;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import ru.netology.audit.AuditEvent;
import ru.netology.audit.AuditStorage;
import ru.netology.dto.Document;
import ru.netology.serialize.DocumentSerializer;
import ru.netology.service.DocumentService;
import ru.netology.storage.DocumentStorage;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Kafka entry point для потоковой обработки PDF.
 *
 * <p><b>Пайплайн</b>:
 * <pre>
 *   pdf-input  ──▶  parse  ──▶  storage.save + audit.record
 *                                   │
 *                                   ├──▶  pdf-output  (JSON Document)
 *                                   └──▶  pdf-dlq     (ошибки)
 * </pre>
 *
 * <p><b>Гарантии доставки</b>: at-least-once. Ручной commit после обработки
 * пачки ({@link KafkaConsumer#commitSync()}). Если процесс упадёт между
 * обработкой и commit'ом —** сообщение будет перечитано. Идемпотентность
 * обеспечивается дедупликацией по SHA-256 в {@link DocumentStorage}
 * (повторный парсинг того же PDF не пересоздаёт файл).
 *
 * <p><b>Масштабирование</b>: горизонтальное. Количество consumer'ов в группе
 * ({@code PARSER_KAFKA_GROUP}) не должно превышать число partition'ов
 * в {@code pdf-input} —** иначе лишние будут простаивать.
 *
 * <p><b>DLQ (Dead Letter Queue)</b>: при любой ошибке парсинга (битый PDF,
 * скан без текста, ошибка I/O) сообщение не теряется —** оно уходит в
 * {@code PARSER_KAFKA_DLQ} с JSON {@code {"sourceName":..., "error":...}}.
 * Это позволяет разобрать инцидент и переотправить файл позже.
 *
 * <p><b>Graceful shutdown</b>: {@code Ctrl+C} →** {@code shutdownHook} ставит
 * {@code running = false} →** цикл выходит из {@code poll} →** consumer и
 * producer закрываются через try-with-resources.
 *
 * <p><b>Конфигурация</b> (env, см. {@link ru.netology.config.AppConfig}):
 * <ul>
 *   <li>{@code PARSER_KAFKA_BOOTSTRAP} —** адреса брокеров;</li>
 *   <li>{@code PARSER_KAFKA_INPUT}   —** входной топик (PDF bytes);</li>
 *   <li>{@code PARSER_KAFKA_OUTPUT}  —** выходной топик (JSON); пусто = отключить;</li>
 *   <li>{@code PARSER_KAFKA_DLQ}     —** топик ошибок;</li>
 *   <li>{@code PARSER_KAFKA_GROUP}   —** consumer group id.</li>
 * </ul>
 */
public class KafkaConsumerRunner {

    private final DocumentService service;
    private final DocumentSerializer serializer;
    private final DocumentStorage storage;
    private final AuditStorage audit;
    private final String parserName;
    private final boolean repairMojibake;

    private final String bootstrapServers;
    private final String inputTopic;
    private final String outputTopic;
    private final String dlqTopic;
    private final String groupId;

    /** Флаг graceful shutdown: {@code false} после {@code Ctrl+C}. */
    private final AtomicBoolean running = new AtomicBoolean(true);

    /**
     * @param service          оркестратор «PDF → Document»
     * @param serializer       сериализатор Document → JSON
     * @param storage          хранилище JSON-результатов (по hash)
     * @param audit            аудит (PARSED / FAILED)
     * @param parserName       имя парсера для аудита (rules / stream)
     * @param repairMojibake   чинить CP1251→UTF-8 (см. {@code PdfDeclaredParser})
     * @param bootstrapServers адрес(а) брокеров: {@code host1:9092,host2:9092}
     * @param inputTopic       топик с PDF (bytes)
     * @param outputTopic      топик с JSON; пустая строка = не публиковать
     * @param dlqTopic         топик ошибок
     * @param groupId          consumer group id
     */
    public KafkaConsumerRunner(DocumentService service,
                               DocumentSerializer serializer,
                               DocumentStorage storage,
                               AuditStorage audit,
                               String parserName,
                               boolean repairMojibake,
                               String bootstrapServers,
                               String inputTopic,
                               String outputTopic,
                               String dlqTopic,
                               String groupId) {
        this.service = service;
        this.serializer = serializer;
        this.storage = storage;
        this.audit = audit;
        this.parserName = parserName;
        this.repairMojibake = repairMojibake;
        this.bootstrapServers = bootstrapServers;
        this.inputTopic = inputTopic;
        this.outputTopic = outputTopic;
        this.dlqTopic = dlqTopic;
        this.groupId = groupId;
    }

    /**
     * Запускает блокирующий цикл обработки.
     *
     * <p>Не возвращает управление, пока не придёт {@code Ctrl+C} (SIGINT)
     * или не случится фатальная ошибка Kafka. Все промежуточные ошибки
     * обрабатываются внутри {@link #handle(ConsumerRecord, KafkaProducer)}
     * —** сообщение уходит в DLQ, цикл продолжается.
     *
     * <p>Метод <b>не</b> бросает checked-исключений: любая ошибка подключения
     * логируется и приводит к завершению. Для повторного запуска нужен
     * рестарт процесса —** это штатное поведение для consumer'а в контейнере
     * (Kubernetes / systemd перезапустят).
     */
    public void run() {
        System.out.println("Kafka consumer starting...");
        System.out.println("  bootstrap: " + bootstrapServers);
        System.out.println("  input:     " + inputTopic);
        System.out.println("  output:    " + (outputTopic.isBlank() ? "<disabled>" : outputTopic));
        System.out.println("  dlq:       " + dlqTopic);
        System.out.println("  group:     " + groupId);
        System.out.println("Press Ctrl+C to stop.");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Stopping Kafka consumer...");
            running.set(false);
        }));

        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");   // ручной commit → at-least-once
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.ACKS_CONFIG, "all");   // acks=all — надёжность

        try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(consumerProps);
             KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {

            consumer.subscribe(List.of(inputTopic));

            while (running.get()) {
                ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, byte[]> record : records) {
                    handle(record, producer);
                }
                if (!records.isEmpty()) {
                    consumer.commitSync();   // коммит пачки
                }
            }
        } catch (Exception ex) {
            System.err.println("Kafka runner failed: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * Обрабатывает одно сообщение: парсит PDF, сохраняет JSON, публикует
     * в {@code output}, при ошибке —** в {@code dlq}.
     *
     * <p><b>Не бросает исключений</b> —** любая ошибка перехватывается и
     * превращается в запись в DLQ. Это гарантирует, что одно «плохое»
     * сообщение не остановит весь поток.
     *
     * @param record   сообщение из {@code pdf-input}: key —** имя файла (опц.),
     *                 value —** байты PDF
     * @param producer продюсер для публикации в {@code output} / {@code dlq}
     */
    private void handle(ConsumerRecord<String, byte[]> record,
                        KafkaProducer<String, String> producer) {
        String msgKey = record.key();
        byte[] pdf = record.value();
        String sourceName = (msgKey != null ? msgKey : "kafka-" + record.offset()) + ".pdf";

        long start = System.currentTimeMillis();
        try {
            Document doc = service.parse(pdf, repairMojibake, sourceName);
            String json = serializer.serialize(doc);

            storage.save(doc.hash(), json);
            audit.record(AuditEvent.parsed(doc.hash(), "kafka:" + groupId, parserName, null));

            if (!outputTopic.isBlank()) {
                producer.send(new ProducerRecord<>(outputTopic, doc.hash(), json));
            }

            long ms = System.currentTimeMillis() - start;
            System.out.printf("  OK %s → %s (%d blocks, %d ms)%n",
                    sourceName, doc.hash().substring(0, 12), doc.blocks().size(), ms);

        } catch (Exception ex) {
            String errorMsg = "{\"sourceName\":\"" + escape(sourceName)
                    + "\",\"error\":\"" + escape(ex.getMessage()) + "\"}";
            producer.send(new ProducerRecord<>(dlqTopic, msgKey, errorMsg));
            audit.record(AuditEvent.failed("unknown", "kafka:" + groupId,
                    parserName, null, ex.getMessage()));
            System.err.printf("  FAIL %s → %s (→ %s)%n",
                    sourceName, ex.getMessage(), dlqTopic);
        }
    }

    /** Экранирует кавычки для встраивания в JSON-строку. */
    private static String escape(String s) {
        return s == null ? "" : s.replace("\"", "\\\"");
    }
}