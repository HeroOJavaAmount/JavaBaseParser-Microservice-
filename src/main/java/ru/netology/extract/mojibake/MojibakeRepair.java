package ru.netology.extract.mojibake;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Исправление "mojibake" — текста, в котором UTF-8 байты
 * были ошибочно интерпретированы как CP1251 (Windows-1251).
 *
 * <p>Типичный случай: PDF хранит UTF-8 байты, но в шрифте заявлена
 * кодировка CP1251. PDFBox читает как CP1251 → получаем "РЎРІРёРґРµС‚РµР»СЊСЃС‚РІРѕ"
 * вместо "Свидетельство".
 *
 * <p>Решение: если текст содержит характерные «маркеры» mojibake
 * (буквы {@code Р}, {@code С}, {@code Ў}, {@code І}, {@code ё}, {@code ґ}...),
 * перекодируем: String → CP1251 bytes → UTF-8 String.
 */
public final class MojibakeRepair {

    private static final Charset CP1251 = Charset.forName("windows-1251");

    private MojibakeRepair() {}

    /**   Временно Валидируем произвольным колличеством , int quantity
     * Возвращает исправленный текст, если он был в mojibake.
     * Если текст выглядит нормально или починить не удалось — возвращает исходный.
     */
    public static String repair(String text) {
        if (text == null || text.isBlank()) return text;
        if (!looksLikeMojibake(text)) return text;

        try {
            byte[] cp1251Bytes = text.getBytes(CP1251);
            String candidate = new String(cp1251Bytes, StandardCharsets.UTF_8);
            // Проверяем, что кандидат «лучше» исходного (меньше артефактов)
            return score(candidate) > score(text) ? candidate : text;
        } catch (Exception ex) {
            return text;
        }
    }

    /**
     * Эвристика: содержит ли текст признаки mojibake.
     * Ключевые «маркеры»: Р, С, Ў, І, ґ, µ, ‚ — они часто появляются при UTF-8→CP1251.
     */
    private static boolean looksLikeMojibake(String text) {
        int suspicious = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == 'Р' || c == 'С' || c == 'Ў' || c == 'І' || c == 'ґ' || c == 'µ' || c == '‚') {
                suspicious++;
            }
        }
        // Если «маркеров» больше 5% текста — считаем mojibake
        return suspicious > Math.max(1, text.length() / 20);
    }

    /**
     * Оценка «естественности» текста: доля букв/пробелов/знаков препинания.
     * У mojibake много символов из редких Unicode-блоков → ниже оценка.
     */
    private static double score(String text) {
        if (text.isEmpty()) return 0;
        int good = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c) || Character.isWhitespace(c)
                    || ".,:;!?()«»\"'-—".indexOf(c) >= 0) {
                good++;
            }
        }
        return (double) good / text.length();
    }
}