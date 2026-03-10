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
 *
 * Архитектура обработки ошибок:
 *   Весь метод doFilterInternal обёрнут в ОДИН внешний try-catch(Exception).
 *   Это гарантирует: ни одно исключение — ни JWT, ни базы данных, ни сети —
 *   никогда не вылетит за пределы фильтра в FilterChainProxy.
 *
 *   Без этого:
 *     JwtException → не перехвачено → FilterChainProxy → огромный stack trace
 *     500 Internal Server Error вместо 401 Unauthorized
 *
 *   С этим:
 *     Любое исключение → catch → JSON 401 + return → trace только в лог.error
 *
 * Контракт:
 *   Нет токена  → filterChain.doFilter() (Spring Security решает)
 *   Есть токен  → внутри защищённого try → успех: chain / ошибка: 401 JSON
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

        // Мы оборачиваем АБСОЛЮТНО ВСЁ в один try-catch.
        // Это единственный надёжный способ гарантировать, что фильтр
        // никогда не выбросит необработанное исключение в FilterChainProxy.
        try {

            // Мы читаем заголовок Authorization
            final String authHeader = request.getHeader("Authorization");

            // Мы пропускаем запросы без Bearer-токена — они идут на открытые эндпоинты
            // (/api/auth/login, /api/auth/register, /actuator/health и т.д.).
            // Spring Security сам вынесет вердикт на основе правил permitAll/authenticated.
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                filterChain.doFilter(request, response);
                return;
            }

            // Мы извлекаем токен, убирая префикс "Bearer " (ровно 7 символов)
            final String token = authHeader.substring(7);

            // Мы парсим username из JWT payload (claim "sub")
            final String username = jwtService.extractUsername(token);

            // Мы устанавливаем аутентификацию только если:
            //   а) username успешно извлечён
            //   б) SecurityContext ещё не содержит аутентификацию этого запроса
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                // Мы загружаем актуальные данные пользователя из PostgreSQL
                final UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // Мы проверяем подпись, срок действия и соответствие username
                if (jwtService.isTokenValid(token, userDetails)) {
                    final UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,                         // credentials не нужны — токен уже проверен
                                    userDetails.getAuthorities()
                            );
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // Мы помещаем аутентификацию в SecurityContext — запрос считается авторизованным
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    log.debug("[JWT] '{}' аутентифицирован → {}", username, request.getRequestURI());
                }
            }

            // Мы передаём запрос дальше по цепочке фильтров.
            // Этот вызов тоже внутри try — любое исключение из downstream поймается ниже.
            filterChain.doFilter(request, response);

        } catch (Exception e) {
            // Мы перехватываем ЛЮБОЕ исключение, которое могло возникнуть:
            //   — ExpiredJwtException, SignatureException, MalformedJwtException (JJWT)
            //   — UsernameNotFoundException (Spring Security)
            //   — любое другое RuntimeException из jwtService или userDetailsService

            // Мы логируем только сообщение без stack trace — это принципиально важно.
            // Передача e четвёртым аргументом (log.error(..., e)) вывела бы полный trace.
            // Нам нужен краткий лог, а не «стена текста» в консоли.
            log.error("[JWT] Ошибка аутентификации [{}]: {}", request.getRequestURI(), e.getMessage());

            // Мы очищаем SecurityContext — потоки из пула переиспользуются,
            // и без явной очистки контекст предыдущего запроса мог бы «протечь»
            SecurityContextHolder.clearContext();

            // Мы отвечаем клиенту только если ответ ещё не зафиксирован.
            // Если filterChain.doFilter() уже частично записал ответ — пропускаем,
            // иначе получим IllegalStateException: "response already committed".
            if (!response.isCommitted()) {
                // Мы используем setStatus() + getWriter() вместо sendError().
                // sendError() может перенаправить на стандартную Spring Error-страницу,
                // которая вернёт HTML вместо JSON — фронтенд не сможет его распарсить.
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(
                        "{\"error\":\"Unauthorized\",\"message\":\""
                        + escapeJsonString(e.getMessage())
                        + "\"}"
                );
            }

            // Мы явно возвращаемся — цепочка фильтров прервана, ответ уже отправлен
            return;
        }
    }

    /**
     * Мы экранируем специальные символы в строке перед вставкой в JSON-литерал.
     *
     * Без этого сообщение вида: Пользователь "admin" не найден
     * сломало бы структуру JSON: {..., "message": "Пользователь "admin" не найден"}
     *
     * Мы обрабатываем только символы, опасные внутри JSON-строки.
     * Для полноценной сериализации используйте ObjectMapper в продакшене.
     */
    private static String escapeJsonString(String value) {
        if (value == null) return "Unknown error";
        return value
                .replace("\\", "\\\\")   // обратный слэш первым
                .replace("\"", "\\\"")   // двойная кавычка
                .replace("\n", "\\n")    // перевод строки
                .replace("\r", "\\r")    // возврат каретки
                .replace("\t", "\\t");   // табуляция
    }
}
