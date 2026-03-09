package org.example.marksmanfx.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.example.marksmanfx.client.network.ServerConnection;
import org.example.marksmanfx.client.ui.SceneManager;

/**
 * Точка входа JavaFX-клиента для сетевой игры.
 * Окно без системных рамок: каждый экран рисует собственную шапку,
 * поддерживает перетаскивание через {@link org.example.marksmanfx.client.ui.WindowDragUtil}
 * и использует свои кнопки сворачивания и закрытия.
 */
public final class ClientApp extends Application {

    private final ServerConnection connection = new ServerConnection();

    @Override
    public void start(Stage stage) {
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setTitle("Меткий стрелок — сетевая игра");

        // Жёсткий минимум, чтобы содержимое никогда не обрезалось.
        // SceneManager подгоняет окно под предпочтительный размер текущего FXML.
        stage.setMinWidth(500);
        stage.setMinHeight(420);

        SceneManager sceneManager = new SceneManager(stage, connection);

        stage.setOnCloseRequest(e -> {
            connection.disconnect();
            Platform.exit();
        });

        sceneManager.showLogin();
    }

    @Override
    public void stop() {
        connection.disconnect();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

