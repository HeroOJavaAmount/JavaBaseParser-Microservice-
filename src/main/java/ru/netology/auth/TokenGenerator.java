package ru.netology.auth;

/**
 * Консольная утилита (CLI) для ручной генерации JWT-токенов администратором.
 * <p>
 * Читает секретный ключ и время жизни из переменных окружения через {@link TokenConfig#fromEnv()}.
 * Пример запуска из командной строки:
 * <pre>{@code
 * PARSER_AUTH_SECRET="your-32-byte-or-longer-secret-key" \
 * java -cp app.jar ru.netology.auth.TokenGenerator "userId"
 * }</pre>
 * Самодостаточность токена.  Внутри строки уже записано имя пользователя (sub) и время истечения срока годности (exp), а сверху всё это «опечатано» криптографической подписью на основе секретного ключа PARSER_AUTH_SECRET.
 */
public class TokenGenerator {

    /**
     * Точка входа CLI-утилиты.
     * <p>
     * Если переменная окружения {@code PARSER_AUTH_SECRET} не задана, выводит сообщение об ошибке
     * в поток {@code System.err} и завершает процесс с кодом возврата {@code 1}.
     *
     * @param args аргументы командной строки; первый элемент ({@code args[0]}) используется как {@code subject}.
     *             Если аргументы не переданы, по умолчанию используется {@code "admin"}
     */
    public static void main(String[] args) {
        TokenConfig cfg = TokenConfig.fromEnv();
        if (!cfg.enabled()) {
            System.err.println("Set PARSER_AUTH_SECRET first (≥32 bytes).");
            System.exit(1);
        }
        String subject = args.length > 0 ? args[0] : "admin";
        String token = new JwtTokenService(cfg).generate(subject);

        System.out.println("Subject:   " + subject);
        System.out.println("Expires in:" + cfg.ttl());
        System.out.println("Token:");
        System.out.println(token);
    }
}