package org.example.marksmanfx.server.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.marksmanfx.server.auth.dto.AuthResponse;
import org.example.marksmanfx.server.auth.dto.LoginRequest;
import org.example.marksmanfx.server.auth.dto.RegisterRequest;
import org.example.marksmanfx.server.auth.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST-контроллер для аутентификации и регистрации.
 *
 * Мы выбираем REST (а не WebSocket) для auth-эндпоинтов, потому что:
 *   — Это стандартный HTTP request/response — проще кэшировать, тестировать, документировать.
 *   — JWT-токен нужен до установки WebSocket-соединения.
 *   — Vue.js, Android и JavaFX одинаково умеют делать HTTP POST.
 *
 * Маршруты:
 *   POST /api/auth/register  — регистрация нового пользователя
 *   POST /api/auth/login     — вход и получение JWT
 *
 * Оба эндпоинта открыты в SecurityConfig (permitAll) — JWT не нужен для их вызова.
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Мы регистрируем нового пользователя.
     *
     * @Valid запускает Bean Validation: проверяет @NotBlank, @Size из RegisterRequest.
     * При ошибке валидации Spring возвращает 400 Bad Request автоматически.
     *
     * Пример запроса:
     *   POST /api/auth/register
     *   Content-Type: application/json
     *   {
     *     "username": "ArcherKing",
     *     "password": "SecurePass123!"
     *   }
     *
     * Пример ответа (201 Created):
     *   {
     *     "token": "eyJhbGciOiJIUzI1NiJ9...",
     *     "username": "ArcherKing",
     *     "expiresIn": 86400
     *   }
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            AuthResponse response = authService.register(request);
            // 201 Created — стандартный HTTP-код для успешного создания ресурса
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            // 409 Conflict — имя пользователя уже занято
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Мы аутентифицируем пользователя и возвращаем JWT-токен.
     *
     * Пример запроса:
     *   POST /api/auth/login
     *   Content-Type: application/json
     *   {
     *     "username": "ArcherKing",
     *     "password": "SecurePass123!"
     *   }
     *
     * Пример ответа (200 OK):
     *   {
     *     "token": "eyJhbGciOiJIUzI1NiJ9...",
     *     "username": "ArcherKing",
     *     "expiresIn": 86400
     *   }
     *
     * При неверных данных Spring Security сам выбросит AuthenticationException
     * и мы возвращаем 401 Unauthorized.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            AuthResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (AuthenticationException e) {
            // 401 Unauthorized — неверный логин или пароль
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Неверное имя пользователя или пароль"));
        }
    }
}
