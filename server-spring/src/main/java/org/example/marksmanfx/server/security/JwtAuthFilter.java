package org.example.marksmanfx.server.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT-фильтр для HTTP REST запросов.
 *
 * Мы встраиваем его в цепочку Spring Security перед UsernamePasswordAuthenticationFilter.
 * Для каждого HTTP-запроса:
 *   1. Читаем заголовок Authorization: Bearer <token>
 *   2. Если токен есть — извлекаем username, загружаем UserDetails из БД
 *   3. Валидируем токен (подпись + срок действия)
 *   4. Устанавливаем Authentication в SecurityContext
 *
 * Зависимости этого класса:
 *   — JwtService:         статический сервис без зависимостей на Security-бины
 *   — UserDetailsService: бин из ApplicationConfig (НЕ из SecurityConfig)
 *
 * Именно поэтому циклической зависимости нет:
 *   JwtAuthFilter → UserDetailsService ← ApplicationConfig
 *   SecurityConfig → JwtAuthFilter  (однонаправленно, цикла нет)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    /** Мы используем JwtService только для парсинга и валидации токена */
    private final JwtService         jwtService;

    /**
     * Мы инжектируем UserDetailsService из ApplicationConfig.
     * Spring выбирает бин по типу — единственная реализация зарегистрирована
     * в ApplicationConfig.userDetailsService(), которая не зависит от SecurityConfig.
     */
    private final UserDetailsService userDetailsService;

    /**
     * Контракт метода: мы НИКОГДА не вызываем response.sendError() и не бросаем исключения
     * наружу. Единственная обязанность фильтра — заполнить SecurityContext, если токен валиден.
     * Решение о блокировке (403/401) принимает Spring Security ПОСЛЕ всей цепочки фильтров,
     * опираясь на правила из SecurityFilterChain (permitAll / authenticated и т.д.).
     *
     * Это ключевой принцип: фильтр аутентифицирует, а не авторизует.
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest  request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain         filterChain
    ) throws ServletException, IOException {

        // Мы читаем заголовок Authorization из входящего HTTP-запроса
        final String authHeader = request.getHeader("Authorization");

        // Если токена нет — передаём запрос дальше по цепочке с пустым SecurityContext.
        // Spring Security сам решит: permitAll() → 200, authenticated() → 401.
        // Именно так работает /actuator/health и /api/auth/login без токена.
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Мы извлекаем значение токена, убирая префикс "Bearer " (7 символов)
        final String token = authHeader.substring(7);

        try {
            final String username = jwtService.extractUsername(token);

            // Мы устанавливаем аутентификацию только если:
            //   а) токен содержит username
            //   б) в текущем SecurityContext ещё нет аутентификации (избегаем перезаписи)
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                // Мы загружаем актуальные данные пользователя из БД для проверки токена
                final UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                if (jwtService.isTokenValid(token, userDetails)) {
                    // Мы создаём объект аутентификации и помещаем его в SecurityContext —
                    // после этого Spring Security будет считать запрос аутентифицированным
                    final UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,                          // credentials не нужны после проверки токена
                                    userDetails.getAuthorities()
                            );
                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.debug("JWT аутентификация успешна для пользователя '{}'", username);
                }
                // Если токен невалиден — мы ничего не устанавливаем в SecurityContext.
                // Запрос дойдёт до Spring Security с пустым контекстом и получит 401.
            }

        } catch (Exception e) {
            // Мы поймали исключение при обработке токена (истёк, неверная подпись и т.д.).
            // Мы очищаем SecurityContext, чтобы гарантировать отсутствие частично
            // заполненного состояния — потоки в пуле переиспользуются, и без явной
            // очистки контекст предыдущего запроса мог бы "протечь" в текущий.
            SecurityContextHolder.clearContext();
            log.warn("Ошибка обработки JWT токена [{}]: {}", request.getRequestURI(), e.getMessage());
        }

        // Мы всегда передаём запрос дальше — фильтр никогда не прерывает цепочку сам.
        // Правила permitAll() / authenticated() в SecurityFilterChain выносят финальный вердикт.
        filterChain.doFilter(request, response);
    }
}
