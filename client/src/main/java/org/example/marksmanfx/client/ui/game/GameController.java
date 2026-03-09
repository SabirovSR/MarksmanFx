package org.example.marksmanfx.client.ui.game;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.example.marksmanfx.client.network.MessageListener;
import org.example.marksmanfx.client.network.ServerConnection;
import org.example.marksmanfx.client.ui.SceneManager;
import org.example.marksmanfx.client.ui.WindowDragUtil;
import org.example.marksmanfx.common.event.AimEvent;
import org.example.marksmanfx.common.event.CrouchEvent;
import org.example.marksmanfx.common.event.FireArrowEvent;
import org.example.marksmanfx.common.event.LeaveRoomEvent;
import org.example.marksmanfx.common.event.MoveEvent;
import org.example.marksmanfx.common.event.PauseRequestEvent;
import org.example.marksmanfx.common.event.PlayerReadyEvent;
import org.example.marksmanfx.common.event.RematchRequestEvent;
import org.example.marksmanfx.common.message.ErrorMessage;
import org.example.marksmanfx.common.message.GameOverMessage;
import org.example.marksmanfx.common.message.GameStartMessage;
import org.example.marksmanfx.common.message.GameStateMessage;
import org.example.marksmanfx.common.message.LobbyStateMessage;
import org.example.marksmanfx.common.message.PauseStateMessage;
import org.example.marksmanfx.common.message.PlayerDisconnectedMessage;
import org.example.marksmanfx.common.message.RematchOfferMessage;
import org.example.marksmanfx.common.message.RoomJoinedMessage;
import org.example.marksmanfx.common.message.RoomUpdatedMessage;
import org.example.marksmanfx.common.message.ServerMessage;
import org.example.marksmanfx.common.message.TechnicalWinMessage;
import org.example.marksmanfx.common.model.GamePhase;
import org.example.marksmanfx.common.model.PlayerInfo;
import org.example.marksmanfx.common.model.PlayerStateDto;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * MVC-контроллер игрового экрана.
 *
 * Отвечает за:
 *   — приём снимков состояния мира от сервера и их отрисовку через {@link GameRenderer}
 *   — трансляцию нажатий клавиш / кнопок в ClientEvents для сервера
 *   — управление жизненным циклом AnimationTimer
 *   — поддержание трехфазной стейт-машины кнопки поузы
 *   — отображение начального списка игроков комнаты
 *   — техническую победу при бисконнекте соперника
 *   — систему реванша после окончания матча
 */
public final class GameController implements MessageListener {

    /** Длительность полного заряда в секундах (совпадает с CHARGE_PER_SECOND сервера). */
    private static final double CHARGE_DURATION_SECS = 1.0 / 0.70;

    // ─── FXML-поля ────────────────────────────────────────────────────────────

    @FXML private BorderPane rootPane;
    @FXML private HBox       titleBar;
    @FXML private Canvas     gameCanvas;
    @FXML private Button     readyButton;
    @FXML private Button     pauseButton;
    @FXML private Button     leaveButton;
    @FXML private Label      statusLabel;
    @FXML private VBox       scoreBoard;
    @FXML private HBox       waitingOverlay;
    @FXML private Label      waitingLabel;

    // ─── Зависимости ──────────────────────────────────────────────────────────

    private SceneManager     sceneManager;
    private ServerConnection connection;
    private String           localPlayerId;
    private String           localNickname;

    // ─── Состояние ────────────────────────────────────────────────────────────

    /** Последний авторитетный снимок мира, пришедший от сервера. */
    private final AtomicReference<GameStateMessage> latestState = new AtomicReference<>();

