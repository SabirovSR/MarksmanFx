package org.example.marksmanfx.server.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

/**
 * Интерцептор WebSocket STOMP-канала для JWT-аутентификации.
 *
 * Мы перехватываем STOMP-фрейм CONNECT и читаем JWT из заголовка Authorization.
 * После валидации устанавливаем Principal в STOMP-сессию — он будет доступен
 * во всех @MessageMapping методах как параметр Principal.
 *
 * Почему нельзя использовать JwtAuthFilter для WebSocket?
 *   — WebSocket — это апгрейд HTTP-соединения. После апгрейда фильтры Servlet
 *     уже не работают. STOMP-интерцептор — правильное место для аутентификации WS.
 *
 * Клиент должен передать токен при подключении:
 *   // JavaScript (stomp.js)
 *   client.connect({ Authorization: 'Bearer ' + token }, onConnect);
 *
 *   // Java (JavaFX)
 *   stompSession.connect("/ws", new StompHeaders(){{ set("Authorization", "Bearer " + token) }}, handler);
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtService         jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        // Перехватываем только фрейм CONNECT — аутентификация нужна только при подключении
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                try {
                    String username = jwtService.extractUsername(token);
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                    if (jwtService.isTokenValid(token, userDetails)) {
                        // Устанавливаем Principal в STOMP-сессию
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        userDetails,
                                        null,
                                        userDetails.getAuthorities()
                                );
                        accessor.setUser(authentication);
                        log.info("WebSocket STOMP аутентификация успешна для '{}'", username);
                    } else {
                        log.warn("Невалидный JWT при WebSocket CONNECT");
                    }
                } catch (Exception e) {
                    log.warn("Ошибка JWT в WebSocket CONNECT: {}", e.getMessage());
                }
            }
        }
        return message;
    }
}
