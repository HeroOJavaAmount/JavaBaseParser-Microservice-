package ru.netology.load;

import ru.netology.exception.DeclaredLoadHttpException;
import ru.netology.enumConst.DeclaredLoadErrorType;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;

/**
 * Загружает содержимое объявленного (declared) ресурса из сети по протоколу HTTP/HTTPS.
 *
 * <p>Подходит для случаев, когда Docker-контейнер должен скачивать
 * файлы из внешнего публичного или приватного хранилища (например, AWS S3, MinIO).
 */
public class DeclaredLoadHttp implements DeclaredLoader {

    private final String url;
    private final HttpClient httpClient;

    /**
     * Создает загрузчик с дефолтным HttpClient.
     * Оптимально для стандартного использования в изолированном контейнере.
     */
    public DeclaredLoadHttp(String url) {
        this(url, HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10)) // Защита от зависания контейнера при старте
                .build());
    }

    /**
     * Конструктор для кастомизации клиента (например, для настройки прокси или SSL).
     *  @param url HTTP/HTTPS-адрес ресурса; не {@code null} и не пустой
     *  @throws IllegalArgumentException если {@code url == null} или пустой
     */
    public DeclaredLoadHttp(String url, HttpClient httpClient) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL must not be null or blank");
        }
        if (httpClient == null) {
            throw new IllegalArgumentException("HttpClient must not be null");
        }
        this.url = url;
        this.httpClient = httpClient;
    }

    @Override
    public byte[] getContent() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30)) // Ограничение времени на чтение данных
                    .GET()
                    .build();

            // Выполняем запрос. Поток не блокирует ядро ОС благодаря архитектуре HttpClient. строка скачивает файл по ссылке.
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

//            // Скачать сразу в файл, не в память:  Для больших файлов — другой handler:
//            HttpResponse<Path> response = httpClient.send(
//                    request,
//                    HttpResponse.BodyHandlers.ofFile(Path.of("download.pdf"))
//            );

            int status = response.statusCode();
            if (status != 200) {
                // Разделяем 4xx (ошибка клиента) и 5xx (ошибка сервера)
                DeclaredLoadErrorType type = (status >= 400 && status < 500)
                        ? DeclaredLoadErrorType.CLIENT_ERROR
                        : DeclaredLoadErrorType.SERVER_ERROR;

                throw new DeclaredLoadHttpException("Server replied with error status", status, type, url);
            }

            return response.body();

        } catch (IllegalArgumentException e) {
            throw new DeclaredLoadHttpException("Invalid URL format supplied", DeclaredLoadErrorType.INVALID_SOURCE, url, e);

        } catch (HttpTimeoutException e) {
            // Точечно ловим таймауты Java HTTP-клиента
            throw new DeclaredLoadHttpException("Connection or read timeout expired", DeclaredLoadErrorType.TIMEOUT, url, e);

        } catch (ConnectException e) {
            // Точечно ловим падение сети или недоступность хоста
            throw new DeclaredLoadHttpException("Remote host is unreachable", DeclaredLoadErrorType.NETWORK_FAILURE, url, e);

        } catch (IOException e) {
            // Все остальные непредвиденные проблемы ввода-вывода
            throw new DeclaredLoadHttpException("General network I/O failure", DeclaredLoadErrorType.NETWORK_FAILURE, url, e);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DeclaredLoadHttpException("Download thread execution was interrupted", DeclaredLoadErrorType.INTERRUPTED, url, e);
        }
    }
}