    /**
     * Фаза игры, которую мы отслеживаем из PauseStateMessage.
     * Не совпадает с GameStateMessage.phase во время PAUSE_REQUESTED,
     * потому что сессия сервера остаётся PLAYING до тех пор, пока все не подтвердят.
     */
    private volatile GamePhase clientPhase            = GamePhase.WAITING;
    private volatile String    pauseRequesterId       = null;
    private volatile String    pauseRequesterNickname = null;
    /** Флаг: мы проголосовали за продолжение в состоянии PAUSED. */
    private volatile boolean   resumeVoteActive       = false;

    /** Флаг завершения матча. */
    private volatile boolean   gameOver               = false;
    /** Имя победителя (null если техническая победа) */
    private volatile String    gameOverWinner         = null;
    /** Имя игрока, ушедшего во время матча (техническая победа). */
    private volatile String    technicalWinDisconnected = null;

    /** Флаг нашего голоса «Готов» в режиме ожидания. */
    private volatile boolean   readyVoteActive        = false;
    /** Флаг нашего голоса за реванш. */
    private volatile boolean   rematchVoted           = false;
    private volatile boolean   crouching              = false;

    /** Время начала заряда выстрела (наносекунды), -1 если не заряжаем. */
    private long chargeStartNanos = -1;

    private AnimationTimer     animationTimer;
    private final Set<KeyCode> heldKeys = EnumSet.noneOf(KeyCode.class);

    // ─── Инициализация ────────────────────────────────────────────────────────

    /**
     * Инициализируем контроллер после загрузки FXML.
     *
     * @param initialPlayers  Список игроков на момент входа в комнату — показываем
     *                        в оверлее ожидания немедленно.
     */
    public void init(SceneManager sceneManager,
                     ServerConnection connection,
                     String localPlayerId,
                     String localNickname,
                     List<PlayerInfo> initialPlayers) {
        this.sceneManager  = sceneManager;
        this.connection    = connection;
        this.localPlayerId = localPlayerId;
        this.localNickname = localNickname;

        configureCanvas();
        bindKeyboard();

        // Подключаем перетаскивание только к тайтл-бару, не к канвасу
        WindowDragUtil.enable(titleBar, sceneManager.getStage());

        // Сразу показываем, кто уже в комнате — без ожидания первого RoomUpdatedMessage
        if (initialPlayers != null && !initialPlayers.isEmpty()) {
            showWaitingPlayerList(initialPlayers);
        }

        startRenderLoop();
    }

    /** Задаём размеры канваса под мировые координаты игры. */
    private void configureCanvas() {
        gameCanvas.setWidth(GameRenderer.WORLD_WIDTH);
        gameCanvas.setHeight(GameRenderer.WORLD_HEIGHT);
    }

    /** Подключаем обработчики клавиш к сцене. */
    private void bindKeyboard() {
        Platform.runLater(() -> {
            if (rootPane.getScene() == null) return;
            rootPane.getScene().addEventFilter(KeyEvent.KEY_PRESSED,  this::onKeyPressed);
            rootPane.getScene().addEventFilter(KeyEvent.KEY_RELEASED, this::onKeyReleased);
            rootPane.requestFocus();
        });
    }

    /** Запускаем AnimationTimer — он рисует каждый кадр из последнего снимка. */
    private void startRenderLoop() {
        animationTimer = new AnimationTimer() {
            @Override public void handle(long now) { renderFrame(now); }
        };
        animationTimer.start();
    }

    // ─── Рендер ───────────────────────────────────────────────────────────────

    private void renderFrame(long now) {
        GraphicsContext gc = gameCanvas.getGraphicsContext2D();

        if (gameOver) {
            // Рисуем последний кадр под оверлеем победы
            GameStateMessage s = latestState.get();
            if (s != null) GameRenderer.render(gc, s, localPlayerId, 0);

            // Выбираем нужный вид оверлея — технический или обычный
            if (technicalWinDisconnected != null) {
                GameRenderer.renderTechnicalWin(gc, technicalWinDisconnected);
            } else {
                GameRenderer.renderGameOver(gc, gameOverWinner != null ? gameOverWinner : "?");
            }
            return;
        }

        GameStateMessage state = latestState.get();
        if (state == null) return;

        GameRenderer.render(gc, state, localPlayerId, computeChargeRatio(now));

        // Рисуем баннер запроса паузы на основе PauseStateMessage,
        // а не GameStateMessage.phase, который запаздывает на один шаг
        if (pauseRequesterNickname != null) {
            GameRenderer.renderPauseRequested(gc, pauseRequesterNickname);
        }
    }

