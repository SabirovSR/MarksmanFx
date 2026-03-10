package org.example.marksmanfx.server.config;

import lombok.RequiredArgsConstructor;
import org.example.marksmanfx.server.auth.repository.UserRepository;
import org.example.marksmanfx.server.security.JwtAuthFilter;
import org.example.marksmanfx.server.security.JwtChannelInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Конфигурация Spring Security.
 *
 * Мы используем компонентный подход (Security Lambda DSL) вместо устаревшего
 * WebSecurityConfigurerAdapter — это рекомендованный способ с Spring Security 6.
 *
 * Стратегия аутентификации: STATELESS (без HTTP-сессий).
 *   — Сервер не хранит сессии в памяти — это горизонтально масштабируемо.
 *   — Каждый запрос несёт JWT в заголовке — сервер проверяет подпись токена.
 *   — Идеально для REST API и WebSocket.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter    jwtAuthFilter;
    private final UserRepository   userRepository;
    private final JwtChannelInterceptor jwtChannelInterceptor;

    /**
     * Мы определяем UserDetailsService — Spring Security использует его
     * для загрузки UserDetails по username при аутентификации.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Пользователь не найден: " + username));
    }

    /**
     * Мы выбираем BCryptPasswordEncoder с силой 12 (по умолчанию 10).
     * Сила 12 означает 2^12 = 4096 итераций хэширования.
     * Это делает брутфорс практически невозможным даже с GPU.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Мы настраиваем DaoAuthenticationProvider — он объединяет
     * UserDetailsService (откуда брать пользователя) и
     * PasswordEncoder (как сравнивать пароли).
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService());
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * Мы получаем AuthenticationManager из конфигурации.
     * Он используется в AuthService.login() для проверки пароля.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Мы настраиваем цепочку фильтров безопасности для HTTP-запросов.
     *
     * Правила доступа:
     *   /api/auth/**  — публичные (регистрация, логин)
     *   /ws/**        — публичные на уровне HTTP (аутентификация идёт в STOMP CONNECT)
     *   остальное     — только с валидным JWT
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Отключаем CSRF — не нужен для stateless REST+JWT API
            .csrf(AbstractHttpConfigurer::disable)
            // Настраиваем CORS — разрешаем запросы от фронтенда
            .cors(cors -> {})
            // Stateless — не создаём и не используем HTTP-сессии
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Правила авторизации запросов
            .authorizeHttpRequests(auth -> auth
                    // Открытые эндпоинты: регистрация и логин
                    .requestMatchers("/api/auth/**").permitAll()
                    // WebSocket эндпоинт — HTTP upgrade должен пройти без токена
                    .requestMatchers("/ws/**").permitAll()
                    // Все остальные запросы требуют аутентификации
                    .anyRequest().authenticated()
            )
            // Регистрируем наш JWT-фильтр перед стандартным фильтром паролей
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Мы регистрируем JwtChannelInterceptor для аутентификации WebSocket STOMP CONNECT.
     *
     * Это отдельный компонент WebSocketMessageBrokerConfigurer — он регистрирует
     * интерцептор на inbound канал (входящие STOMP-фреймы от клиентов).
     */
    @Bean
    public WebSocketMessageBrokerConfigurer webSocketSecurityConfigurer() {
        return new WebSocketMessageBrokerConfigurer() {
            @Override
            public void configureClientInboundChannel(ChannelRegistration registration) {
                // Добавляем JWT-интерцептор — он читает токен из заголовка STOMP CONNECT
                registration.interceptors(jwtChannelInterceptor);
            }
        };
    }
}
