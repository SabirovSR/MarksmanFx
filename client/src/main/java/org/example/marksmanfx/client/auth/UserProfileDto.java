package org.example.marksmanfx.client.auth;

/**
 * DTO ответа сервера на запрос GET /api/auth/me.
 *
 * Мы используем record — он неизменяем по природе, что идеально
 * для объектов данных, которые создаются один раз и только читаются.
 *
 * Пример JSON от сервера:
 * {
 *   "id": 42,
 *   "username": "ArcherKing"
 * }
 */
public record UserProfileDto(Long id, String username) {

    /**
     * Мы проверяем валидность полученного профиля — оба поля обязательны.
     */
    public boolean isValid() {
        return id != null && username != null && !username.isBlank();
    }
}
