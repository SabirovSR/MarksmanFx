package org.example.marksmanfx.client.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import org.example.marksmanfx.client.network.dto.AuthMessage;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * WebSocket / STOMP клиент для JavaFX приложения.
 *
 * Мы реализуем минимальный STOMP-клиент поверх нативного Java WebSocket.
 * STOMP — текстовый протокол поверх WebSocket. Формат фрейма:
 *
 *   COMMAND\n
 *   header1:value1\n
 *   \n
 *   body\0
 *
 * Аутентификация реализована двумя слоями:
 *   1. Задача 4: AuthMessage — отправляем сразу в onOpen() до STOMP handshake.
 *      Это защищает от сред, которые не передают кастомные HTTP-заголовки при апгрейде.
 *   2. STOMP CONNECT — JWT в заголовке Authorization: Bearer <token>.
 *      Обрабатывается JwtChannelInterceptor на Spring Boot сервере.
 *
 * Зависимость для pom.xml клиента:
 *   <dependency>
 *     <groupId>org.java-websocket</groupId>
 *     <artifactId>Java-WebSocket</artifactId>
 *     <version>1.5.4</version>
 *   </dependency>
 */
public class StompWebSocketClient {

    private static final Logger LOG = Logger.getLogger(StompWebSocketClient.class.getName());

    // STOMP команды
    private static final String CONNECT    = "CONNECT";
    private static final String CONNECTED  = "CONNECTED";
    private static final String SUBSCRIBE  = "SUBSCRIBE";
    private static final String SEND       = "SEND";
    private static final String DISCONNECT = "DISCONNECT";
    private static final String MESSAGE    = "MESSAGE";

    private final String serverUrl;
    private final String jwtToken;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private RawWebSocketConnection          ws;
    private final AtomicInteger             subscriptionCounter = new AtomicInteger(0);
    private final Map<String, Consumer<String>> subscriptions   = new ConcurrentHashMap<>();
    private final CountDownLatch            connectedLatch      = new CountDownLatch(1);
    private volatile boolean                connected           = false;

    /**
     * Мы создаём клиент с URL сервера и JWT-токеном для аутентификации.
     *
     * @param serverUrl  WebSocket URL: ws://localhost:8080/ws
     * @param jwtToken   токен, полученный через POST /api/auth/login
     */
    public StompWebSocketClient(String serverUrl, String jwtToken) {
        this.serverUrl = serverUrl;
        this.jwtToken  = jwtToken;
    }

    /**
     * Мы подключаемся к серверу, выполняем двойную JWT-аутентификацию
     * и завершаем STOMP handshake.
     *
     * Порядок:
     *   1. TCP + HTTP Upgrade → WebSocket соединение открыто (onOpen срабатывает)
     *   2. onOpen: отправляем AuthMessage{"type":"AUTH","token":"..."}
     *   3. onOpen: отправляем STOMP CONNECT с JWT в заголовке
     *   4. Сервер отвечает STOMP CONNECTED
     *
     * Блокирует текущий поток до готовности соединения (таймаут 5 сек).
     */
    public void connect() throws Exception {
        ws = new RawWebSocketConnection(new URI(serverUrl));
        ws.connect();

        // Ждём установки WebSocket соединения
        if (!connectedLatch.await(5, TimeUnit.SECONDS)) {
            throw new RuntimeException("Таймаут подключения к WebSocket: " + serverUrl);
        }
    }

    /**
     * Мы подписываемся на STOMP-топик.
     * Callback вызывается в JavaFX Application Thread через Platform.runLater.
     *
     * @param destination  например "/topic/game/A1B2C3D4"
     * @param onMessage    получает JSON-тело фрейма
     */
    public void subscribe(String destination, Consumer<String> onMessage) {
        String subId = "sub-" + subscriptionCounter.getAndIncrement();
        subscriptions.put(destination, onMessage);
        sendFrame(SUBSCRIBE,
                "id:" + subId + "\ndestination:" + destination + "\nack:auto",
                "");
        LOG.info("[WS] SUBSCRIBE → " + destination);
    }

    /**
     * Мы отправляем координаты выстрела на сервер.
     * Сервер обработает и перешлёт ShotBroadcastMessage всем в /topic/game/{roomId}.
     */
    public void sendShot(String roomId, double aimX, double aimY, double chargeRatio) {
        String destination = "/app/game/" + roomId + "/shot";
        String body = String.format(
                "{\"aimX\":%.2f,\"aimY\":%.2f,\"chargeRatio\":%.4f}",
                aimX, aimY, chargeRatio
        );
        sendFrame(SEND, "destination:" + destination + "\ncontent-type:application/json", body);
    }

