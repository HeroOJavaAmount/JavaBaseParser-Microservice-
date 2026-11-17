package ru.netology.load;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import ru.netology.exception.DeclaredLoaderException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;

public class DeclaredLoadFile implements DeclaredLoader {

    private static final Cache<Path, byte[]> CACHE = Caffeine.newBuilder()
            .maximumWeight(500L * 1024 * 1024)
            .weigher((Path key, byte[] value) -> value.length)
            .expireAfterAccess(Duration.ofMinutes(10))
            .recordStats()
            .build();

    private final Path path;

    public DeclaredLoadFile(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("fileName must not be null or blank");
        }
        try {
            this.path = Path.of(fileName).toAbsolutePath().normalize();
        } catch (InvalidPathException ex) {
            throw new IllegalArgumentException("Invalid file path: " + fileName, ex);
        }
    }

    @Override
    public byte[] getContent() {
        return CACHE.get(path, p -> {
            try {
                return Files.readAllBytes(p);
            } catch (IOException ex) {
                throw new DeclaredLoaderException("Can not load file: " + p, ex);
            }
        });
    }
}