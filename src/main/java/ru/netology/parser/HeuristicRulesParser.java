package ru.netology.parser;

import ru.netology.dto.Block;
import java.util.ArrayList;
import java.util.List;

public class HeuristicRulesParser implements BlockParser {

    /**
     * List-список<br>
     * Block-смысловой блок текста, то есть абзац,единица будущего списка.
     * @param text
     * @metods То что отправиться в classify на эвристику. Пустая строка
     * @return Готовый список текста
     */
    @Override
    public List<Block> parse(String text) {
        List<Block> blocks = new ArrayList<>();
        for (String line : text.split("\\R")) {
            if (line.isBlank()) continue;
            blocks.add(classify(line.trim()));
        }
        return blocks;
    }

    /**Простая эвристика
     * @param line
     * @metods Задаёт 3 вопроса сразу и требует « да » на все<br><br>похоже на заголовок <br>^[А-ЯA-Z]	начинается с заглавной (рус/лат)<br>[^.!?]{0,80}	до 80 символов без . ! ?<br>$	вся строка целиком<br><br>похожа на список<br>^	начало строки
     * [-•*]	один символ из трёх: - или • или *<br>\s+	один или больше пробелов<br>.*	любые символы дальше (текст пункта)
     * <br>
     * @return "Отчёт за январь" → ✅ HEADING, <br>"01.01.2025" → ❌ (начинается с цифры)
     */
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