package org.example.marksmanfx.client.ui.login;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import org.example.marksmanfx.client.auth.SessionManager;
import org.example.marksmanfx.client.auth.TokenStorage;
import org.example.marksmanfx.client.network.ApiClient;
import org.example.marksmanfx.client.network.MessageListener;
import org.example.marksmanfx.client.network.ServerConnection;
import org.example.marksmanfx.client.ui.SceneManager;
import org.example.marksmanfx.client.ui.WindowDragUtil;
import org.example.marksmanfx.common.message.ServerMessage;

/**
 * Контроллер экрана входа / регистрации.
 *
 * Мы переходим от прямого TCP-подключения к REST API аутентификации.
 * Алгоритм:
 *   1. Пользователь вводит логин, пароль, URL сервера.
 *   2. POST /api/auth/login → JWT-токен.
 *   3. Если «Запомнить меня» → сохраняем токен в {@link TokenStorage}.
 *   4. {@link SessionManager} обновляется — сессия активна.
 *   5. Переходим на экран лобби.
 *
 * Регистрация идёт по аналогичному пути:
 *   POST /api/auth/register → JWT-токен (пользователь сразу залогинен).
 */
public final class LoginController implements MessageListener {

    @FXML private BorderPane  rootPane;
    @FXML private HBox        titleBar;
    @FXML private TextField   usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField   serverUrlField;
    @FXML private CheckBox    rememberMeBox;
    @FXML private Button      loginButton;
    @FXML private Button      registerButton;
    @FXML private Label       statusLabel;

    private SceneManager     sceneManager;
    private ServerConnection connection;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Вызывается SceneManager после загрузки FXML
    public void init(SceneManager sceneManager, ServerConnection connection) {
        this.sceneManager = sceneManager;
        this.connection   = connection;
        WindowDragUtil.enable(titleBar, sceneManager.getStage());
    }

