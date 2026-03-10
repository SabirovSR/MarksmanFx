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

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest  request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain         filterChain
    ) throws ServletException, IOException {

        // Мы читаем заголовок авторизации из входящего HTTP-запроса
        String authHeader = request.getHeader("Authorization");

        // Мы пропускаем запросы без Bearer-токена (публичные эндпоинты)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Мы извлекаем токен, убирая префикс "Bearer "
        String token = authHeader.substring(7);

        try {
            String username = jwtService.extractUsername(token);

            // Мы устанавливаем контекст только если пользователь ещё не аутентифицирован
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                // Мы загружаем актуальные данные пользователя из БД
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // Мы проверяем валидность токена относительно данного пользователя
                if (jwtService.isTokenValid(token, userDetails)) {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );
                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );
                    // Мы помещаем объект аутентификации в контекст безопасности текущего запроса
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.debug("JWT аутентификация успешна для пользователя '{}'", username);
                }
            }
        } catch (Exception e) {
            // Мы логируем ошибку и продолжаем цепочку — Spring Security сам отклонит запрос
            log.warn("Ошибка обработки JWT токена: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
