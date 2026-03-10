package org.example.marksmanfx.server.config;

import lombok.RequiredArgsConstructor;
import org.example.marksmanfx.server.auth.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Конфигурация инфраструктурных бинов аутентификации.
 *
 * Мы выносим сюда четыре бина, которые раньше жили в SecurityConfig и
 * порождали циклическую зависимость «курица и яйцо»:
 *
 *   Старая схема (цикл):
 *     SecurityConfig → JwtAuthFilter → UserDetailsService → SecurityConfig
 *
 *   Новая схема (DAG, цикла нет):
 *     ApplicationConfig → UserDetailsService / PasswordEncoder / AuthenticationProvider / AuthenticationManager
 *     JwtAuthFilter     → JwtService + UserDetailsService (из ApplicationConfig)
 *     SecurityConfig    → JwtAuthFilter + AuthenticationProvider (из ApplicationConfig)
 *
 * ApplicationConfig зависит только от UserRepository и AuthenticationConfiguration
 * — ни один из них не тянет за собой SecurityConfig или фильтры.
 * Это классический паттерн разрыва цикла через вынесение общих зависимостей
 * в отдельный "нейтральный" конфигурационный класс.
 */
@Configuration
@RequiredArgsConstructor
public class ApplicationConfig {

    /**
     * Мы инжектим репозиторий напрямую — единственная внешняя зависимость этого класса.
     * UserRepository не зависит ни от Security, ни от фильтров.
     */
    private final UserRepository userRepository;

    // ──────────────────────────────────────────────────────────────────────
    //  UserDetailsService
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Мы определяем UserDetailsService как самостоятельный бин в этом классе.
     *
     * Именно его инжектируют:
     *   — JwtAuthFilter   (для проверки JWT-токена в HTTP-запросах)
     *   — JwtChannelInterceptor (для проверки JWT в STOMP CONNECT)
     *
     * Spring резолвит его из ApplicationConfig — до инициализации SecurityConfig,
     * поэтому никакой циклической зависимости не возникает.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Пользователь не найден: " + username));
    }

    // ──────────────────────────────────────────────────────────────────────
    //  PasswordEncoder
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Мы выбираем BCryptPasswordEncoder с силой 12 (2^12 = 4096 итераций).
     * Это делает брутфорс атаку практически нецелесообразной даже с GPU.
     *
     * Бин находится здесь, а не в SecurityConfig, потому что его используют:
     *   — AuthenticationProvider (ниже в этом же классе)
     *   — AuthService (при хэшировании пароля при регистрации)
     *
     * Оба они существуют независимо от SecurityConfig.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    // ──────────────────────────────────────────────────────────────────────
    //  AuthenticationProvider
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Мы настраиваем DaoAuthenticationProvider — он объединяет:
     *   — userDetailsService(): откуда загружать пользователя (наш бин выше)
     *   — passwordEncoder():   как сравнивать пароли (наш бин выше)
     *
     * SecurityConfig инжектирует этот бин и регистрирует его в HttpSecurity,
     * не зная ничего о деталях его реализации.
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService());
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    // ──────────────────────────────────────────────────────────────────────
    //  AuthenticationManager
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Мы получаем AuthenticationManager из AuthenticationConfiguration.
     *
     * AuthService.login() инжектирует этот бин для делегирования проверки
     * логина и пароля Spring Security (compare with BCrypt hash, handle exceptions).
     *
     * AuthenticationConfiguration — инфраструктурный бин Spring Security,
     * он не зависит от SecurityConfig, поэтому цикла нет.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
