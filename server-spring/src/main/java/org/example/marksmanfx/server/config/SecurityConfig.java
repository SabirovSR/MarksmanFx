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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

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
            // Мы явно передаём наш CorsConfigurationSource, чтобы Spring Security
            // знал, какие origin разрешены. Пустой customizer .cors(cors -> {})
            // заставляет Spring искать бин CorsConfigurationSource в контексте —
            // если его нет, OPTIONS-запросы блокируются с 403.
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // Мы переводим Spring Security в stateless-режим: никаких HTTP-сессий
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Мы настраиваем правила авторизации запросов
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/auth/**").permitAll()
                    .requestMatchers("/ws/**").permitAll()
                    // Мы открываем Actuator для Docker healthcheck без JWT-токена.
                    // /actuator/health используется в docker-compose depends_on.
                    // Остальные эндпоинты Actuator (/info, /metrics и т.д.) тоже
                    // открываем — в продакшене их стоит закрыть отдельным firewall-правилом.
                    .requestMatchers("/actuator/**").permitAll()
                    .anyRequest().authenticated()
            )
            // Мы регистрируем наш AuthenticationProvider из ApplicationConfig
            .authenticationProvider(authenticationProvider)
            // Мы добавляем JWT-фильтр перед стандартным фильтром имя/пароль
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // ──────────────────────────────────────────────────────────────────────
    //  CORS — политика кросс-доменных запросов
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Мы описываем политику CORS для всего API.
     *
     * Почему preflight OPTIONS получал 403 без этого бина?
     *   Браузер перед любым «небезопасным» запросом (POST, PUT, DELETE,
     *   запрос с заголовком Authorization) сначала отправляет OPTIONS-запрос
     *   (preflight) на тот же URL. Spring Security перехватывает его первым.
     *   Без CorsConfigurationSource Spring не знает, что этот origin разрешён,
     *   и блокирует запрос с 403 — до того как сработает permitAll().
     *
     * Почему setAllowedOriginPatterns, а не setAllowedOrigins?
     *   При setAllowCredentials(true) нельзя использовать setAllowedOrigins("*"):
     *   RFC запрещает браузеру отправлять credentials на wildcard origin.
     *   setAllowedOriginPatterns("*") — это Spring-паттерн, а не HTTP-wildcard,
     *   поэтому он совместим с allowCredentials и не нарушает спецификацию.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Мы разрешаем запросы с конкретных origin нашего фронтенда.
        // В dev-среде используем паттерн "*", чтобы не перечислять все порты вручную.
        // В продакшене замените на явный список: List.of("https://game.example.com")
        config.setAllowedOriginPatterns(List.of(
                "http://localhost",      // фронтенд за Nginx (порт 80)
                "http://localhost:82",   // фронтенд на нестандартном порту
                "http://localhost:5173", // Vite dev server
                "http://localhost:8080"  // прямой доступ к бэкенду в dev
        ));

        // Мы явно перечисляем методы — OPTIONS здесь критически важен:
        // он используется браузером для preflight-проверки перед POST/PUT/DELETE
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

        // Мы разрешаем все заголовки, включая Authorization (Bearer JWT),
        // Content-Type и любые кастомные заголовки, которые может добавить фронтенд
        config.setAllowedHeaders(List.of("*"));

        // Мы разрешаем браузеру читать заголовок Authorization из ответа сервера
        // (нужно, если сервер будет возвращать обновлённый токен в заголовке)
        config.setExposedHeaders(List.of("Authorization"));

        // Мы разрешаем передачу credentials (куки, заголовки авторизации)
        // в кросс-доменных запросах — нужно для JWT в заголовке Authorization
        config.setAllowCredentials(true);

        // Мы кэшируем результат preflight-запроса в браузере на 30 минут.
        // Это избавляет от лишних OPTIONS-запросов при каждом API-вызове.
        config.setMaxAge(1800L);

        // Мы регистрируем эту конфигурацию для всех эндпоинтов сервера
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
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
