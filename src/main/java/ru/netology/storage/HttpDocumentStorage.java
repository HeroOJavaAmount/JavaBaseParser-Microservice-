package ru.netology.storage;

import ru.netology.exception.StorageException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Отправляет JSON-документ на удалённый HTTP-эндпоинт (например, 1С).
 * Sends JSON document to a remote HTTP endpoint (e.g. 1C).
 *
 * <p>URL формируется как {@code baseUrl + "/" + hash}.
 * Ответ с кодом {@code 2xx} считается успехом, иначе {@link StorageException}.
 */
public class HttpDocumentStorage implements DocumentStorage {

    private final String baseUrl;
    private final HttpClient client;

    /**
     * @param baseUrl базовый URL, например {@code http://1c-server/import}
     *                (без слэша на конце)
     */
    public HttpDocumentStorage(String baseUrl) {
        this(baseUrl, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    /**
     * Для тестов — передать свой {@link HttpClient}.
     */
    public HttpDocumentStorage(String baseUrl, HttpClient client) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl")
                .replaceAll("/+$", "");
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public void save(String hash, String serializedContent) {
        String url = baseUrl + "/" + hash;
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(serializedContent))
                .build();
        try {
            HttpResponse<Void> resp = client.send(req, HttpResponse.BodyHandlers.discarding());
            int code = resp.statusCode();
            if (code / 100 != 2) {
                throw new StorageException("HTTP " + code + " from " + url);
            }
        } catch (StorageException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new StorageException("Interrupted while POST to " + url, ex);
        } catch (Exception ex) {
            throw new StorageException("Failed to POST to " + url + ": " + ex.getMessage(), ex);
        }
    }

    @Override
    public Optional<String> findByHash(String hash) {
        // HTTP-storage только отправляет — не хранит
        return Optional.empty();
    }

    @Override
    public boolean exists(String hash) {
        // Не знаем — не проверяем
        return false;
    }
}