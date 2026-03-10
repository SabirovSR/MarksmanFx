package org.example.marksmanfx.client.ui;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.example.marksmanfx.client.network.ServerConnection;
import org.example.marksmanfx.client.ui.game.GameController;
import org.example.marksmanfx.client.ui.lobby.LobbyController;
import org.example.marksmanfx.client.ui.login.LoginController;
import org.example.marksmanfx.common.model.PlayerInfo;

import java.io.IOException;
import java.net.URL;
import java.util.List;

/**
 * Управляет переходами между сценами: вход -> лобби -> игра и обратно.
 *
 * Используется одна {@link Scene}: при переходе меняется только корневой узел.
 * После каждой замены {@code stage.sizeToScene()} подгоняет окно под
 * предпочтительные размеры нового экрана и заново центрирует его.
 */
public final class SceneManager {

    private final Stage            stage;
    private final ServerConnection connection;
    private       Scene            currentScene;

    public SceneManager(Stage stage, ServerConnection connection) {
        this.stage      = stage;
        this.connection = connection;
    }

    // Переходы между экранами.

    /**
     * Мы показываем экран-заставку с индикатором загрузки.
     * Отображается пока идёт проверка сохранённого токена через REST API.
     * Создаётся программно — отдельный FXML не нужен для простой заглушки.
     */
    public void showSplash() {
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(48, 48);

        Label label = new Label("Проверяем сохранённую сессию…");
        label.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 14px;");

        VBox root = new VBox(20, spinner, label);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: #1e1e2e;");
        root.setPrefSize(500, 420);

        applyScene(root);
    }

    /**
     * Мы показываем экран входа с предзаполненным сообщением об ошибке.
     * Используется при автологине, когда сервер был недоступен.
     */
    public void showLoginWithError(String errorMessage) {
        try {
            FXMLLoader loader = loader("login.fxml");
            Parent root = loader.load();
            LoginController ctrl = loader.getController();
            ctrl.init(this, connection);
            ctrl.setStatusMessage(errorMessage);
            applyScene(root);
            connection.setListener(ctrl);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось загрузить login.fxml", e);
        }
    }

    public void showLogin() {
        try {
            FXMLLoader loader = loader("login.fxml");
            Parent root = loader.load();
            LoginController ctrl = loader.getController();
            ctrl.init(this, connection);
            applyScene(root);
            connection.setListener(ctrl);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось загрузить login.fxml", e);
        }
    }

    public void showLobby(String playerId, String nickname) {
        try {
            FXMLLoader loader = loader("lobby.fxml");
            Parent root = loader.load();
            LobbyController ctrl = loader.getController();
            ctrl.init(this, connection, playerId, nickname);
            applyScene(root);
            connection.setListener(ctrl);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось загрузить lobby.fxml", e);
        }
    }

    /**
     * Переключает интерфейс на игровой экран и экран ожидания комнаты.
     *
     * @param localPlayerId  UUID локального игрока, назначенный сервером
     * @param localNickname  отображаемое имя игрока для возврата в лобби
     * @param initialPlayers список игроков в комнате на момент входа;
     *                       сразу показывается в оверлее ожидания
     */
    public void showGame(String localPlayerId,
                         String localNickname,
                         List<PlayerInfo> initialPlayers) {
        try {
            FXMLLoader loader = loader("game.fxml");
            Parent root = loader.load();
            GameController ctrl = loader.getController();
            ctrl.init(this, connection, localPlayerId, localNickname, initialPlayers);
            applyScene(root);
            connection.setListener(ctrl);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось загрузить game.fxml", e);
        }
    }

    // Внутренние вспомогательные методы.
    /**
     * Создаёт сцену при первом вызове или подменяет её корневой узел.
     * После прохода layout изменяет размер окна под предпочтительный размер содержимого.
     */
    private void applyScene(Parent root) {
        if (currentScene == null) {
            currentScene = new Scene(root);
            URL css = getClass().getResource("/org/example/marksmanfx/client/style.css");
            if (css != null) currentScene.getStylesheets().add(css.toExternalForm());
            stage.setScene(currentScene);
            stage.show();
        } else {
            currentScene.setRoot(root);
        }

        // Подгоняем размер окна под новый экран и затем центрируем его на дисплее.
        Platform.runLater(() -> {
            stage.sizeToScene();
            stage.centerOnScreen();
        });
    }

    private FXMLLoader loader(String fxml) {
        URL resource = getClass().getResource("/org/example/marksmanfx/client/" + fxml);
        if (resource == null) throw new RuntimeException("FXML не найден: " + fxml);
        return new FXMLLoader(resource);
    }

    public Stage getStage() { return stage; }
}