    /** Вычисляем текущий процент заряда для отображения шкалы. */
    private double computeChargeRatio(long now) {
        if (chargeStartNanos < 0 || clientPhase != GamePhase.PLAYING) return 0;
        return Math.min(1.0, (now - chargeStartNanos) / 1_000_000_000.0 / CHARGE_DURATION_SECS);
    }

    // ─── MessageListener ──────────────────────────────────────────────────────

    @Override
    public void onMessage(ServerMessage message) {
        switch (message) {
            case GameStartMessage          m -> onGameStart();
            case GameStateMessage          m -> onGameState(m);
            case GameOverMessage           m -> onGameOver(m.winnerNickname());
            case TechnicalWinMessage       m -> onTechnicalWin(m);
            case RematchOfferMessage       m -> onRematchOffer(m);
            case PauseStateMessage         m -> onPauseState(m);
            case RoomUpdatedMessage        m -> onRoomUpdated(m);
            case PlayerDisconnectedMessage m -> statusLabel.setText("«" + m.nickname() + "» отключился");
            case RoomJoinedMessage         m -> {} // уже обработан в LobbyController
            case LobbyStateMessage         m -> {} // вернулись в лобби — игнорируем
            case ErrorMessage              m -> statusLabel.setText("Ошибка: " + m.text());
            default -> {}
        }
    }

    /** Матч начался — сбрасываем все флаги и скрываем оверлей ожидания. */
    private void onGameStart() {
        clientPhase            = GamePhase.PLAYING;
        gameOver               = false;
        gameOverWinner         = null;
        technicalWinDisconnected = null;
        readyVoteActive        = false;
        rematchVoted           = false;
        resumeVoteActive       = false;
        pauseRequesterId       = null;
        pauseRequesterNickname = null;
        chargeStartNanos       = -1;

        waitingOverlay.setVisible(false);
        waitingOverlay.setManaged(false);
        updateButtons();
        rootPane.requestFocus();
    }

    /** Сохраняем последний снимок мира и обновляем счётную панель. */
    private void onGameState(GameStateMessage msg) {
        latestState.set(msg);
        updateScoreBoard(msg.players());
    }

    /** Обычная победа: кто-то набрал нужное количество очков. */
    private void onGameOver(String winnerNickname) {
        clientPhase              = GamePhase.FINISHED;
        gameOver                 = true;
        gameOverWinner           = winnerNickname;
        technicalWinDisconnected = null;
        chargeStartNanos         = -1;
        updateButtons();
    }

    /**
     * Техническая победа — соперник покинул матч.
     * Показываем специальный оверлей с именем ушедшего игрока.
     */
    private void onTechnicalWin(TechnicalWinMessage msg) {
        clientPhase              = GamePhase.FINISHED;
        gameOver                 = true;
        gameOverWinner           = msg.winnerNickname();
        technicalWinDisconnected = msg.disconnectedNickname();
        chargeStartNanos         = -1;
        updateButtons();
    }

    /**
     * Обновляем индикатор реванша — кто уже проголосовал.
     * Отображаем в statusLabel, чтобы все видели прогресс голосования.
     */
    private void onRematchOffer(RematchOfferMessage msg) {
        if (msg.voterNicknames().isEmpty()) return;
        String voters = String.join(", ", msg.voterNicknames());
        statusLabel.setText("Реванш: " + voters
                + " (" + msg.voterNicknames().size() + "/" + msg.totalPlayers() + ")");
    }

