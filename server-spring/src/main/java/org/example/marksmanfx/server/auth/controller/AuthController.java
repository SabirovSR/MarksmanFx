package org.example.marksmanfx.server.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.marksmanfx.server.auth.dto.AuthResponse;
import org.example.marksmanfx.server.auth.dto.LoginRequest;
import org.example.marksmanfx.server.auth.dto.RegisterRequest;
import org.example.marksmanfx.server.auth.dto.UserProfileDto;
import org.example.marksmanfx.server.auth.entity.AppUser;
import org.example.marksmanfx.server.auth.repository.UserRepository;
import org.example.marksmanfx.server.auth.service.AuthService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

/**
 * REST-контроллер для аутентификации и регистрации.
 *
 * Ожидаемый формат JSON для каждого эндпоинта:
 *
 *   POST /api/auth/register
 *     { "username": "ArcherKing", "password": "SecurePass123!" }
 *     → 201 Created:  { "token": "eyJ...", "username": "ArcherKing", "expiresIn": 86400 }
 *     → 409 Conflict: { "error": "Пользователь с именем '...' уже существует" }
 *     → 400 Bad Request: если username < 3 симв. или password < 6 симв.
 *
 *   POST /api/auth/login
 *     { "username": "ArcherKing", "password": "SecurePass123!" }
 *     → 200 OK:           { "token": "eyJ...", "username": "ArcherKing", "expiresIn": 86400 }
 *     → 401 Unauthorized: { "error": "Неверное имя пользователя или пароль" }
 *
 *   GET /api/auth/me
 *     Authorization: Bearer eyJ...
 *     → 200 OK: { "id": 42, "username": "ArcherKing" }
 *     → 401:    если токен отсутствует, истёк или неверен
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService    authService;
    private final UserRepository userRepository;

    // ──────────────────────────────────────────────────────────────────────
    //  POST /api/auth/register
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Мы регистрируем нового пользователя и сразу возвращаем JWT-токен.
     *
     * @Valid запускает Bean Validation до входа в метод:
     *   — username: @NotBlank + @Size(min=3, max=50)
     *   — password: @NotBlank + @Size(min=6, max=128)
     * При нарушении Spring автоматически вернёт 400 Bad Request.
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        // Мы логируем попытку регистрации — имя пользователя безопасно для логов,
        // пароль никогда не попадает в лог даже в dev-режиме
        log.info("[AUTH] Попытка регистрации: username='{}'", request.getUsername());

        try {
            AuthResponse response = authService.register(request);
            log.info("[AUTH] Регистрация успешна: username='{}', userId будет присвоен БД",
                    response.getUsername());
            // Мы возвращаем 201 Created — стандартный код для создания нового ресурса
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            // Мы поймали проверку уникальности на уровне сервиса (existsByUsername)
            log.warn("[AUTH] Регистрация отклонена: username='{}' уже занят. Причина: {}",
                    request.getUsername(), e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));

        } catch (DataIntegrityViolationException e) {
            // Мы поймали нарушение UNIQUE-constraint на уровне PostgreSQL —
            // race condition: два запроса прошли existsByUsername одновременно
            log.warn("[AUTH] DataIntegrityViolation при регистрации '{}': {}",
                    request.getUsername(), e.getMostSpecificCause().getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Пользователь с таким именем уже существует"));

        } catch (Exception e) {
            // Мы поймали неожиданную ошибку — логируем полный stacktrace для диагностики
            log.error("[AUTH] Неожиданная ошибка при регистрации '{}': {}",
                    request.getUsername(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Внутренняя ошибка сервера при регистрации"));
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  POST /api/auth/login
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Мы аутентифицируем пользователя через Spring Security и возвращаем JWT.
     *
     * Внутри authService.login() вызывается:
     *   authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(...))
     * Это запускает DaoAuthenticationProvider, который:
     *   1. Вызывает userDetailsService.loadUserByUsername() → ищет в БД
     *   2. Вызывает passwordEncoder.matches(rawPassword, storedHash)
     * При ошибке бросается BadCredentialsException или UsernameNotFoundException.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        // Мы логируем попытку входа для мониторинга и отладки
        log.info("[AUTH] Попытка входа: username='{}'", request.getUsername());

        try {
            AuthResponse response = authService.login(request);
            log.info("[AUTH] Вход успешен: username='{}'", response.getUsername());
            return ResponseEntity.ok(response);

        } catch (UsernameNotFoundException e) {
            // Мы разделяем "пользователь не найден" от "неверный пароль" в логах —
            // это ключевое различие при отладке: надо ли регистрироваться или верен ли пароль?
            log.warn("[AUTH] Вход отклонён: пользователь '{}' не найден в БД",
                    request.getUsername());
            // Мы не сообщаем клиенту точную причину (информационная безопасность):
            // знать, существует ли аккаунт — лишняя информация для злоумышленника
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Неверное имя пользователя или пароль"));

        } catch (BadCredentialsException e) {
            // Мы поймали неверный пароль — пользователь существует, но хэши не совпали
            log.warn("[AUTH] Вход отклонён: неверный пароль для пользователя '{}'",
                    request.getUsername());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Неверное имя пользователя или пароль"));

        } catch (AuthenticationException e) {
            // Мы поймали другие ошибки аутентификации (заблокированный аккаунт и т.д.)
            log.warn("[AUTH] Ошибка аутентификации для '{}': {} — {}",
                    request.getUsername(), e.getClass().getSimpleName(), e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Аутентификация не выполнена: " + e.getMessage()));

        } catch (Exception e) {
            log.error("[AUTH] Неожиданная ошибка при входе '{}': {}",
                    request.getUsername(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Внутренняя ошибка сервера при входе"));
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  GET /api/auth/me
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Мы возвращаем профиль текущего аутентифицированного пользователя.
     *
     * Этот эндпоинт используется клиентом при автологине:
     *   1. Клиент читает сохранённый JWT из localStorage/Preferences
     *   2. Отправляет GET /api/auth/me с заголовком Authorization: Bearer <token>
     *   3. 200 → токен валиден, сессия восстановлена
     *   4. 401 → JwtAuthFilter отклонил токен → клиент показывает экран входа
     *
     * Сюда запрос доходит только если JwtAuthFilter успешно валидировал токен.
     * Principal.getName() гарантированно содержит username из JWT-claim "sub".
     */
    @GetMapping("/me")
    public ResponseEntity<UserProfileDto> getCurrentUser(Principal principal) {
        // Мы логируем только на уровне DEBUG — этот эндпоинт вызывается при каждом старте приложения
        log.debug("[AUTH] GET /me: запрос профиля для '{}'", principal.getName());

        AppUser user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> {
                    log.error("[AUTH] Критично: пользователь '{}' прошёл JWT-валидацию, но не найден в БД",
                            principal.getName());
                    return new UsernameNotFoundException(
                            "Пользователь не найден после валидации токена: " + principal.getName());
                });

        return ResponseEntity.ok(new UserProfileDto(user.getId(), user.getUsername()));
    }
}
