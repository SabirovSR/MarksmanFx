package org.example.marksmanfx.server.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.marksmanfx.server.auth.dto.AuthResponse;
import org.example.marksmanfx.server.auth.dto.LoginRequest;
import org.example.marksmanfx.server.auth.dto.RegisterRequest;
import org.example.marksmanfx.server.auth.entity.AppUser;
import org.example.marksmanfx.server.auth.repository.UserRepository;
import org.example.marksmanfx.server.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сервис аутентификации и регистрации пользователей.
 *
 * Мы используем паттерн Service Layer: контроллер тонкий (только HTTP-маппинг),
 * вся бизнес-логика сосредоточена здесь.
 *
 * AuthenticationManager — стандартный Spring Security компонент.
 * authenticate() проверяет логин/пароль и бросает исключение при ошибке.
 * Нам не нужно вручную сравнивать хэши — Spring Security делает это сам.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository       userRepository;
    private final PasswordEncoder      passwordEncoder;
    private final JwtService           jwtService;
    private final AuthenticationManager authenticationManager;

    /**
     * Мы регистрируем нового пользователя.
     *
     * Алгоритм:
     *   1. Проверяем уникальность username в БД.
     *   2. Хэшируем пароль через BCrypt (автоматически добавляет соль).
     *   3. Сохраняем пользователя в PostgreSQL.
     *   4. Генерируем JWT и возвращаем клиенту — он сразу залогинен.
     *
     * @throws IllegalArgumentException если username уже занят
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Проверяем уникальность имени пользователя
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException(
                    "Пользователь с именем '" + request.getUsername() + "' уже существует"
            );
        }

        // Создаём пользователя с хэшированным паролем
        AppUser user = AppUser.builder()
                .username(request.getUsername())
                // BCryptPasswordEncoder.encode() — вычисляет bcrypt хэш с рандомной солью
                // Формат: $2a$10$<salt><hash>
                .password(passwordEncoder.encode(request.getPassword()))
                .build();

        AppUser saved = userRepository.save(user);
        log.info("Зарегистрирован новый пользователь: '{}'", saved.getUsername());

        // Генерируем JWT — пользователь сразу аутентифицирован после регистрации
        String token = jwtService.generateToken(saved);
        return new AuthResponse(token, saved.getUsername(), jwtService.getExpirationSeconds());
    }

    /**
     * Мы аутентифицируем существующего пользователя.
     *
     * AuthenticationManager.authenticate() внутри:
     *   1. Загружает UserDetails через UserDetailsService.loadUserByUsername()
     *   2. Сравнивает введённый пароль с хэшем через PasswordEncoder.matches()
     *   3. Бросает BadCredentialsException если пароль неверен
     *
     * @throws org.springframework.security.core.AuthenticationException при неверных данных
     */
    public AuthResponse login(LoginRequest request) {
        // Делегируем проверку пароля Spring Security
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        // Загружаем пользователя для генерации токена
        AppUser user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден после аутентификации"));

        log.info("Успешный вход пользователя: '{}'", user.getUsername());

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, user.getUsername(), jwtService.getExpirationSeconds());
    }
}
