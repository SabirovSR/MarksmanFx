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
    /**
     * Мы настраиваем маршрутизацию STOMP-сообщений.
     *
     * Три пространства имён:
     *   /topic  — широковещательные топики (все подписчики темы получат сообщение)
     *   /queue  — персональные очереди (ОБЯЗАТЕЛЬНО для работы @SendToUser и /user/queue/*)
     *   /app    — обработчики @MessageMapping на сервере (не идёт в брокер напрямую)
     *   /user   — ТОЛЬКО как userDestinationPrefix: клиент пишет /user/queue/X,
     *             Spring переписывает в /queue/X-user{sessionId} для доставки
     *
     * КРИТИЧНО: почему /queue, а не /user в enableSimpleBroker?
     * ─────────────────────────────────────────────────────────
     * UserDestinationMessageHandler перехватывает клиентские подписки на /user/**
     * и ПЕРЕПИСЫВАЕТ их во внутренние пути вида /queue/room-joined-user{sessionId}.
     * SimpleBrokerMessageHandler.checkDestinationPrefix() проверяет, начинается ли
     * назначение с одного из сконфигурированных префиксов.
     * С enableSimpleBroker("/topic", "/user") путь /queue/room-joined-user123 НЕ совпадает
     * ни с /topic, ни с /user → SimpleBroker молча дропает сообщение.
     * Именно поэтому ROOM_JOINED никогда не доходил до клиента и навигации не было.
     * Правильная конфигурация: /topic (broadcast) + /queue (user-specific).
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Мы регистрируем /topic и /queue как префиксы для Simple Broker.
        // /topic — для широковещательных рассылок (GameStateMessage, LobbyState и т.д.)
        // /queue — для персональных очередей (ROOM_JOINED, ошибки зрителей и т.д.)
        //          UserDestinationMessageHandler переписывает /user/queue/X → /queue/X-user{id}
        config.enableSimpleBroker("/topic", "/queue");

        // Мы задаём префикс для @MessageMapping: клиент шлёт на /app/lobby/create,
        // Spring ищет @MessageMapping("/lobby/create")
        config.setApplicationDestinationPrefixes("/app");

        // Мы задаём префикс для пользовательских назначений.
        // Клиент подписывается на /user/queue/room-joined →
        // Spring переписывает в /queue/room-joined-user{sessionId} для брокера
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
