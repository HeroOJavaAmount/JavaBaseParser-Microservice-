package ru.netology.serialize;

import ru.netology.dto.Document;

public interface DocumentSerializer {
    String serialize(Document document);
    String contentType();               // "application/json", "text/csv"
    String fileExtension();             // "json", "csv"
}