    /**
     * Получаем PauseStateMessage и обновляем клиентскую фазу.
     * Это единственный источник правды для состояния паузы на клиенте.
     */
    private void onPauseState(PauseStateMessage msg) {
        clientPhase = msg.phase();

        switch (msg.phase()) {
            case PAUSE_REQUESTED -> {
                pauseRequesterId       = msg.requesterId();
                pauseRequesterNickname = msg.requesterNickname();
            }
            case PAUSED -> {
                pauseRequesterId       = msg.requesterId();
                pauseRequesterNickname = null; // баннер убираем, пауза уже подтверждена
            }
            case PLAYING -> {
                // Пауза отменена или игра возобновлена
                pauseRequesterId       = null;
                pauseRequesterNickname = null;
                resumeVoteActive       = false;
            }
            default -> {}
        }
        updateButtons();
    }

    /** Список игроков в комнате изменился — обновляем оверлей ожидания. */
    private void onRoomUpdated(RoomUpdatedMessage msg) {
        if (clientPhase == GamePhase.WAITING || clientPhase == GamePhase.FINISHED) {
            showWaitingPlayerList(msg.players());
        }
    }

    @Override
    public void onDisconnected() {
        statusLabel.setText("Соединение с сервером потеряно.");
        clientPhase = GamePhase.WAITING;
        if (animationTimer != null) animationTimer.stop();
    }

    // ─── Обработчики кнопок ───────────────────────────────────────────────────

    /**
     * Кнопка «Готов» / «Реванш» в зависимости от контекста.
     * В PAUSED — делегируем к логике паузы.
     */
    @FXML
    private void onReady() {
        switch (clientPhase) {
            case WAITING -> {
                // Переключаем готовность перед стартом игры
                readyVoteActive = !readyVoteActive;
                connection.send(new PlayerReadyEvent(readyVoteActive));
                updateButtons();
            }
            case PAUSED -> {
                // Кнопка «Готов продолжить» дублирует логику кнопки паузы
                handlePauseInput();
            }
            case FINISHED -> {
                // Предлагаем реванш — блокируем кнопку после нажатия
                if (!rematchVoted) {
                    rematchVoted = true;
                    connection.send(new RematchRequestEvent());
                    readyButton.setDisable(true);
                    readyButton.setText("Ожидаем остальных…");
                }
            }
            default -> {}
        }
    }

    /**
     * Контекстная кнопка паузы.
     * Поведение зависит от текущей фазы игры.
     */
    @FXML
    private void onPause() { handlePauseInput(); }

    /** Выходим из комнаты и возвращаемся в лобби. */
    @FXML
    private void onLeave() {
        if (animationTimer != null) animationTimer.stop();
        connection.send(new LeaveRoomEvent());
        sceneManager.showLobby(localPlayerId, localNickname);
    }

    // Управление окном
    @FXML private void onMinimize() { sceneManager.getStage().setIconified(true); }
    @FXML private void onClose()    { connection.disconnect(); Platform.exit(); }

    // ─── Клавиатурный ввод ────────────────────────────────────────────────────

    private void onKeyPressed(KeyEvent e) {
        KeyCode code = e.getCode();
        // Игнорируем повторные события при удержании клавиши
        if (!heldKeys.add(code)) { e.consume(); return; }

        switch (code) {
            case W, UP    -> sendMove("UP",    true);
            case S, DOWN  -> sendMove("DOWN",  true);
            case A        -> sendMove("LEFT",  true);
            case D        -> sendMove("RIGHT", true);
            case Q, LEFT  -> sendAim("UP",    true);
            case E, RIGHT -> sendAim("DOWN",  true);
            case C        -> toggleCrouch();
            case SPACE    -> startCharge();
            case P        -> handlePauseInput();
            case R        -> onReady();
            default       -> { return; }
        }
        e.consume();
    }

