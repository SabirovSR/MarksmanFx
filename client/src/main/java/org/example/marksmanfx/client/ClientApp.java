package org.example.marksmanfx.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.example.marksmanfx.client.network.ServerConnection;
import org.example.marksmanfx.client.ui.SceneManager;

/**
 * JavaFX entry point for the multiplayer client.
 * Window is undecorated; each screen provides its own title bar with
 * dragging support (via {@link org.example.marksmanfx.client.ui.WindowDragUtil})
 * and custom minimize / close buttons.
 */
public final class ClientApp extends Application {

    private final ServerConnection connection = new ServerConnection();

    @Override
    public void start(Stage stage) {
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setTitle("Меткий стрелок — Multiplayer");

        // Hard minimum so nothing is ever clipped.
        // SceneManager resizes to the actual FXML preferred size on each transition.
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
