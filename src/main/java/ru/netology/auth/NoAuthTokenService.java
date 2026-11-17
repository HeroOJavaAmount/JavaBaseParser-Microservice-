package ru.netology.auth;

/**
 * Заглушка {@link TokenService} для режима разработки и локального тестирования (dev-mode).
 * <p>
 * Отключает реальную проверку подлинности: любой входящий запрос считается валидным
 * и выполняется от имени условного пользователя {@code "anonymous"}.
 * Генерация новых токенов в этом режиме заблокирована.
 */
public class NoAuthTokenService implements TokenService {

    /**
     * Всегда выбрасывает исключение, так как выпуск токенов при отключенной аутентификации не имеет смысла.
     *
     * @param subject идентификатор субъекта (игнорируется)
     * @return не возвращает значения
     * @throws UnsupportedOperationException при любом вызове метода
     */
    @Override
    public String generate(String subject) {
        throw new UnsupportedOperationException("Auth is disabled (dev mode)");
    }

    /**
     * Пропускает любой запрос без проверки содержимого токена.
     *
     * @param token произвольная строка или {@code null} (игнорируется)
     * @return всегда успешный результат {@link AuthResult#ok(String, java.time.Instant)}
     *         для субъекта {@code "anonymous"} без ограничения срока действия
     */
    @Override
    public AuthResult validate(String token) {
        return AuthResult.ok("anonymous", null);
    }
}