    private void onKeyReleased(KeyEvent e) {
        KeyCode code = e.getCode();
        heldKeys.remove(code);

        switch (code) {
            case W, UP    -> sendMove("UP",    false);
            case S, DOWN  -> sendMove("DOWN",  false);
            case A        -> sendMove("LEFT",  false);
            case D        -> sendMove("RIGHT", false);
            case Q, LEFT  -> sendAim("UP",    false);
            case E, RIGHT -> sendAim("DOWN",  false);
            case SPACE    -> releaseCharge();
            default       -> { return; }
        }
        e.consume();
    }

    // ─── Логика паузы ─────────────────────────────────────────────────

    /**
     * Обрабатываем нажатие P или кнопки «Пауза» с учётом текущей фазы.
     *
     * PLAYING         → запрашиваем паузу
     * PAUSE_REQUESTED → подтверждаем (или отменяем, если мы инициатор)
     * PAUSED          → переключаем голос за продолжение
     */
    private void handlePauseInput() {
        switch (clientPhase) {
            case PLAYING -> connection.send(new PauseRequestEvent(true));
            case PAUSE_REQUESTED -> {
                boolean amRequester = localPlayerId.equals(pauseRequesterId);
                // Инициатор может отозвать запрос, остальные — подтверждают
                connection.send(new PauseRequestEvent(!amRequester));
            }
            case PAUSED -> {
                // Переключаем наш голос за возобновление
                resumeVoteActive = !resumeVoteActive;
                connection.send(new PlayerReadyEvent(resumeVoteActive));
                updateButtons();
            }
            default -> {}
        }
    }

    // ─── Вспомогательные методы ввода ─────────────────────────────────────────

    private void sendMove(String dir, boolean pressed) {
        if (clientPhase != GamePhase.PLAYING) return;
        connection.send(new MoveEvent(dir, pressed));
    }

    private void sendAim(String dir, boolean pressed) {
        if (clientPhase != GamePhase.PLAYING) return;
        connection.send(new AimEvent(dir, pressed));
    }

    private void toggleCrouch() {
        if (clientPhase != GamePhase.PLAYING) return;
        crouching = !crouching;
        connection.send(new CrouchEvent(crouching));
    }

    private void startCharge() {
        if (clientPhase != GamePhase.PLAYING) return;
        chargeStartNanos = System.nanoTime();
    }

    private void releaseCharge() {
        if (chargeStartNanos < 0 || clientPhase != GamePhase.PLAYING) return;
        double elapsed = (System.nanoTime() - chargeStartNanos) / 1_000_000_000.0;
        chargeStartNanos = -1;
        // Отправляем команду выстрела,
        // но сервер всё равно проигнорирует её, если стрела ещё летит
        connection.send(new FireArrowEvent(Math.min(1.0, elapsed / CHARGE_DURATION_SECS)));
    }

    // ─── Обновление UI ────────────────────────────────────────────────────────

