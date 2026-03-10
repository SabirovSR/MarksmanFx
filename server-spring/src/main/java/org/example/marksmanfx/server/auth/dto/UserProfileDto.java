package org.example.marksmanfx.server.auth.dto;

/**
 * DTO профиля аутентифицированного пользователя.
 * Возвращается эндпоинтом GET /api/auth/me.
 *
 * Мы используем record: данные только читаются, изменять их смысла нет.
 *
 * Пример JSON ответа:
 * {
 *   "id": 42,
 *   "username": "ArcherKing"
 * }
 */
public record UserProfileDto(Long id, String username) {}
