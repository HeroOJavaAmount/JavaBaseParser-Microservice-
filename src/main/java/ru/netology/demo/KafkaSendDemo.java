package ru.netology.demo;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import ru.netology.petach.KafkaConsumerRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Демо/утилита: отправляет PDF-файл в Kafka-топик для проверки
 * {@link KafkaConsumerRunner}.
 *
 * <p>Не часть продакшн-кода. Используется в интеграционных тестах и при
 * локальном прогоне пайплайна: после запуска consumer'а этой командой
 * «закидывается» PDF в {@code pdf-input}.
 *
 * <p><b>Формат сообщения</b>:
 * <ul>
 *   <li>key   —** {@code doc-<nanoTime>} (уникальный, чтобы легко найти в топике);</li>
 *   <li>value —** байты PDF (как есть, без сжатия/кодирования).</li>
 * </ul>
 *
 * <p><b>Использование</b> (из корня проекта):
 * <pre>{@code
 * mvn -q exec:java "-Dexec.mainClass=ru.netology.demo.KafkaSendDemo" \
 *                   "-Dexec.args=certificate.pdf"
 *
 * // С кастомным топиком:
 * mvn -q exec:java "-Dexec.mainClass=ru.netology.demo.KafkaSendDemo" \
 *                   "-Dexec.args=certificate.pdf pdf-input-custom"
 * }</pre>
 *
 * <p><b>Предусловие</b>: Kafka запущена на {@code localhost:9092}.
 * Для локального теста используйте {@code docker-compose.kafka.yml}.
 */
public class KafkaSendDemo {

    /**
     * Отправляет файл в Kafka и ждёт подтверждения от брокера.
     *
     * @param args {@code [0]} —** путь к PDF (по умолчанию {@code certificate.pdf});
     *             {@code [1]} —** имя топика (по умолчанию {@code pdf-input})
     * @throws Exception если файл не читается или брокер недоступен
     */
    public static void main(String[] args) throws Exception {
        String file = args.length > 0 ? args[0] : "certificate.pdf";
        String topic = args.length > 1 ? args[1] : "pdf-input";
        byte[] pdf = Files.readAllBytes(Path.of(file));

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);

        try (KafkaProducer<String, byte[]> p = new KafkaProducer<>(props)) {
            var meta = p.send(new ProducerRecord<>(
                    topic,
                    "doc-" + System.nanoTime(),
                    pdf
            )).get();   // .get() — ждём подтверждения (синхронно, для демо)

            System.out.printf("Sent %d bytes to %s[%d] offset=%d%n",
                    pdf.length, topic, meta.partition(), meta.offset());
        }
    }
}