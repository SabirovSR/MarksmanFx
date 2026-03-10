package org.example.marksmanfx.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.example.marksmanfx.client.auth.AutoLoginService;
import org.example.marksmanfx.client.auth.SessionManager;
import org.example.marksmanfx.client.auth.UserProfileDto;
import org.example.marksmanfx.client.network.ServerConnection;
import org.example.marksmanfx.client.ui.SceneManager;

import java.util.logging.Logger;

/**
 * Точка входа JavaFX-клиента.
 *
 * Жизненный цикл старта (автологин):
 *
 *   start()
 *     │
 *     ├─ [Показываем экран загрузки, пока идёт проверка токена]
 *     │
 *     └─ AutoLoginService.check()
 *           │
 *           ├── Токена нет в хранилище ──────────────────► showLogin()
 *           │
 *           ├── HTTP GET /api/auth/me:
 *           │     ├── 200 OK → восстановить сессию ──────► showLobby()
 *           │     ├── 401/403 → очистить токен ───────────► showLogin()
 *           │     └── Сетевая ошибка → уведомить ─────────► showLogin() + статус ошибки
 *           │
 *           └── [Сеть работает в виртуальном потоке, UI не блокируется]
 */
public final class ClientApp extends Application {

    private static final Logger LOG = Logger.getLogger(ClientApp.class.getName());

    /**
     * TCP-соединение оставляем для обратной совместимости с legacy TCP-сервером.
     * В новой архитектуре WebSocket управляется через StompWebSocketClient.
     */
    private final ServerConnection connection    = new ServerConnection();
    private final AutoLoginService autoLoginService = new AutoLoginService();

    @Override
    public void start(Stage stage) {
        // Мы настраиваем окно без системных рамок — каждый экран рисует свою шапку
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setTitle("Меткий стрелок — сетевая игра");
        stage.setMinWidth(500);
        stage.setMinHeight(420);

        SceneManager sceneManager = new SceneManager(stage, connection);

        stage.setOnCloseRequest(e -> {
            connection.disconnect();
            Platform.exit();
        });

        // Мы запускаем флоу автологина вместо безусловного показа LoginScreen
        launchAutoLogin(sceneManager);
    }

    /**
     * Мы реализуем флоу автоматического входа при старте приложения.
     *
     * Алгоритм:
     *   1. Показываем экран загрузки (чтобы окно появилось моментально).
     *   2. AutoLoginService читает токен из Preferences и запускает проверку.
     *   3. В зависимости от результата — переходим в лобби или на экран входа.
     *
     * Все переходы между экранами происходят в JavaFX потоке (через Platform.runLater
     * внутри AutoLoginService).
     */
    private void launchAutoLogin(SceneManager sceneManager) {
        // Мы сразу показываем экран загрузки — окно появляется без задержки,
        // даже если проверка токена займёт несколько секунд
        sceneManager.showSplash();

        autoLoginService.check(
                // Случай 1: Токен валиден — сессия восстановлена → сразу в лобби
                (UserProfileDto profile) -> {
                    LOG.info("[ClientApp] Автологин успешен для: " + profile.username());
                    SessionManager session = SessionManager.getInstance();
                    sceneManager.showLobby(profile.username(), profile.username());
                },

                // Случай 2: Токен протух (401/403) → показываем форму входа
                () -> {
                    LOG.info("[ClientApp] Сохранённый токен недействителен → экран входа");
                    sceneManager.showLogin();
                },

                // Случай 3: Токена нет (первый запуск или явный логаут) → сразу на вход
                () -> {
                    LOG.info("[ClientApp] Сохранённый токен отсутствует → экран входа");
                    sceneManager.showLogin();
                },

                // Случай 4: Сетевая ошибка → показываем вход с предупреждением
                (String errorMsg) -> {
                    LOG.warning("[ClientApp] Сеть недоступна при автологине: " + errorMsg);
                    // Мы переходим на экран входа и передаём сообщение об ошибке
                    sceneManager.showLoginWithError(errorMsg);
                }
        );
    }

    @Override
    public void stop() {
        connection.disconnect();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
