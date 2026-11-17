package ru.netology.load;

import ru.netology.exception.DeclaredLoaderException;

import java.io.IOException;
import java.io.InputStream;

/**
 * Загружает содержимое statement из <b>ресурса classpath</b>.
 *
 * <p>Подходит для случаев, когда файл упакован внутрь jar/war/образа
 * контейнера и не лежит на файловой системе «как файл». Для настоящих
 * файлов на диске используйте {@link DeclaredLoadFile}.
 *
 * <p>Имя ресурса трактуется относительно корня classpath:
 * <pre>
 *   "statements/sample.pdf"   →  /statements/sample.pdf в classpath
 *   "/statements/sample.pdf"  →  то же самое (ведущий '/' отбрасывается)
 * </pre>
 */
public class DeclaredLoadResource implements DeclaredLoader {

    private final String resourceName;
    private final ClassLoader classLoader;

    /**
     * Использует context class loader текущего потока — стандартный
     * подход для war/контейнеров, где он может отличаться от системного.
     */
    public DeclaredLoadResource(String resourceName) {
        this(resourceName, Thread.currentThread().getContextClassLoader());
    }

    /**
     * @param resourceName имя ресурса в classpath (без ведущего '/' или с ним — не важно)
     * @param classLoader  загрузчик; если {@code null}, берётся загрузчик этого класса
     */
    public DeclaredLoadResource(String resourceName, ClassLoader classLoader) {
        if (resourceName == null || resourceName.isBlank()) {
            throw new IllegalArgumentException("resourceName must not be null or blank");
        }
        // ClassLoader ищет от корня classpath, ведущий '/' ему не нужен
        this.resourceName = resourceName.startsWith("/")
                ? resourceName.substring(1)
                : resourceName;
        this.classLoader = classLoader != null
                ? classLoader
                : DeclaredLoadResource.class.getClassLoader();
    }

    @Override
    public byte[] getContent() {
        try (InputStream in = classLoader.getResourceAsStream(resourceName)) {
            if (in == null) {
                // Ресурс отсутствует — это ошибка конфигурации, а не IOException
                throw new DeclaredLoaderException(
                        "Resource not found on classpath: " + resourceName);
            }
            return in.readAllBytes();
        } catch (IOException ex) {
            throw new DeclaredLoaderException(
                    "Can not read resource: " + resourceName, ex);
        }
    }
}