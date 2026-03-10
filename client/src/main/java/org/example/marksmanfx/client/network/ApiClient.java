package org.example.marksmanfx.client.network;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Logger;

/**
 * HTTP-клиент для общения с REST API Spring Boot сервера.
 *
 * Мы используем {@link java.net.http.HttpClient}, встроенный в JDK с Java 11.
 * Это позволяет обойтись без внешних HTTP-библиотек (OkHttp, Apache HttpClient).
 *
 * Клиент настроен на работу с JWT Bearer токенами.
 * Все операции выполняются синхронно (send(), а не sendAsync()) —
 * вызывающий код сам управляет потоком через {@code Thread.ofVirtual()}.
 *
 * ObjectMapper настроен с FAIL_ON_UNKNOWN_PROPERTIES=false — это защита
 * от ситуации когда сервер добавляет новые поля в ответ, а клиент старой версии.
 */
public final class ApiClient {

    private static final Logger LOG = Logger.getLogger(ApiClient.class.getName());

    /** Таймаут на одиночный HTTP-запрос */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient   httpClient;
    private final ObjectMapper objectMapper;
    private final String       baseUrl;

    /**
     * Мы создаём клиент привязанный к конкретному серверу.
     *
     * @param baseUrl  базовый URL, например "http://localhost:8080"
     */
    public ApiClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                // HTTP/2 со стратегией downgrade до HTTP/1.1 при необходимости
                .version(HttpClient.Version.HTTP_2)
                .build();
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Аутентификация
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Мы отправляем запрос на регистрацию нового пользователя.
     *
     * @param username имя пользователя
     * @param password пароль в открытом виде (HTTPS шифрует транспорт)
     * @return {@link ApiResponse} с телом ответа или кодом ошибки
     */
    public ApiResponse<String> register(String username, String password) {
        String body = String.format(
                "{\"username\":\"%s\",\"password\":\"%s\"}",
                escapeJson(username), escapeJson(password)
        );
        return post("/api/auth/register", body, null);
    }

    /**
     * Мы отправляем запрос на аутентификацию.
     * Возвращает JSON с полем "token" при успехе, либо ошибку.
     */
    public ApiResponse<String> login(String username, String password) {
        String body = String.format(
                "{\"username\":\"%s\",\"password\":\"%s\"}",
                escapeJson(username), escapeJson(password)
        );
        return post("/api/auth/login", body, null);
    }

    /**
     * Мы проверяем валидность токена через GET /api/auth/me.
     *
     * Сервер проверяет подпись и срок действия JWT, а затем возвращает
     * профиль пользователя. Если токен протух — 401 Unauthorized.
     *
     * @param jwtToken Bearer токен для проверки
     * @return {@link ApiResponse} с JSON профиля (200) или кодом ошибки (401/403)
     */
    public ApiResponse<String> validateToken(String jwtToken) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/auth/me"))
                .header("Authorization", "Bearer " + jwtToken)
                .header("Accept", "application/json")
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();

        return execute(request);
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Вспомогательные методы
    // ──────────────────────────────────────────────────────────────────────

    private ApiResponse<String> post(String path, String jsonBody, String bearerToken) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        return execute(builder.build());
    }

    /**
     * Мы выполняем HTTP-запрос синхронно и оборачиваем результат в ApiResponse.
     * При сетевой ошибке возвращаем ApiResponse с кодом -1 и сообщением исключения.
     */
    private ApiResponse<String> execute(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );
            LOG.fine("[ApiClient] " + request.method() + " "
                    + request.uri() + " → " + response.statusCode());
            return new ApiResponse<>(response.statusCode(), response.body());
        } catch (Exception e) {
            LOG.warning("[ApiClient] Ошибка запроса " + request.uri() + ": " + e.getMessage());
            return new ApiResponse<>(-1, e.getMessage());
        }
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    /** Мы экранируем спецсимволы перед вставкой в JSON-строку вручную */
    private static String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Вложенный record для ответа
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Мы оборачиваем HTTP-ответ в типизированный объект.
     * Это позволяет не бросать исключения при ошибочных кодах (4xx/5xx),
     * а обрабатывать их явно в вызывающем коде.
     */
    public record ApiResponse<T>(int statusCode, T body) {
        public boolean isOk()          { return statusCode >= 200 && statusCode < 300; }
        public boolean isUnauthorized() { return statusCode == 401 || statusCode == 403; }
        public boolean isNetworkError() { return statusCode == -1; }
    }
}
