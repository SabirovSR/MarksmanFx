package org.example.marksmanfx.client.network.dto;

/**
 * Первичное аутентификационное сообщение WebSocket.
 *
 * Мы отправляем этот DTO серверу сразу после открытия WebSocket-соединения,
 * в коллбеке {@code onOpen()}. Это необходимо для протоколов, где JWT нельзя
 * передать в заголовках HTTP Upgrade (например, некоторые окружения проксируют
 * WebSocket без кастомных заголовков).
 *
 * Два подхода к JWT в WebSocket — мы поддерживаем оба:
 *   1. STOMP-заголовок CONNECT:  "Authorization: Bearer <token>" в STOMP CONNECT фрейме.
 *      Обрабатывается {@code JwtChannelInterceptor} на сервере.
 *   2. Первое сообщение (этот класс): JSON {"type":"AUTH","token":"..."} сразу после onOpen().
 *      Используется для сырых WebSocket без STOMP, или как дополнительный fallback.
 *
 * Пример сериализованного JSON:
 * {
 *   "type": "AUTH",
 *   "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJBcmNoZXJLaW5nIn0.xxx"
 * }
 *
 * Сервер получает это сообщение, извлекает токен, валидирует его через JwtService
 * и либо подтверждает сессию, либо закрывает соединение.
 */
public record AuthMessage(String type, String token) {

    /** Мы всегда создаём сообщение с типом "AUTH" — это фиксированный протокольный идентификатор */
    public AuthMessage(String token) {
        this("AUTH", token);
    }

    /**
     * Мы сериализуем сообщение в JSON вручную, чтобы не тащить зависимость
     * в тех частях кода, где Jackson может быть недоступен.
     */
    public String toJson() {
        return String.format("{\"type\":\"%s\",\"token\":\"%s\"}", type, token);
    }
}
