package org.example.marksmanfx.server.config;

import lombok.RequiredArgsConstructor;
import org.example.marksmanfx.server.security.JwtAuthFilter;
import org.example.marksmanfx.server.security.JwtChannelInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Конфигурация Spring Security — только цепочка фильтров HTTP.
 *
 * Мы намеренно оставляем здесь ТОЛЬКО SecurityFilterChain и регистрацию
 * WebSocket-интерцептора. Все инфраструктурные бины (UserDetailsService,
 * PasswordEncoder, AuthenticationProvider, AuthenticationManager) живут
 * в {@link ApplicationConfig}.
 *
 * Разделение ответственности (Single Responsibility):
 *   ApplicationConfig  → «что» и «как» аутентифицировать (бины аутентификации)
 *   SecurityConfig     → «где» и «для кого» применять защиту (правила доступа)
 *
 * Граф зависимостей после рефакторинга (цикла нет):
 *
 *   ApplicationConfig
 *       └─ produces: UserDetailsService, PasswordEncoder,
 *                    AuthenticationProvider, AuthenticationManager
 *
 *   JwtAuthFilter         ──→ JwtService
 *                         ──→ UserDetailsService  (из ApplicationConfig)
 *
 *   JwtChannelInterceptor ──→ JwtService
 *                         ──→ UserDetailsService  (из ApplicationConfig)
 *
 *   SecurityConfig        ──→ JwtAuthFilter        (из контейнера, цикла нет)
 *                         ──→ AuthenticationProvider (из ApplicationConfig)
 *                         ──→ JwtChannelInterceptor  (из контейнера)
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /**
     * Мы инжектируем только готовые бины из других конфигов.
     * SecurityConfig не знает ничего о UserRepository или UserDetailsService —
     * эти детали инкапсулированы в ApplicationConfig.
     */
    private final JwtAuthFilter          jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;
    private final JwtChannelInterceptor  jwtChannelInterceptor;

    // ──────────────────────────────────────────────────────────────────────
    //  HTTP Security — цепочка фильтров
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Мы настраиваем цепочку фильтров безопасности для всех HTTP-запросов.
     *
     * Правила доступа:
     *   /api/auth/**  — открыто: регистрация и вход не требуют токена
     *   /ws/**        — открыто на HTTP-уровне: аутентификация идёт в STOMP CONNECT
     *                   через JwtChannelInterceptor, не через HTTP-фильтры
     *   всё остальное — требует валидного JWT в заголовке Authorization
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Мы отключаем CSRF — не нужен для stateless JWT API
            .csrf(AbstractHttpConfigurer::disable)
            // Мы разрешаем CORS — фронтенд находится на другом origin в dev-режиме
            .cors(cors -> {})
            // Мы переводим Spring Security в stateless-режим: никаких HTTP-сессий
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Мы настраиваем правила авторизации запросов
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/auth/**").permitAll()
                    .requestMatchers("/ws/**").permitAll()
                    .anyRequest().authenticated()
            )
            // Мы регистрируем наш AuthenticationProvider из ApplicationConfig
            .authenticationProvider(authenticationProvider)
            // Мы добавляем JWT-фильтр перед стандартным фильтром имя/пароль
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // ──────────────────────────────────────────────────────────────────────
    //  WebSocket Security — регистрация STOMP-интерцептора
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Мы регистрируем JwtChannelInterceptor для аутентификации WebSocket-соединений.
     *
     * Анонимная реализация WebSocketMessageBrokerConfigurer дополняет конфигурацию
     * из WebSocketConfig: Spring композирует все реализации этого интерфейса.
     * JwtChannelInterceptor перехватывает STOMP-фрейм CONNECT и устанавливает
     * Principal в сессию — он доступен в @MessageMapping через параметр Principal.
     */
    @Bean
    public WebSocketMessageBrokerConfigurer webSocketSecurityConfigurer() {
        return new WebSocketMessageBrokerConfigurer() {
            @Override
            public void configureClientInboundChannel(ChannelRegistration registration) {
                // Мы добавляем JWT-интерцептор для проверки токена при WS-подключении
                registration.interceptors(jwtChannelInterceptor);
            }
        };
    }
}
