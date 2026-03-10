package org.example.marksmanfx.server.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO для запроса регистрации нового пользователя.
 * POST /api/auth/register
 *
 * Пример JSON:
 * {
 *   "username": "ArcherKing",
 *   "password": "SecurePass123!"
 * }
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "Имя пользователя не может быть пустым")
    @Size(min = 3, max = 50, message = "Имя пользователя должно быть от 3 до 50 символов")
    private String username;

    @NotBlank(message = "Пароль не может быть пустым")
    @Size(min = 6, max = 128, message = "Пароль должен быть от 6 до 128 символов")
    private String password;
}