    @FXML
    private void initialize() {
        // Мы предзаполняем поля последними использованными значениями из хранилища
        serverUrlField.setText(TokenStorage.getServerUrl());
        usernameField.setText(TokenStorage.getLastUsername());

        // Мы переводим фокус на поле пароля если логин уже предзаполнен
        if (!usernameField.getText().isEmpty()) {
            Platform.runLater(passwordField::requestFocus);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Обработчики кнопок
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Мы обрабатываем нажатие кнопки «Войти».
     * POST /api/auth/login → JWT → SessionManager → LobbyScreen.
     */
    @FXML
    private void onLogin() {
        String username  = usernameField.getText().trim();
        String password  = passwordField.getText();
        String serverUrl = serverUrlField.getText().trim();

        if (!validateInputs(username, password, serverUrl)) return;

        setFormDisabled(true);
        setStatus("Входим в систему…");

        // Мы выполняем HTTP-запрос в виртуальном потоке, не блокируя JavaFX
        Thread.ofVirtual().name("login-request").start(() -> {
            ApiClient api = new ApiClient(serverUrl);
            ApiClient.ApiResponse<String> response = api.login(username, password);

            Platform.runLater(() -> {
                if (response.isOk()) {
                    handleAuthSuccess(response.body(), username, serverUrl);
                } else if (response.isUnauthorized()) {
                    setStatus("Неверное имя пользователя или пароль.");
                    setFormDisabled(false);
                } else if (response.isNetworkError()) {
                    setStatus("Сервер недоступен: " + serverUrl);
                    setFormDisabled(false);
                } else {
                    setStatus("Ошибка сервера (HTTP " + response.statusCode() + ").");
                    setFormDisabled(false);
                }
            });
        });
    }

    /**
     * Мы обрабатываем нажатие кнопки «Зарегистрироваться».
     * POST /api/auth/register → JWT → SessionManager → LobbyScreen.
     * После регистрации пользователь сразу входит в систему.
     */
    @FXML
    private void onRegister() {
        String username  = usernameField.getText().trim();
        String password  = passwordField.getText();
        String serverUrl = serverUrlField.getText().trim();

        if (!validateInputs(username, password, serverUrl)) return;

        if (password.length() < 6) {
            setStatus("Пароль должен содержать не менее 6 символов.");
            return;
        }

        setFormDisabled(true);
        setStatus("Регистрируемся…");

        Thread.ofVirtual().name("register-request").start(() -> {
            ApiClient api = new ApiClient(serverUrl);
            ApiClient.ApiResponse<String> response = api.register(username, password);

            Platform.runLater(() -> {
                if (response.isOk()) {
                    handleAuthSuccess(response.body(), username, serverUrl);
                } else {
                    // Мы разбираем тело ошибки — там может быть "Пользователь уже существует"
                    String errorMsg = extractErrorMessage(response.body(),
                            "Ошибка регистрации (HTTP " + response.statusCode() + ")");
                    setStatus(errorMsg);
                    setFormDisabled(false);
                }
            });
        });
    }

    @FXML private void onMinimize() { sceneManager.getStage().setIconified(true); }
    @FXML private void onClose()    { connection.disconnect(); Platform.exit(); }

    // ──────────────────────────────────────────────────────────────────────
    //  Логика обработки успешной аутентификации
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Мы обрабатываем успешный ответ от сервера (200 OK с JWT в теле).
     *
     * Последовательность действий:
     *   1. Разбираем JSON и извлекаем токен.
     *   2. Обновляем SessionManager в оперативной памяти.
     *   3. Если "Запомнить меня" — сохраняем в Preferences (через SessionManager).
     *   4. Переходим на экран лобби.
     */
    private void handleAuthSuccess(String responseBody, String username, String serverUrl) {
        try {
            JsonNode json  = objectMapper.readTree(responseBody);
            String   token = json.get("token").asText();

            boolean rememberMe = rememberMeBox.isSelected();

            // Мы фиксируем активную сессию — оба слоя (RAM + Preferences) обновляются
            SessionManager.getInstance().login(username, token, serverUrl, rememberMe);

            setStatus("Добро пожаловать, " + username + "!");
            // Мы переходим в лобби — здесь используем username как идентификатор
            sceneManager.showLobby(username, username);

        } catch (Exception e) {
            setStatus("Ошибка обработки ответа сервера: " + e.getMessage());
            setFormDisabled(false);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Публичный метод для вызова из SceneManager (автологин с ошибкой)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Мы устанавливаем сообщение о статусе программно.
     * Вызывается из {@link SceneManager#showLoginWithError} при ошибке автологина.
     */
    public void setStatusMessage(String message) {
        Platform.runLater(() -> setStatus(message));
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Обратная совместимость — TCP MessageListener (legacy)
    // ──────────────────────────────────────────────────────────────────────

    /** Мы оставляем реализацию MessageListener для обратной совместимости с TCP-сервером */
    @Override
    public void onMessage(ServerMessage message) {}

    @Override
    public void onDisconnected() {
        setFormDisabled(false);
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Вспомогательные методы
    // ──────────────────────────────────────────────────────────────────────

    private boolean validateInputs(String username, String password, String serverUrl) {
        if (username.isEmpty()) { setStatus("Введите имя пользователя."); return false; }
        if (password.isEmpty()) { setStatus("Введите пароль."); return false; }
        if (serverUrl.isEmpty()) { setStatus("Введите адрес сервера."); return false; }
        return true;
    }

    private void setFormDisabled(boolean disabled) {
        loginButton.setDisable(disabled);
        registerButton.setDisable(disabled);
        usernameField.setDisable(disabled);
        passwordField.setDisable(disabled);
        serverUrlField.setDisable(disabled);
    }

    private void setStatus(String text) {
        statusLabel.setText(text);
    }

    /** Мы извлекаем поле "error" из JSON-ответа ошибки, либо возвращаем дефолтный текст */
    private String extractErrorMessage(String json, String defaultMsg) {
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.has("error")) return node.get("error").asText();
            if (node.has("message")) return node.get("message").asText();
        } catch (Exception ignored) {}
        return defaultMsg;
    }
}
