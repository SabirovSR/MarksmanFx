package org.example.marksmanfx.server.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
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
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT-фильтр для HTTP REST запросов.
 *
 * Мы встраиваем его в цепочку Spring Security перед UsernamePasswordAuthenticationFilter.
 *
 * Контракт метода (обновлён):
 *
 *   ┌──────────────────────────────────────────────────────────┐
 *   │ Заголовок Authorization отсутствует / не Bearer          │
 *   │   → передаём в chain (Spring Security решает сам)        │
 *   ├──────────────────────────────────────────────────────────┤
 *   │ Токен ЕСТЬ, но невалиден (истёк / подпись / формат)      │
 *   │   → 401 Unauthorized + return (цепочка прерывается)      │
 *   ├──────────────────────────────────────────────────────────┤
 *   │ Токен ЕСТЬ и валиден                                     │
 *   │   → устанавливаем Authentication в SecurityContext       │
 *   │   → передаём в chain                                     │
 *   └──────────────────────────────────────────────────────────┘
 *
 * Почему мы отправляем 401 при невалидном токене, а не молча пропускаем?
 *   — Если токен присутствует — это попытка аутентификации. Провальная попытка
 *     должна давать явный 401, а не тихий 403 от downstream-фильтров.
 *   — Spring Security's ExceptionTranslationFilter тоже возвращает 401, но
 *     только после прохода через всю цепочку. При явном return мы экономим
 *     ресурсы и даём однозначный ответ.
 *   — "Unhandled exception in filter chain" возникал именно потому, что
 *     JwtException из JJWT не был перехвачен и вылетал наружу.
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

        // Мы читаем заголовок Authorization из входящего HTTP-запроса
        final String authHeader = request.getHeader("Authorization");

        // Если токена нет — передаём запрос дальше с пустым SecurityContext.
        // Spring Security сам решит: permitAll() → 200, authenticated() → 401.
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Мы извлекаем значение токена, убирая префикс "Bearer " (7 символов)
        final String token = authHeader.substring(7);

        // ── Блок валидации токена ──────────────────────────────────────────
        // Мы оборачиваем ВСЮ логику парсинга и валидации в try-catch.
        // Каждый тип JWT-исключения перехватывается отдельно для точного лог-сообщения.
        // При любой ошибке — 401 + return, цепочка фильтров прерывается немедленно.

        try {
            final String username = jwtService.extractUsername(token);

            // Мы устанавливаем аутентификацию только если:
            //   а) токен содержит username
            //   б) SecurityContext ещё не содержит аутентификацию (избегаем перезаписи)
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                // Мы загружаем актуальные данные пользователя из БД
                final UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                if (jwtService.isTokenValid(token, userDetails)) {
                    // Мы создаём объект аутентификации и помещаем в SecurityContext
                    final UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    log.debug("[JWT] Аутентификация успешна: '{}' [{}]",
                            username, request.getRequestURI());
                }
            }

        } catch (ExpiredJwtException e) {
            // Мы поймали истёкший токен — клиент должен получить новый через /api/auth/login
            rejectWithUnauthorized(request, response,
                    "Срок действия токена истёк", e);
            return; // Мы прерываем цепочку фильтров — ответ уже отправлен

        } catch (SignatureException e) {
            // Мы поймали неверную подпись — токен мог быть подделан или выпущен другим сервером
            rejectWithUnauthorized(request, response,
                    "Неверная подпись токена", e);
            return;

        } catch (MalformedJwtException e) {
            // Мы поймали токен с нарушенной структурой (header.payload.signature)
            rejectWithUnauthorized(request, response,
                    "Токен имеет некорректный формат", e);
            return;

        } catch (UnsupportedJwtException e) {
            // Мы поймали неподдерживаемый алгоритм или тип токена (например, не HS256)
            rejectWithUnauthorized(request, response,
                    "Неподдерживаемый тип токена", e);
            return;

        } catch (UsernameNotFoundException e) {
            // Мы поймали ситуацию когда токен валиден, но пользователь удалён из БД
            rejectWithUnauthorized(request, response,
                    "Пользователь из токена не найден", e);
            return;

        } catch (JwtException e) {
            // Мы ловим остальные исключения JJWT-библиотеки, которые не попали выше
            rejectWithUnauthorized(request, response,
                    "Ошибка JWT токена", e);
            return;

        } catch (Exception e) {
            // Мы ловим всё непредвиденное — чтобы исключение не вылетело в FilterChainProxy
            // и не появилось как "Unhandled exception in filter chain" в логах Spring
            rejectWithUnauthorized(request, response,
                    "Внутренняя ошибка при обработке токена", e);
            return;
        }

        // Мы передаём запрос дальше только если выше не было return.
        // Это значит: токена нет (ранний return) — ИЛИ — токен валиден.
        filterChain.doFilter(request, response);
    }

    // ──────────────────────────────────────────────────────────────────────

    /**
     * Мы централизованно обрабатываем любую ошибку JWT:
     *   1. Очищаем SecurityContext — предотвращаем "протечку" аутентификации между потоками
     *   2. Логируем предупреждение с URI запроса и причиной
     *   3. Отправляем 401 Unauthorized с читаемым сообщением об ошибке
     *
     * После вызова этого метода вызывающий код ОБЯЗАН сделать return,
     * чтобы не вызывать filterChain.doFilter() с уже записанным ответом.
     */
    private void rejectWithUnauthorized(
            HttpServletRequest  request,
            HttpServletResponse response,
            String              reason,
            Exception           cause) throws IOException {

        // Мы очищаем контекст безопасности — потоки переиспользуются в пуле,
        // без явной очистки контекст предыдущего запроса мог бы "протечь"
        SecurityContextHolder.clearContext();

        log.warn("[JWT] {} [{}]: {}", reason, request.getRequestURI(), cause.getMessage());

        // Мы отправляем 401 с человекочитаемым описанием ошибки.
        // sendError() записывает статус и тело, после чего ответ считается зафиксированным.
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, reason);
    }
}
