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
 * Мы встраиваем его в цепочку Spring Security фильтров ПЕРЕД стандартным
 * UsernamePasswordAuthenticationFilter. Для каждого HTTP-запроса:
 *   1. Читаем заголовок Authorization: Bearer <token>
 *   2. Если токен есть — извлекаем username, загружаем UserDetails из БД
 *   3. Валидируем токен (подпись + срок действия)
 *   4. Устанавливаем Authentication в SecurityContext
 *
 * OncePerRequestFilter гарантирует, что фильтр выполняется ровно один раз
 * на запрос, даже при forward/redirect внутри приложения.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService         jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest  request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain         filterChain
    ) throws ServletException, IOException {

        // Читаем заголовок авторизации
        String authHeader = request.getHeader("Authorization");

        // Если заголовка нет или он не Bearer — пропускаем фильтр (публичный эндпоинт)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Извлекаем токен (убираем префикс "Bearer ")
        String token = authHeader.substring(7);

        try {
            String username = jwtService.extractUsername(token);

            // Устанавливаем контекст только если пользователь ещё не аутентифицирован
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // Загружаем актуальные данные пользователя из БД
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // Проверяем валидность токена относительно этого пользователя
                if (jwtService.isTokenValid(token, userDetails)) {
                    // Создаём объект аутентификации и помещаем его в контекст безопасности
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );
                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.debug("JWT аутентификация успешна для пользователя '{}'", username);
                }
            }
        } catch (Exception e) {
            // Логируем и продолжаем цепочку — Spring Security отклонит запрос сам
            log.warn("Ошибка обработки JWT токена: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
