package org.example.marksmanfx.server.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO ответа при успешной аутентификации или регистрации.
 *
 * Мы возвращаем JWT-токен, который клиент должен сохранить
 * (localStorage в браузере, SharedPreferences в Android, файл в JavaFX)
 * и прикладывать к каждому запросу в заголовке:
 *   Authorization: Bearer <token>
 *
 * Для WebSocket: токен передаётся при STOMP CONNECT в заголовке Authorization.
 *
 * Пример JSON:
 * {
 *   "token": "eyJhbGciOiJIUzI1NiJ9...",
 *   "username": "ArcherKing",
 *   "expiresIn": 86400
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    /** JWT Bearer токен */
    private String token;

    /** Имя пользователя для отображения в UI */
    private String username;

    /** Время жизни токена в секундах (для клиентского таймера обновления) */
    private long expiresIn;
}
