package ru.netology.enumConst;

/**
 * Типы сетевых и инфраструктурных ошибок загрузки.
 */
public enum DeclaredLoadErrorType {
    TIMEOUT,            // Сервер завис или сеть слишком медленная
    SERVER_ERROR,       // Ошибки сервера (5xx)
    CLIENT_ERROR,       // Ошибки неверных запросов / ссылок (4xx, например 404)
    NETWORK_FAILURE,    // Физический сбой сети (нет интернета, упал DNS)
    INVALID_SOURCE,     // Ошибка формата переданных данных (например, битая ссылка)
    INTERRUPTED         // Поток принудительно остановлен (например, Docker завершает работу)
}
