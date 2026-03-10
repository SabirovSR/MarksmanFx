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
 * Мы встраиваем его перед UsernamePasswordAuthenticationFilter.
 *
 * Архитектура защиты от исключений:
 *
 *   Запрос без Bearer-токена
 *     → filterChain.doFilter() вызывается ВОВНЕ try-catch
 *     → реальные ошибки downstream (WebSocket upgrade, 404 и т.д.) не маскируются под 401
 *
 *   Запрос С Bearer-токеном
 *     → JWT-валидация внутри try-catch(Exception)
 *     → при ошибке: записываем 401 JSON через вложенный try — он поглощает IOException от getWriter()
 *     → filterChain.doFilter() вызывается ВОВНЕ try-catch (реальные ошибки не маскируются)
 *
 * Почему предыдущая версия давала stack trace в FilterChainProxy?
 *   1. filterChain.doFilter() для запросов без токена был ВНУТРИ try.
 *      Любое исключение из downstream (disconnect, timeout) маскировалось под 401.
 *   2. response.getWriter().write() в блоке catch бросал IOException
 *      (клиент отключился ДО записи ответа). Эта вторичная IOException
 *      не была поймана — она вылетала из catch наружу в FilterChainProxy.
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

        final String authHeader = request.getHeader("Authorization");

        // Мы проверяем наличие Bearer-токена ДО входа в try-catch.
        // Запросы без токена: /api/auth/login, /actuator/health, OPTIONS и т.д.
        // Их ошибки должны распространяться нормально, не маскируясь под 401.
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Мы извлекаем токен (убираем "Bearer ", 7 символов)
        final String token = authHeader.substring(7);

        // Мы оборачиваем в try ТОЛЬКО логику валидации JWT.
        // filterChain.doFilter() остаётся снаружи — реальные ошибки не глотаются.
        try {
            final String username = jwtService.extractUsername(token);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                final UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                if (jwtService.isTokenValid(token, userDetails)) {
                    final UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    log.debug("[JWT] '{}' аутентифицирован → {}", username, request.getRequestURI());
                }
            }

        } catch (Exception e) {
            // Мы поймали ошибку JWT (истёкший, неверная подпись, сломанный формат и т.д.)
            // Логируем ТОЛЬКО сообщение — без передачи e четвёртым аргументом,
            // иначе SLF4J напечатает полный stack trace.
            log.error("[JWT] Ошибка аутентификации [{}]: {}", request.getRequestURI(), e.getMessage());

            SecurityContextHolder.clearContext();

            // Мы пишем ответ во ВЛОЖЕННОМ try-catch.
            // Если клиент уже отключился, getWriter().write() бросит IOException.
            // Без вложенного try эта IOException улетала бы в FilterChainProxy
            // и давала огромный stack trace — именно это и происходило.
            try {
                if (!response.isCommitted()) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(
                            "{\"error\":\"Unauthorized\",\"message\":\""
                            + escapeJsonString(e.getMessage())
                            + "\"}"
                    );
                    response.getWriter().flush();
                }
            } catch (Exception writeEx) {
                // Мы не смогли записать ответ (клиент отключился, соединение сброшено).
                // Логируем кратко на уровне DEBUG — это штатная ситуация, не ошибка.
                log.debug("[JWT] Не удалось записать ответ клиенту (соединение закрыто?): {}",
                        writeEx.getMessage());
            }

            // Мы прерываем цепочку фильтров — ответ уже записан (или попытка была)
            return;
        }

        // Мы вызываем filterChain.doFilter() ВОВНЕ try-catch.
        // Исключения от downstream-фильтров и контроллеров не поглощаются фильтром JWT —
        // они всплывают и обрабатываются штатными механизмами Spring (ExceptionTranslationFilter,
        // GlobalExceptionHandler и т.д.) с правильным HTTP-статусом.
        filterChain.doFilter(request, response);
    }

    /**
     * Мы экранируем специальные символы перед вставкой строки в JSON-литерал.
     * Без экранирования сообщение вида: User "admin" not found
     * сломало бы структуру: {..., "message": "User "admin" not found"}.
     */
    private static String escapeJsonString(String value) {
        if (value == null) return "Unknown error";
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
