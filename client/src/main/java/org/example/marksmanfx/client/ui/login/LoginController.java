package org.example.marksmanfx.client.ui.login;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import org.example.marksmanfx.client.network.MessageListener;
import org.example.marksmanfx.client.network.ServerConnection;
import org.example.marksmanfx.client.ui.SceneManager;
import org.example.marksmanfx.client.ui.WindowDragUtil;
import org.example.marksmanfx.common.event.JoinLobbyEvent;
import org.example.marksmanfx.common.message.ConnectedMessage;
import org.example.marksmanfx.common.message.ErrorMessage;
import org.example.marksmanfx.common.message.ServerMessage;

import java.io.IOException;

public final class LoginController implements MessageListener {

    @FXML private BorderPane rootPane;
    @FXML private HBox       titleBar;
    @FXML private TextField  nicknameField;
    @FXML private TextField  hostField;
    @FXML private TextField  portField;
    @FXML private Button     connectButton;
    @FXML private Label      statusLabel;

    private SceneManager     sceneManager;
    private ServerConnection connection;

    // Вызывается SceneManager после загрузки FXML.
    public void init(SceneManager sceneManager, ServerConnection connection) {
        this.sceneManager = sceneManager;
        this.connection   = connection;

        // Включаем перетаскивание окна за шапку.
        WindowDragUtil.enable(titleBar, sceneManager.getStage());
    }

    @FXML
    private void initialize() {
        hostField.setText("localhost");
        portField.setText("55555");
    }

    // Обработчики кнопок.
    @FXML
    private void onConnect() {
        String nickname = nicknameField.getText().trim();
        String host     = hostField.getText().trim();
        String portText = portField.getText().trim();

        if (nickname.isEmpty()) { setStatus("Введите никнейм."); return; }

        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException e) {
            setStatus("Некорректный порт."); return;
        }

        connectButton.setDisable(true);
        setStatus("Подключение к " + host + ":" + port + "...");

        Thread.ofVirtual().start(() -> {
            try {
                connection.connect(host, port);
                connection.send(new JoinLobbyEvent(nickname));
            } catch (IOException ex) {
                Platform.runLater(() -> {
                    setStatus("Ошибка подключения: " + ex.getMessage());
                    connectButton.setDisable(false);
                });
            }
        });
    }

    @FXML private void onMinimize() { sceneManager.getStage().setIconified(true); }
    @FXML private void onClose()    { connection.disconnect(); Platform.exit(); }

    // Обработка сообщений от сервера.
    @Override
    public void onMessage(ServerMessage message) {
        switch (message) {
            case ConnectedMessage m -> sceneManager.showLobby(m.playerId(), m.nickname());
            case ErrorMessage     m -> { setStatus("Ошибка: " + m.text()); connectButton.setDisable(false); }
            default -> {}
        }
    }

    @Override
    public void onDisconnected() {
        setStatus("Соединение разорвано.");
        connectButton.setDisable(false);
    }

    private void setStatus(String text) { statusLabel.setText(text); }
}

