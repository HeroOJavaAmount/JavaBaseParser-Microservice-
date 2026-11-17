package ru.netology.config;

import java.io.InputStream;
import java.util.jar.Manifest;

/**
 * Информация о версии из jar-манифеста.
 * Version info loaded from jar manifest.
 *
 * <p>Читает {@code Implementation-Version} и {@code Implementation-Title},
 * которые Maven записывает при сборке. Если запуск из IDE (не из jar) —
 * значения {@code "dev"} и {@code "Simple Parser"}.
 */
public final class AppVersion {

    private static final String VERSION = read("Implementation-Version", "dev");
    private static final String TITLE = read("Implementation-Title", "Simple Parser");

    private AppVersion() {}

    /** Только версия, например "1.0.0". */
    public static String version() {
        return VERSION;
    }

    /** Только название, например "Base Parser". */
    public static String title() {
        return TITLE;
    }

    /** Полная строка для логов: "Base Parser v1.0.0". */
    public static String full() {
        return TITLE + " v" + VERSION;
    }

    private static String read(String key, String fallback) {
        try (InputStream in = AppVersion.class.getResourceAsStream("/META-INF/MANIFEST.MF")) {
            if (in == null) return fallback;
            String value = new Manifest(in).getMainAttributes().getValue(key);
            return value != null ? value : fallback;
        } catch (Exception ex) {
            return fallback;
        }
    }
}