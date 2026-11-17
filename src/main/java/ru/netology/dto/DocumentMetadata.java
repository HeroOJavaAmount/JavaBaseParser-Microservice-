package ru.netology.dto;

import java.time.Instant;

public record DocumentMetadata(
    String author,
    String title,
    int pageCount,
    Instant createdAt,
    String sourceName                    // имя файла или resourceName
) {}