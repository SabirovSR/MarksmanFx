package org.example.marksmanfx.client.ui;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
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
 * Manages scene transitions: Login → Lobby → Game (and back).
 *
 * A single {@link Scene} is reused across transitions — only its root node
 * is swapped. After each swap {@code stage.sizeToScene()} re-fits the window
 * to the new screen's preferred dimensions and re-centers it on screen.
 */
public final class SceneManager {

    private final Stage            stage;
    private final ServerConnection connection;
    private       Scene            currentScene;

    public SceneManager(Stage stage, ServerConnection connection) {
        this.stage      = stage;
        this.connection = connection;
    }

    // ─── Screen transitions ───────────────────────────────────────────────────

    public void showLogin() {
        try {
            FXMLLoader loader = loader("login.fxml");
            Parent root = loader.load();
            LoginController ctrl = loader.getController();
            ctrl.init(this, connection);
            applyScene(root);
            connection.setListener(ctrl);
        } catch (IOException e) {
            throw new RuntimeException("Cannot load login.fxml", e);
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
            throw new RuntimeException("Cannot load lobby.fxml", e);
        }
    }

    /**
     * Transitions to the game / waiting-room screen.
     *
     * @param localPlayerId   server-assigned UUID of the local player
     * @param localNickname   display name (used when returning to lobby)
     * @param initialPlayers  roster at the moment of room join; shown immediately
     *                        in the waiting overlay so the user sees who is already there
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
            throw new RuntimeException("Cannot load game.fxml", e);
        }
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    /**
     * Either creates the scene (first call) or swaps the root node.
     * Resizes the stage to the new content's preferred size after the layout pass.
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

        // Resize the window to exactly fit the new screen's preferred size,
        // then re-centre on the display.
        Platform.runLater(() -> {
            stage.sizeToScene();
            stage.centerOnScreen();
        });
    }

    private FXMLLoader loader(String fxml) {
        URL resource = getClass().getResource("/org/example/marksmanfx/client/" + fxml);
        if (resource == null) throw new RuntimeException("FXML not found: " + fxml);
        return new FXMLLoader(resource);
    }

    public Stage getStage() { return stage; }
}
