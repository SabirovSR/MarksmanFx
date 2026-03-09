package org.example.marksmanfx.client.ui.lobby;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.example.marksmanfx.client.network.MessageListener;
import org.example.marksmanfx.client.network.ServerConnection;
import org.example.marksmanfx.client.ui.SceneManager;
import org.example.marksmanfx.client.ui.WindowDragUtil;
import org.example.marksmanfx.common.event.CreateRoomEvent;
import org.example.marksmanfx.common.event.JoinRoomEvent;
import org.example.marksmanfx.common.event.QuickMatchEvent;
import org.example.marksmanfx.common.message.ErrorMessage;
import org.example.marksmanfx.common.message.LobbyStateMessage;
import org.example.marksmanfx.common.message.RoomJoinedMessage;
import org.example.marksmanfx.common.message.ServerMessage;
import org.example.marksmanfx.common.model.PlayerInfo;
import org.example.marksmanfx.common.model.RoomInfo;

import java.net.URL;
import java.util.List;
import java.util.Optional;

public final class LobbyController implements MessageListener {

    @FXML private HBox                         titleBar;
    @FXML private Label                        welcomeLabel;
    @FXML private TableView<RoomInfo>          roomTable;
    @FXML private TableColumn<RoomInfo,String> colName;
    @FXML private TableColumn<RoomInfo,String> colPlayers;
    @FXML private TableColumn<RoomInfo,String> colStatus;
    @FXML private Button                       joinButton;
    @FXML private Label                        statusLabel;

    private final ObservableList<RoomInfo> rooms = FXCollections.observableArrayList();

    private SceneManager     sceneManager;
    private ServerConnection connection;
    private String           playerId;
    private String           nickname;

    // Вызывается SceneManager после загрузки FXML.
    public void init(SceneManager sceneManager, ServerConnection connection,
                     String playerId, String nickname) {
        this.sceneManager = sceneManager;
        this.connection   = connection;
        this.playerId     = playerId;
        this.nickname     = nickname;
        welcomeLabel.setText("Добро пожаловать, " + nickname + "!");

        WindowDragUtil.enable(titleBar, sceneManager.getStage());
    }

    @FXML
    private void initialize() {
        colName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().roomName()));
        colPlayers.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().playerCount() + "/" + c.getValue().maxPlayers()));
        colStatus.setCellValueFactory(c -> new SimpleStringProperty(phaseLabel(c.getValue())));

        roomTable.setItems(rooms);
        roomTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, sel) -> joinButton.setDisable(sel == null || sel.playerCount() >= sel.maxPlayers()));
        joinButton.setDisable(true);
    }

    // Обработчики кнопок.
    @FXML
    private void onCreateRoom() {
        Optional<String> result = showCreateRoomDialog();
        result.ifPresent(name -> connection.send(new CreateRoomEvent(name)));
    }

    /**
     * Создаёт модальное окно без системных рамок для ввода названия комнаты.
     * Окно поддерживает перетаскивание за заголовок и выполнено без скругления,
     * чтобы углы совпадали с геометрией самого окна.
     */
    private Optional<String> showCreateRoomDialog() {
        javafx.stage.Stage dlg = new javafx.stage.Stage();
        dlg.initStyle(javafx.stage.StageStyle.UNDECORATED);
        dlg.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        dlg.initOwner(sceneManager.getStage());

        String[] result = {null};

        TextField nameField = new TextField();
        nameField.setPromptText("Например: «Комната героев»");
        nameField.getStyleClass().add("input-field");
        nameField.setMaxWidth(Double.MAX_VALUE);
        nameField.setOnAction(e -> { result[0] = nameField.getText().trim(); dlg.close(); });

        Button okBtn     = new Button("Создать");
        Button cancelBtn = new Button("Отмена");
        okBtn.getStyleClass().add("btn-accent");
        cancelBtn.getStyleClass().add("button");
        okBtn.setDefaultButton(true);
        okBtn.setOnAction(e -> { result[0] = nameField.getText().trim(); dlg.close(); });
        cancelBtn.setOnAction(e -> dlg.close());

        HBox buttons = new HBox(10, okBtn, cancelBtn);
        buttons.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        Label nameLabel = new Label("Название комнаты");
        nameLabel.getStyleClass().add("field-label");

        VBox form = new VBox(10, nameLabel, nameField, buttons);
        form.setPadding(new Insets(16, 20, 20, 20));

        Label title   = new Label("Создать комнату");
        Button closeX = new Button("✕");
        title.getStyleClass().add("toolbar-title");
        closeX.getStyleClass().add("btn-window-close");
        closeX.setOnAction(e -> dlg.close());

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        HBox dialogTitleBar = new HBox(10, title, spacer, closeX);
        dialogTitleBar.getStyleClass().add("toolbar");
        dialogTitleBar.setPadding(new Insets(8, 8, 8, 16));
        dialogTitleBar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        WindowDragUtil.enable(dialogTitleBar, dlg);

        VBox root = new VBox(dialogTitleBar, form);
        root.getStyleClass().addAll("root-pane", "window-shell-flat");

        javafx.scene.Scene scene = new javafx.scene.Scene(root, 380, 200);
        URL css = getClass().getResource("/org/example/marksmanfx/client/style.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());

        dlg.setScene(scene);
        dlg.setOnShown(e -> Platform.runLater(nameField::requestFocus));
        dlg.showAndWait();

        return Optional.ofNullable(result[0]).filter(s -> !s.isEmpty());
    }

    @FXML
    private void onJoinRoom() {
        RoomInfo selected = roomTable.getSelectionModel().getSelectedItem();
        if (selected != null) connection.send(new JoinRoomEvent(selected.roomId()));
    }

    @FXML
    private void onQuickMatch() { connection.send(new QuickMatchEvent()); }

    @FXML private void onMinimize() { sceneManager.getStage().setIconified(true); }
    @FXML private void onClose()    { connection.disconnect(); Platform.exit(); }

    // Обработка сообщений от сервера.
    @Override
    public void onMessage(ServerMessage message) {
        switch (message) {
            case LobbyStateMessage m -> {
                rooms.setAll(m.rooms());
                statusLabel.setText("Комнат: " + rooms.size());
            }
            case RoomJoinedMessage m -> {
                String ourNickname = findNickname(m.players(), m.localPlayerId());
                sceneManager.showGame(m.localPlayerId(), ourNickname, m.players());
            }
            case ErrorMessage m -> statusLabel.setText("Ошибка: " + m.text());
            default -> {}
        }
    }

    @Override
    public void onDisconnected() { statusLabel.setText("Соединение потеряно."); }

    private static String findNickname(List<PlayerInfo> players, String playerId) {
        return players.stream()
                .filter(p -> p.playerId().equals(playerId))
                .map(PlayerInfo::nickname)
                .findFirst()
                .orElse("");
    }

    private static String phaseLabel(RoomInfo info) {
        return switch (info.phase()) {
            case WAITING         -> "Ожидание";
            case PLAYING         -> "Идёт игра";
            case PAUSE_REQUESTED -> "Запрос паузы";
            case PAUSED          -> "Пауза";
            case FINISHED        -> "Завершена";
        };
    }
}

