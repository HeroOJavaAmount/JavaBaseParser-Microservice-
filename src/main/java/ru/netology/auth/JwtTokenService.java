package ru.netology.auth;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/** JWT (HS256): подпись HMAC, TTL, subject.
 * @constructor {@link public JwtTokenService(TokenConfig cfg)}  превращает текстовый пароль из TokenConfig в криптографический объект SecretKey через Keys.hmacShaKeyFor(...).
 * @methods {@link String generate(String subject) } - программа фиксирует текущую секунду (Instant.now()), прибавляет к ней время жизни (now.plus(ttl)) и передает данные в построитель Jwts.builder().
 * <br>{@link AuthResult validate(String token)} - проверяет токен настоящий, не просроченный, и кто его владелец? (библиотека для JWT)
 */
public class JwtTokenService implements TokenService {

    private final SecretKey key;
    private final Duration ttl;

    /**
     * Создает сервис работы с JWT на основе переданной конфигурации.
     *
     * @param cfg конфигурация аутентификации с активным секретным ключом
     * @throws IllegalStateException если конфигурация не передана или секретный ключ не задан ({@code !cfg.enabled()})
     */
    public JwtTokenService(TokenConfig cfg) {
        if (cfg == null || !cfg.enabled()) {
            throw new IllegalStateException("Auth secret is not set");
        }
        this.key = Keys.hmacShaKeyFor(cfg.secret().getBytes(StandardCharsets.UTF_8));
        this.ttl = cfg.ttl();
    }

    /**
     * Важно payload НЕ секретный(sub/iat/exp секретов не содержат).
     * Выпускает подписанный JWT-токен со стандартными полями {@code sub}, {@code iat} и {@code exp}.
     * @param subject идентификатор пользователя или сервиса, для которого выпускается токен
     * @return компактная строка JWT в формате {@code Header.Payload.Signature}
     * @throws IllegalArgumentException если {@code subject} равен {@code null} или состоит только из пробелов
     */
    @Override
    public String generate(String subject) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Subject cannot be null or blank");
        }
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /**
     * @commands Jwts.parser().verifyWith(key).build() настраивает парсер секретным ключом.<br><br>
     * .verifyWith(key) — говорим, каким ключом проверять подпись. Это тот самый секрет из переменной окружения (PARSER_AUTH_SECRET), который используется и при выпуске токенов;<br><br>
     * .parseSignedClaims(token) делает сразу две вещи: заново вычисляет подпись (проверяя, не подделали ли токен) и сверяет время exp с текущими часами сервера.<br><br>
     * .getPayload() — достаём содержимое токена (claims): кто владелец, когда истекает и т. д.
     * @param token
     * @return возвращаем  AuthResult  двумя фактами: sub (имя пользователя, на которого выпущен токен) и момент истечения (exp), переведённый в удобный Instant.<br><br>
     * Если время вышло или подпись сломана, библиотека jjwt выбрасывает исключение (ExpiredJwtException или JwtException).
     */
    @Override
    public AuthResult validate(String token) {
        if (token == null || token.isBlank()) {
            return AuthResult.fail("Empty token");
        }
        try {
            var claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return AuthResult.ok(claims.getSubject(), claims.getExpiration().toInstant());
        } catch (ExpiredJwtException ex) {
            return AuthResult.fail("Token expired at " + ex.getClaims().getExpiration());
        } catch (JwtException | IllegalArgumentException ex) {
            return AuthResult.fail("Invalid token: " + ex.getMessage());
        }
    }
}