    /**
     * Синхронизируем состояние всех кнопок с текущей фазой.
     * Вызываем из любого обработчика, меняющего фазу.
     */
    private void updateButtons() {
        switch (clientPhase) {

            case WAITING -> {
                // Показываем оверлей ожидания, кнопка «Готов» доступна
                waitingOverlay.setVisible(true);
                waitingOverlay.setManaged(true);
                readyButton.setDisable(false);
                readyButton.setText(readyVoteActive ? "Не готов [R]" : "Готов [R]");
                readyButton.getStyleClass().removeAll("btn-danger");
                readyButton.getStyleClass().add("btn-accent");
                pauseButton.setDisable(true);
                pauseButton.setText("Пауза [P]");
                resetPauseButtonStyle();
                leaveButton.setText("Выйти из комнаты");
            }

            case PLAYING -> {
                waitingOverlay.setVisible(false);
                waitingOverlay.setManaged(false);
                readyButton.setDisable(true);
                readyButton.setText("Готов [R]");
                pauseButton.setDisable(false);
                pauseButton.setText("Пауза [P]");
                resetPauseButtonStyle();
                leaveButton.setText("Выйти из комнаты");
            }

            case PAUSE_REQUESTED -> {
                boolean amRequester = localPlayerId.equals(pauseRequesterId);
                pauseButton.setDisable(false);
                if (amRequester) {
                    // Мы запросили паузу — даём возможность отменить
                    pauseButton.setText("Отменить запрос [P]");
                    setPauseButtonStyle("btn-danger");
                } else {
                    // Кто-то другой запросил — предлагаем подтвердить
                    pauseButton.setText("Подтвердить паузу [P]");
                    setPauseButtonStyle("btn-accent");
                }
            }

            case PAUSED -> {
                pauseButton.setDisable(false);
                readyButton.setDisable(false);
                if (resumeVoteActive) {
                    pauseButton.setText("Отменить ▷ [P]");
                    readyButton.setText("Отменить ▷ [R]");
                    setPauseButtonStyle("btn-danger");
                } else {
                    pauseButton.setText("Готов продолжить [P]");
                    readyButton.setText("Готов продолжить [R]");
                    setPauseButtonStyle("btn-accent");
                }
            }

            case FINISHED -> {
                // Два варианта действий после матча
                pauseButton.setDisable(true);
                pauseButton.setText("Пауза [P]");
                resetPauseButtonStyle();
                readyButton.setDisable(rematchVoted);
                readyButton.setText(rematchVoted ? "Ожидаем остальных…" : "🔄 Реванш [R]");
                readyButton.getStyleClass().removeAll("btn-danger");
                readyButton.getStyleClass().add("btn-accent");
                leaveButton.setText("Выйти в лобби");
            }
        }

        statusLabel.setText(switch (clientPhase) {
            case WAITING         -> "Ожидание игроков…";
            case PLAYING         -> "Игра идёт";
            case PAUSE_REQUESTED -> "Запрошена пауза…";
            case PAUSED          -> "Пауза";
            case FINISHED        -> gameOverWinner != null
                    ? "Победитель: " + gameOverWinner
                    : "Игра окончена";
        });
    }

    /** Сбрасываем классы стиля кнопки паузы в состояние по умолчанию (btn-primary). */
    private void resetPauseButtonStyle() {
        pauseButton.getStyleClass().removeAll("btn-accent", "btn-danger");
        if (!pauseButton.getStyleClass().contains("btn-primary")) {
            pauseButton.getStyleClass().add("btn-primary");
        }
    }

    /** Переключаем стиль кнопки паузы под нужный контекст. */
    private void setPauseButtonStyle(String newClass) {
        pauseButton.getStyleClass().removeAll("btn-primary", "btn-accent", "btn-danger");
        pauseButton.getStyleClass().add(newClass);
    }

    /** Строим список игроков в комнате для оверлея ожидания. */
    private void showWaitingPlayerList(List<PlayerInfo> players) {
        StringBuilder sb = new StringBuilder();
        for (PlayerInfo p : players) {
            sb.append(p.playerId().equals(localPlayerId) ? "▶ " : "  ");
            sb.append(p.nickname());
            if (p.ready()) sb.append(" ✓");
            sb.append("\n");
        }
        waitingLabel.setText(sb.toString().strip());
    }

    /** Перестраиваем панель счёта из последнего снимка игроков. */
    private void updateScoreBoard(List<PlayerStateDto> players) {
        scoreBoard.getChildren().clear();
        for (PlayerStateDto p : players) {
            boolean isMe = p.playerId().equals(localPlayerId);
            Label lbl = new Label((isMe ? "▶ " : "  ") + p.nickname() + ":  " + p.score());
            lbl.getStyleClass().add(isMe ? "score-self" : "score-other");
            scoreBoard.getChildren().add(lbl);
        }
    }
}
