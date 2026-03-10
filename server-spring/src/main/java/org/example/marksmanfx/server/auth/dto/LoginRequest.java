package org.example.marksmanfx.server.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTO для запроса аутентификации.
 * POST /api/auth/login
 *
 * Пример JSON:
 * {
 *   "username": "ArcherKing",
 *   "password": "SecurePass123!"
 * }
 */
@Data
public class LoginRequest {

    @NotBlank(message = "Имя пользователя не может быть пустым")
    private String username;

    @NotBlank(message = "Пароль не может быть пустым")
    private String password;
}
