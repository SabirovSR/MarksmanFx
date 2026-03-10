package org.example.marksmanfx.client.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * WebSocket клиент для JavaFX приложения.
 *
 * Мы реализуем минимальный STOMP-клиент поверх нативного Java WebSocket.
 * STOMP — текстовый протокол поверх WebSocket. Формат фрейма:
 *
 *   COMMAND\n
 *   header1:value1\n
 *   header2:value2\n
 *   \n
 *   body\0
 *
 * Для полноценного STOMP в JavaFX рекомендуется библиотека kaazing/stomp-client-java
 * или reactor-netty. Здесь мы показываем ручную реализацию для понимания протокола.
 *
 * В реальном проекте лучше использовать:
 *   <dependency>
 *     <groupId>org.springframework</groupId>
 *     <artifactId>spring-websocket</artifactId>
 *   </dependency>
 * и его StompSession.
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
    private static final String CONNECT     = "CONNECT";
    private static final String CONNECTED   = "CONNECTED";
    private static final String SUBSCRIBE   = "SUBSCRIBE";
    private static final String SEND        = "SEND";
    private static final String DISCONNECT  = "DISCONNECT";
    private static final String MESSAGE     = "MESSAGE";

    private final String serverUrl;
    private final String jwtToken;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private RawWebSocketConnection ws;
    private final AtomicInteger subscriptionCounter = new AtomicInteger(0);

    /** Callback для входящих сообщений от сервера: destination → consumer */
    private final java.util.Map<String, Consumer<String>> subscriptions = new java.util.concurrent.ConcurrentHashMap<>();

    private final CountDownLatch connectedLatch = new CountDownLatch(1);
    private volatile boolean connected = false;

    /**
     * Мы создаём клиент с URL сервера и JWT-токеном для аутентификации.
     *
     * @param serverUrl  URL вида ws://localhost:8080/ws
     * @param jwtToken   токен, полученный через POST /api/auth/login
     */
    public StompWebSocketClient(String serverUrl, String jwtToken) {
        this.serverUrl = serverUrl;
        this.jwtToken  = jwtToken;
    }

    /**
     * Мы подключаемся к серверу и выполняем STOMP handshake.
     * Блокирует текущий поток до установки соединения (или таймаута 5 сек).
     */
    public void connect() throws Exception {
        ws = new RawWebSocketConnection(new URI(serverUrl));
        ws.connect();

        // Ждём установки WebSocket соединения (до 5 секунд)
        if (!connectedLatch.await(5, TimeUnit.SECONDS)) {
            throw new RuntimeException("Таймаут подключения к WebSocket серверу");
        }

        // Отправляем STOMP CONNECT фрейм с JWT в заголовке
        sendFrame(CONNECT,
                "accept-version:1.2\n" +
                "heart-beat:10000,10000\n" +
                "Authorization:Bearer " + jwtToken,
                "");

        LOG.info("STOMP CONNECT отправлен на " + serverUrl);
    }

    /**
     * Мы подписываемся на STOMP-топик (например, /topic/game/ABC123).
     * При получении сообщения вызывается callback с JSON-телом фрейма.
     *
     * @param destination  STOMP destination (топик или личная очередь)
     * @param onMessage    callback, вызывается в JavaFX потоке через Platform.runLater
     */
    public void subscribe(String destination, Consumer<String> onMessage) {
        String subId = "sub-" + subscriptionCounter.getAndIncrement();
        subscriptions.put(destination, onMessage);

        sendFrame(SUBSCRIBE,
                "id:" + subId + "\n" +
                "destination:" + destination + "\n" +
                "ack:auto",
                "");

        LOG.info("STOMP SUBSCRIBE → " + destination);
    }

    /**
     * Мы отправляем выстрел на сервер.
     *
     * Пример использования в GameController:
     *   stompClient.sendShot(roomId, aimX, aimY, chargeRatio);
     */
    public void sendShot(String roomId, double aimX, double aimY, double chargeRatio) {
        String destination = "/app/game/" + roomId + "/shot";
        String body = String.format(
                "{\"aimX\":%.2f,\"aimY\":%.2f,\"chargeRatio\":%.4f}",
                aimX, aimY, chargeRatio
        );
        sendFrame(SEND, "destination:" + destination + "\ncontent-type:application/json", body);
        LOG.fine("Выстрел отправлен: " + body);
    }

    /**
     * Мы отправляем запрос зрителя на повышение до статуса игрока.
     */
    public void requestUpgradeToPlayer(String roomId) {
        String destination = "/app/room/" + roomId + "/upgrade";
        String body = "{\"roomId\":\"" + roomId + "\"}";
        sendFrame(SEND, "destination:" + destination + "\ncontent-type:application/json", body);
        LOG.info("Запрос на повышение роли в комнате " + roomId);
    }

    /**
     * Мы подписываемся на персональную очередь ошибок.
     * /user/queue/errors — сюда приходит RoomFullEvent.
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
    //  Внутренний парсинг STOMP-фреймов
    // ──────────────────────────────────────────────────────────────────────

    private void onStompMessage(String raw) {
        // Разбираем фрейм: первая строка — команда, затем заголовки, потом тело
        String[] lines = raw.split("\n");
        if (lines.length == 0) return;

        String command = lines[0].trim();

        switch (command) {
            case CONNECTED -> {
                connected = true;
                LOG.info("STOMP CONNECTED — соединение установлено");
            }
            case MESSAGE -> {
                // Ищем заголовок destination
                String destination = null;
                int bodyStart = 0;
                for (int i = 1; i < lines.length; i++) {
                    if (lines[i].startsWith("destination:")) {
                        destination = lines[i].substring("destination:".length()).trim();
                    }
                    if (lines[i].isEmpty()) {
                        bodyStart = i + 1;
                        break;
                    }
                }
                // Собираем тело фрейма
                if (destination != null && bodyStart < lines.length) {
                    StringBuilder body = new StringBuilder();
                    for (int i = bodyStart; i < lines.length; i++) {
                        body.append(lines[i]);
                    }
                    String finalBody = body.toString().replace("\0", "");
                    String finalDest = destination;

                    Consumer<String> handler = subscriptions.get(finalDest);
                    if (handler != null) {
                        // Вызываем callback в JavaFX Application Thread
                        Platform.runLater(() -> handler.accept(finalBody));
                    }
                }
            }
            default -> LOG.fine("STOMP фрейм: " + command);
        }
    }

    private void sendFrame(String command, String headers, String body) {
        if (ws == null) return;
        StringBuilder frame = new StringBuilder();
        frame.append(command).append("\n");
        if (!headers.isEmpty()) {
            frame.append(headers).append("\n");
        }
        frame.append("\n");
        frame.append(body);
        frame.append("\0");
        ws.send(frame.toString());
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Внутренний WebSocket-клиент
    // ──────────────────────────────────────────────────────────────────────

    private class RawWebSocketConnection extends WebSocketClient {

        public RawWebSocketConnection(URI uri) {
            super(uri);
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
            LOG.info("WebSocket соединение открыто, статус: " + handshake.getHttpStatusMessage());
            connectedLatch.countDown();
        }

        @Override
        public void onMessage(String message) {
            onStompMessage(message);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            connected = false;
            LOG.warning("WebSocket закрыт: code=" + code + ", reason=" + reason);
        }

        @Override
        public void onError(Exception ex) {
            LOG.severe("WebSocket ошибка: " + ex.getMessage());
        }
    }

    public boolean isConnected() {
        return connected;
    }
}
