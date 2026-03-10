package org.example.marksmanfx.server.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Сервис для работы с JWT-токенами.
 *
 * Мы используем библиотеку JJWT (Java JWT) версии 0.12+.
 * Алгоритм подписи: HMAC-SHA256 (HS256) — симметричный, секретный ключ хранится только на сервере.
 *
 * Структура JWT:
 *   HEADER.PAYLOAD.SIGNATURE
 *   — Header: {"alg":"HS256","typ":"JWT"}
 *   — Payload: {"sub":"ArcherKing","iat":1710000000,"exp":1710086400}
 *   — Signature: HMAC-SHA256(base64(header) + "." + base64(payload), secretKey)
 *
 * ВАЖНО: JWT не шифруется — payload читается любым. Не кладём туда пароли.
 */
@Service
public class JwtService {

    /** Секретный ключ из application.yml — минимум 32 байта для HS256 */
    @Value("${app.jwt.secret}")
    private String secretKeyString;

    /** Время жизни токена в секундах (по умолчанию 24 часа) */
    @Value("${app.jwt.expiration-seconds:86400}")
    private long expirationSeconds;

    /**
     * Мы лениво создаём SecretKey из строки конфигурации.
     * Keys.hmacShaKeyFor() проверяет, что ключ достаточной длины для HS256.
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = secretKeyString.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Мы генерируем JWT-токен для аутентифицированного пользователя.
     *
     * @param userDetails Spring Security UserDetails с именем пользователя
     * @return подписанный JWT-токен в компактном формате (три части через точку)
     */
    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    /**
     * Мы генерируем JWT с дополнительными claims (например, role, telegramLinked и т.д.).
     */
    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        long nowMillis = System.currentTimeMillis();
        return Jwts.builder()
                .claims(extraClaims)
                // subject — имя пользователя, основной идентификатор
                .subject(userDetails.getUsername())
                .issuedAt(new Date(nowMillis))
                .expiration(new Date(nowMillis + expirationSeconds * 1000))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Мы извлекаем имя пользователя из токена.
     * Если токен просрочен или подпись неверна — выбрасывается JwtException.
     */
    public String extractUsername(String token) {
        return extractClaims(token).getSubject();
    }

    /**
     * Мы проверяем валидность токена: подпись, срок действия и соответствие пользователю.
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            String username = extractUsername(token);
            return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
        } catch (JwtException e) {
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        return extractClaims(token).getExpiration().before(new Date());
    }

    /**
     * Мы парсим токен и возвращаем все claims.
     * verifyWith() проверяет подпись — если она неверна, бросается SignatureException.
     */
    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }
}
