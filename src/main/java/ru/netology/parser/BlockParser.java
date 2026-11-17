package ru.netology.parser;

import ru.netology.dto.Block;
import java.util.List;

public interface BlockParser {
    List<Block> parse(String text);
}
// Сделаем абстрактный класс (parse) через enum (евристика)