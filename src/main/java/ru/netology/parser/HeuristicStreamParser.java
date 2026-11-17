package ru.netology.parser;

import ru.netology.dto.Block;

import java.util.Arrays;
import java.util.List;

/**
 * Вторая реализация {@link BlockParser} — та же эвристика, что в
 * {@link HeuristicRulesParser}, но {@code parse} написан через Stream API.
 *
 * <p>Используется для сравнения подходов (цикл vs стрим) и как демонстрация
 * того, что {@link ru.netology.service.DocumentService} работает с любым
 * {@code BlockParser}.
 */
public class HeuristicStreamParser implements BlockParser {

    @Override
    public List<Block> parse(String text) {
        return Arrays.stream(text.split("\\R"))
                .filter(line -> !line.isBlank())
                .map(String::trim)
                .map(this::classify)
                .toList();
    }

//     return Arrays.stream(text.split("\\R\\s*\\R"))     // уже абзацы целиком
//            .filter(p -> !p.isBlank())
//            .map(p -> p.replaceAll("\\R", " ").trim())   // склеить строки ВНУТРИ абзаца
//            .map(this::classify)
//        .toList();
//}

    private Block classify(String line) {
        if (line.matches("^[А-ЯA-Z][^.!?]{0,80}$")) {
            return Block.heading(1, line);
        }
        if (line.matches("^[-•*]\\s+.*")) {
            return Block.listItem(line);
        }
        return Block.paragraph(line);
    }
}