package org.example.marksmanfx.client.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import org.example.marksmanfx.client.network.ApiClient;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Сервис автоматического входа при старте приложения.
 *
 * Мы инкапсулируем весь флоу проверки сохранённой сессии в одном классе.
 * Это позволяет держать {@code ClientApp.start()} чистым — там только
 * вызов одного метода и обработка двух возможных исходов.
 *
 * Поток выполнения:
 *   1. Читаем токен из {@link TokenStorage} (Preferences).
 *   2. Если токена нет → сразу вызываем {@code onNoSession}.
 *   3. Если токен есть → запускаем виртуальный поток для HTTP-запроса.
 *   4. GET /api/auth/me с заголовком Authorization: Bearer <token>.
 *   5. 200 OK → парсим профиль → восстанавливаем сессию → вызываем {@code onSuccess}.
 *   6. 401/403  → удаляем протухший токен → вызываем {@code onExpired}.
 *   7. Сетевая ошибка → вызываем {@code onNetworkError}.
 *
 * Все callbacks вызываются в JavaFX Application Thread через {@code Platform.runLater()}.
 */
public final class AutoLoginService {

    private static final Logger LOG = Logger.getLogger(AutoLoginService.class.getName());

    /**
     * Мы запускаем процесс автологина в момент вызова.
     *
     * Метод неблокирующий: проверка токена идёт в виртуальном потоке
     * (Project Loom, доступен с Java 21). JavaFX поток не блокируется —
     * UI остаётся отзывчивым во время сетевого запроса.
     *
     * @param onSuccess       токен валиден, профиль восстановлен → показать LobbyScreen
     * @param onExpired       токен просрочен или неверен → показать LoginScreen
     * @param onNoSession     токена нет в хранилище → сразу показать LoginScreen
     * @param onNetworkError  сервер недоступен → показать LoginScreen с предупреждением
     */
    public void check(
            Consumer<UserProfileDto> onSuccess,
            Runnable                 onExpired,
            Runnable                 onNoSession,
            Consumer<String>         onNetworkError
    ) {
        // Читаем сохранённый токен из Preferences
        Optional<String> savedToken = TokenStorage.getToken();

        if (savedToken.isEmpty()) {
            // Мы не нашли сохранённого токена — это первый запуск или явный логаут
            LOG.info("[AutoLogin] Токен не найден в хранилище → экран входа");
            runOnFx(onNoSession);
            return;
        }

        String token     = savedToken.get();
        String serverUrl = TokenStorage.getServerUrl();

        LOG.info("[AutoLogin] Найден сохранённый токен, проверяем на сервере: " + serverUrl);

        // Мы запускаем проверку в виртуальном потоке, чтобы не блокировать JavaFX поток
        Thread.ofVirtual()
              .name("autologin-check")
              .start(() -> performCheck(token, serverUrl, onSuccess, onExpired, onNetworkError));
    }

    /**
     * Мы отправляем запрос к /api/auth/me и обрабатываем ответ.
     * Метод выполняется вне JavaFX потока — все callbacks направляем через runOnFx().
     */
    private void performCheck(
            String token,
            String serverUrl,
            Consumer<UserProfileDto> onSuccess,
            Runnable                 onExpired,
            Consumer<String>         onNetworkError
    ) {
        ApiClient client = new ApiClient(serverUrl);
        ApiClient.ApiResponse<String> response = client.validateToken(token);

        if (response.isOk()) {
            // Мы разбираем JSON-ответ и восстанавливаем сессию в памяти
            try {
                ObjectMapper mapper  = client.getObjectMapper();
                UserProfileDto profile = mapper.readValue(response.body(), UserProfileDto.class);

                if (!profile.isValid()) {
                    LOG.warning("[AutoLogin] Сервер вернул невалидный профиль");
                    TokenStorage.clearToken();
                    runOnFx(onExpired);
                    return;
                }

                // Мы восстанавливаем сессию — теперь SessionManager знает о текущем пользователе
                SessionManager.getInstance().restore(profile.username(), token, serverUrl);
                LOG.info("[AutoLogin] Сессия восстановлена: " + profile.username());

                runOnFx(() -> onSuccess.accept(profile));

            } catch (Exception e) {
                LOG.warning("[AutoLogin] Ошибка парсинга профиля: " + e.getMessage());
                TokenStorage.clearToken();
                runOnFx(onExpired);
            }

        } else if (response.isUnauthorized()) {
            // Мы получили 401/403 — токен истёк или отозван на сервере
            LOG.info("[AutoLogin] Токен недействителен (HTTP " + response.statusCode() + ") → удаляем");
            // Удаляем протухший токен — автологин больше не сработает до нового входа
            TokenStorage.clearToken();
            runOnFx(onExpired);

        } else if (response.isNetworkError()) {
            // Мы не смогли достучаться до сервера — сеть недоступна
            LOG.warning("[AutoLogin] Сервер недоступен: " + response.body());
            // Токен не удаляем — при восстановлении сети автологин должен сработать
            runOnFx(() -> onNetworkError.accept(
                    "Сервер недоступен (" + serverUrl + "). Проверьте подключение."
            ));

        } else {
            // Мы получили неожиданный HTTP-код (5xx и т.д.)
            LOG.warning("[AutoLogin] Неожиданный ответ сервера: HTTP " + response.statusCode());
            runOnFx(onExpired);
        }
    }

    /** Мы запускаем действие в JavaFX Application Thread */
    private static void runOnFx(Runnable action) {
        Platform.runLater(action);
    }
}
