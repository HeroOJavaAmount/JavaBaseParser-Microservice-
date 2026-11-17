package ru.netology.serialize;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import ru.netology.dto.Document;
import ru.netology.exception.SerializeException;

public class JsonDocumentSerializer implements DocumentSerializer {
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);// Когда пойдёшь в прод — убери INDENT_OUTPUT (компактнее в сети).

    @Override
    public String serialize(Document document) {
        try {
            return mapper.writeValueAsString(document);
        } catch (JsonProcessingException ex) {
            throw new SerializeException("Can not serialize to JSON", ex);
        }
    }

    @Override public String contentType() { return "application/json"; }
    @Override public String fileExtension() { return "json"; }
}