    /**
     * Мы отправляем запрос зрителя на получение роли игрока.
     * Сервер ответит либо обновлённым RoomStateMessage, либо RoomFullEvent.
     */
    public void requestUpgradeToPlayer(String roomId) {
        String destination = "/app/room/" + roomId + "/upgrade";
        String body = "{\"roomId\":\"" + roomId + "\"}";
        sendFrame(SEND, "destination:" + destination + "\ncontent-type:application/json", body);
        LOG.info("[WS] Запрос на смену роли в комнате " + roomId);
    }

    /**
     * Мы подписываемся на персональную очередь ошибок.
     * Сюда придёт RoomFullEvent если мест для игроков нет.
     */
    public void subscribeToErrors(Consumer<String> onError) {
        subscribe("/user/queue/errors", onError);
    }

    public void disconnect() {
        sendFrame(DISCONNECT, "", "");
        if (ws != null) ws.close();
        connected = false;
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Разбор входящих STOMP-фреймов
    // ──────────────────────────────────────────────────────────────────────

    private void onStompMessage(String raw) {
        String[] lines = raw.split("\n");
        if (lines.length == 0) return;
        String command = lines[0].trim();

        switch (command) {
            case CONNECTED -> {
                connected = true;
                LOG.info("[WS] STOMP CONNECTED — соединение полностью установлено");
            }
            case MESSAGE -> {
                String destination = null;
                int bodyStart = 0;
                for (int i = 1; i < lines.length; i++) {
                    if (lines[i].startsWith("destination:")) {
                        destination = lines[i].substring("destination:".length()).trim();
                    }
                    if (lines[i].isEmpty()) { bodyStart = i + 1; break; }
                }
                if (destination != null && bodyStart < lines.length) {
                    StringBuilder body = new StringBuilder();
                    for (int i = bodyStart; i < lines.length; i++) body.append(lines[i]);
                    String json  = body.toString().replace("\0", "");
                    String dest  = destination;
                    Consumer<String> handler = subscriptions.get(dest);
                    if (handler != null) {
                        // Мы всегда вызываем обработчик в JavaFX-потоке
                        Platform.runLater(() -> handler.accept(json));
                    }
                }
            }
            default -> LOG.fine("[WS] Получен STOMP фрейм: " + command);
        }
    }

    private void sendFrame(String command, String headers, String body) {
        if (ws == null) return;
        String frame = command + "\n"
                + (headers.isEmpty() ? "" : headers + "\n")
                + "\n"
                + body
                + "\0";
        ws.send(frame);
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Внутренний WebSocket-клиент с интеграцией AuthMessage
    // ──────────────────────────────────────────────────────────────────────

    private class RawWebSocketConnection extends WebSocketClient {

        public RawWebSocketConnection(URI uri) {
            super(uri);
        }

        /**
         * Мы обрабатываем открытие WebSocket соединения.
         *
         * Задача 4: Сразу после открытия отправляем AuthMessage с JWT-токеном.
         * Это первое сообщение, которое видит сервер — ещё до STOMP handshake.
         * После него сервер знает, кто подключился, и может авторизовать STOMP-сессию.
         *
         * Порядок строго важен:
         *   1. AuthMessage  — raw JSON, читается до STOMP-парсинга (серверный хэндлер)
         *   2. STOMP CONNECT — с JWT в заголовке (STOMP интерцептор)
         */
        @Override
        public void onOpen(ServerHandshake handshake) {
            LOG.info("[WS] Соединение открыто, HTTP статус: " + handshake.getHttpStatusMessage());

            // Шаг 1: Отправляем AuthMessage — первичная аутентификация сокета
            AuthMessage authMessage = new AuthMessage(jwtToken);
            ws.send(authMessage.toJson());
            LOG.info("[WS] AuthMessage отправлен (токен длиной " + jwtToken.length() + " символов)");

            // Шаг 2: Выполняем STOMP handshake с JWT в заголовке Authorization
            sendFrame(CONNECT,
                    "accept-version:1.2\n" +
                    "heart-beat:10000,10000\n" +
                    "Authorization:Bearer " + jwtToken,
                    "");
            LOG.info("[WS] STOMP CONNECT отправлен");

            // Мы разблокируем connect() после успешного открытия соединения
            connectedLatch.countDown();
        }

        @Override
        public void onMessage(String message) {
            onStompMessage(message);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            connected = false;
            LOG.warning("[WS] Соединение закрыто: code=" + code + ", reason=" + reason
                    + (remote ? " (сервером)" : " (клиентом)"));
        }

        @Override
        public void onError(Exception ex) {
            LOG.severe("[WS] Ошибка: " + ex.getMessage());
        }
    }

    public boolean isConnected() { return connected; }
}
