package org.example.marksmanfx.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Конфигурация WebSocket с протоколом STOMP.
 *
 * Почему STOMP, а не сырые WebSocket?
 *   — STOMP добавляет маршрутизацию сообщений (topic/queue) поверх бинарного WS.
 *   — Spring автоматически десериализует JSON payload в Java-объекты.
 *   — Все клиенты (JavaFX через Java-WebSocket/STOMP, Vue.js через stomp.js,
 *     Android через okhttp-stomp) подключаются по единому протоколу.
 *   — Поддержка «личных» сообщений (user-specific) через /user/queue/...
 *     без написания ни строки роутинга вручную.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Мы настраиваем встроенный брокер сообщений (Simple Broker).
     *
     * В продакшене /topic и /queue можно заменить на внешний RabbitMQ/ActiveMQ,
     * изменив строку на enableStompBrokerRelay() — переход займёт 5 минут.
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // /topic — широковещательные каналы: все подписчики темы получат сообщение.
        // Используем для рассылки состояния комнаты, координат выстрелов и т.д.
        // /user  — персональные очереди: только конкретный пользователь получит сообщение.
        // Используем для ошибок (например, RoomFullEvent).
        config.enableSimpleBroker("/topic", "/user");

        // Префикс для методов @MessageMapping на сервере.
        // Клиент шлёт сообщение на /app/game/ABC123/shot → попадает в @MessageMapping("/game/{roomId}/shot").
        config.setApplicationDestinationPrefixes("/app");

        // Префикс для персональных назначений (Spring автоматически добавляет sessionId).
        config.setUserDestinationPrefix("/user");
    }

    /**
     * Мы регистрируем STOMP-эндпоинт, к которому подключаются все клиенты.
     *
     * SockJS — это fallback-транспорт для браузеров/окружений без нативного WS.
     * Работает через Long-Polling, EventSource и пр. Для JavaFX нативный WS лучше,
     * но SockJS не мешает его использованию.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry
            .addEndpoint("/ws")
            // Мы разрешаем подключения со всех источников для локальной разработки.
            // В продакшене заменяем на конкретный домен: setAllowedOrigins("https://game.example.com")
            .setAllowedOriginPatterns("*")
            // SockJS для поддержки Vue.js в браузерах без нативных WebSocket
            .withSockJS();
    }
}
