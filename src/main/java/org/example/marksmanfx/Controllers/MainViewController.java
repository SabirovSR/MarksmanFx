package org.example.marksmanfx.Controllers;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.ArcType;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import org.example.marksmanfx.Engine.GameEngine;
import org.example.marksmanfx.Models.ArrowSnapshot;
import org.example.marksmanfx.Models.GameModel;
import org.example.marksmanfx.Models.GameSnapshot;
import org.example.marksmanfx.Models.TargetSnapshot;
import org.example.marksmanfx.Models.TargetType;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/// Главный класс интерфейса.
/// Связывает кнопки, клавиатуру, холст `Canvas`, подписи со статистикой и игровой движок.
/// 1. Подписка на события JavaFX.
/// 2. Хранение состояния удерживаемых клавиш/кнопок.
/// 3. Отрисовка кадра на `Canvas`.
public class MainViewController {
    private static final double WORLD_WIDTH = 960;
    private static final double WORLD_HEIGHT = 560;
    private static final double FIELD_PADDING = 10;
    private static final double CHARGE_PER_SECOND = 0.70;
    private static final double MOVE_SPEED_PER_SECOND = 237.5;
    private static final double AIM_SPEED_PER_SECOND = 84.375;

    /// Текущее состояние игры
    private final GameModel gameModel = new GameModel();
    /// Объект, который обновляет модель
    private final GameEngine gameEngine = new GameEngine(gameModel);


    private AnimationTimer animationTimer;
    private boolean keyboardBound;
    private Stage stage;
    private double dragOffsetX;
    private double dragOffsetY;
    private long lastFrameNanos = -1L;

    private boolean moveUpHeld;
    private boolean moveDownHeld;
    private boolean moveLeftHeld;
    private boolean moveRightHeld;
    private boolean aimUpHeld;
    private boolean aimDownHeld;
    private boolean shootHeld;
    private double shotCharge;

    private final Set<KeyCode> pressedKeys = EnumSet.noneOf(KeyCode.class);

    @FXML
    private BorderPane rootPane;

    @FXML
    private StackPane gameFieldContainer;

    @FXML
    private Canvas gameCanvas;

    @FXML
    private Label scoreLabel;

    @FXML
    private Label shotsLabel;

    @FXML
    private Label levelLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private Label angleLabel;

    @FXML
    private Label postureLabel;

    @FXML
    private Button pauseButton;

    @FXML
    private Button shootButton;

    @FXML
    private Button moveUpButton;

    @FXML
    private Button moveDownButton;

    @FXML
    private Button moveLeftButton;

    @FXML
    private Button moveRightButton;

    @FXML
    private Button aimUpButton;

    @FXML
    private Button aimDownButton;

    public void attachStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    private void initialize() {
        // Настраиваем размеры области с холстом
        configureCanvasHost();

        // Привязываем кнопки к логике удержания
        configureHoldButtons();

        // Запускаем поток анимации
        createAnimationLoop();

        // Сразу рисуем стартовый кадр
        render(gameEngine.snapshot());

        // Подключаем клавиатуру
        bindKeyboard();
    }

    /// Запускаем новую игру
    @FXML
    private void onStartGame() {
        shotCharge = 0;
        lastFrameNanos = -1L;
        gameEngine.startNewGame();
        render(gameEngine.snapshot());
        requestGameFocus();
    }

    /// Останавливаем игру
    @FXML
    private void onStopGame() {
        shootHeld = false;
        shotCharge = 0;
        gameEngine.stopGame();
        render(gameEngine.snapshot());
        requestGameFocus();
    }

    /// Переключаем паузу
    @FXML
    private void onPauseResume() {
        gameEngine.togglePause();
        lastFrameNanos = -1L;
        render(gameEngine.snapshot());
        requestGameFocus();
    }

    /// Удерживаем - копим силу, когда отпускаем - стреляем
    @FXML
    private void onShoot() {
        releaseChargingShot();
        requestGameFocus();
    }

    /// Меняем угол прицеливания на фиксированный шаг
    @FXML
    private void onAimUp() {
        gameEngine.aimUp();
        render(gameEngine.snapshot());
        requestGameFocus();
    }

    @FXML
    private void onAimDown() {
        gameEngine.aimDown();
        render(gameEngine.snapshot());
        requestGameFocus();
    }

    /// Сдвигаем лучника по полю на фиксированную величину
    @FXML
    private void onMoveUp() {
        gameEngine.moveArcherUp();
        render(gameEngine.snapshot());
        requestGameFocus();
    }

    @FXML
    private void onMoveDown() {
        gameEngine.moveArcherDown();
        render(gameEngine.snapshot());
        requestGameFocus();
    }

    @FXML
    private void onMoveLeft() {
        gameEngine.moveArcherLeft();
        render(gameEngine.snapshot());
        requestGameFocus();
    }

    @FXML
    private void onMoveRight() {
        gameEngine.moveArcherRight();
        render(gameEngine.snapshot());
        requestGameFocus();
    }

    /// Переключаем стойку лучника: обычная или присед
    @FXML
    private void onToggleCrouch() {
        gameEngine.toggleCrouch();
        render(gameEngine.snapshot());
        requestGameFocus();
    }

    /// Останавливаем поток ввода и просим GameEngine завершить фоновые циклы
    public void shutdown() {
        if (animationTimer != null) {
            animationTimer.stop();
        }
    }

    /// Запоминаем смещение курсора относительно окна в момент начала перетаскивания.
    /// Все из-за кастомного StageStyle, потому что указали в App StageStyle.UNDECORATED
    @FXML
    private void onTopBarPressed(MouseEvent event) {
        if (stage == null) {
            return;
        }
        dragOffsetX = event.getSceneX();
        dragOffsetY = event.getSceneY();
    }

    /// Двигаем окно вслед за мышью
    @FXML
    private void onTopBarDragged(MouseEvent event) {
        if (stage == null) {
            return;
        }
        stage.setX(event.getScreenX() - dragOffsetX);
        stage.setY(event.getScreenY() - dragOffsetY);
    }

    /// Закрываем окно
    @FXML
    private void onCloseWindow() {
        if (stage != null) {
            stage.close();
        } else {
            Platform.exit();
        }
    }

    /// Подключаем обработчики клавиатуры к сцене
    private void bindKeyboard() {
        rootPane.setFocusTraversable(true);
        Platform.runLater(() -> {
            if (keyboardBound) {
                return;
            }

            Scene scene = rootPane.getScene();
            if (scene == null) {
                return;
            }

            scene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyPressed);
            scene.addEventFilter(KeyEvent.KEY_RELEASED, this::handleKeyReleased);
            keyboardBound = true;
            rootPane.requestFocus();
        });
    }

    /// Обрабатываем нажатия клавиш
    private void handleKeyPressed(KeyEvent event) {
        KeyCode code = event.getCode();
        if (!pressedKeys.add(code)) {
            event.consume();
            return;
        }

        switch (code) {
            case W, UP -> moveUpHeld = true;
            case S, DOWN -> moveDownHeld = true;
            case A -> moveLeftHeld = true;
            case D -> moveRightHeld = true;
            case RIGHT, E -> aimDownHeld = true;
            case LEFT, Q -> aimUpHeld = true;
            case ENTER, R -> onStartGame();
            case T -> onStopGame();
            case P -> onPauseResume();
            case SPACE -> startChargingShot();
            case C -> onToggleCrouch();
            default -> {
                return;
            }
        }

        event.consume();
    }

    /// Снимаем флаг удержания
    private void handleKeyReleased(KeyEvent event) {
        KeyCode code = event.getCode();
        pressedKeys.remove(code);

        switch (code) {
            case W, UP -> moveUpHeld = false;
            case S, DOWN -> moveDownHeld = false;
            case A -> moveLeftHeld = false;
            case D -> moveRightHeld = false;
            case RIGHT, E -> aimDownHeld = false;
            case LEFT, Q -> aimUpHeld = false;
            case SPACE -> releaseChargingShot();
            default -> {
                return;
            }
        }
        event.consume();
    }

    /// Задаем размеры контейнера и холста
    private void configureCanvasHost() {
        double hostWidth = WORLD_WIDTH + FIELD_PADDING * 2;
        double hostHeight = WORLD_HEIGHT + FIELD_PADDING * 2;

        gameFieldContainer.setMinSize(hostWidth, hostHeight);
        gameFieldContainer.setPrefSize(hostWidth, hostHeight);
        gameFieldContainer.setMaxSize(hostWidth, hostHeight);

        gameCanvas.setWidth(WORLD_WIDTH);
        gameCanvas.setHeight(WORLD_HEIGHT);
    }

    /// Подключаем кнопки интерфейса к механизму удержани
    private void configureHoldButtons() {
        bindHold(moveUpButton, () -> moveUpHeld = true, () -> moveUpHeld = false);
        bindHold(moveDownButton, () -> moveDownHeld = true, () -> moveDownHeld = false);
        bindHold(moveLeftButton, () -> moveLeftHeld = true, () -> moveLeftHeld = false);
        bindHold(moveRightButton, () -> moveRightHeld = true, () -> moveRightHeld = false);
        bindHold(aimUpButton, () -> aimUpHeld = true, () -> aimUpHeld = false);
        bindHold(aimDownButton, () -> aimDownHeld = true, () -> aimDownHeld = false);
        bindHold(shootButton, this::startChargingShot, this::releaseChargingShot);
    }

    /// Общий метод для всех кнопок удержания
    private void bindHold(Button button, Runnable onPress, Runnable onRelease) {
        button.setOnMousePressed(event -> {
            onPress.run();
            requestGameFocus();
        });
        button.setOnMouseReleased(event -> onRelease.run());
        button.setOnMouseExited(event -> onRelease.run());
    }

    /// Запускаем animationTimer
    private void createAnimationLoop() {
        animationTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastFrameNanos < 0) {
                    lastFrameNanos = now;
                    render(gameEngine.snapshot());
                    return;
                }

                double deltaSeconds = (now - lastFrameNanos) / 1_000_000_000.0;
                lastFrameNanos = now;

                processContinuousInput(deltaSeconds);
                gameEngine.update(deltaSeconds);
                render(gameEngine.snapshot());
            }
        };

        animationTimer.start();
    }

    private void processContinuousInput(double deltaSeconds) {
        double deltaX = (moveRightHeld ? 1 : 0) - (moveLeftHeld ? 1 : 0);
        double deltaY = (moveDownHeld ? 1 : 0) - (moveUpHeld ? 1 : 0);
        double deltaAim = (aimUpHeld ? 1 : 0) - (aimDownHeld ? 1 : 0);

        if (deltaX != 0 || deltaY != 0) {
            gameEngine.moveArcher(deltaX * MOVE_SPEED_PER_SECOND * deltaSeconds,
                    deltaY * MOVE_SPEED_PER_SECOND * deltaSeconds);
        }
        if (deltaAim != 0) {
            gameEngine.aim(deltaAim * AIM_SPEED_PER_SECOND * deltaSeconds);
        }

        if (!shootHeld) {
            return;
        }

        GameSnapshot snapshot = gameEngine.snapshot();
        if (snapshot.running() && !snapshot.paused() && !snapshot.arrow().active()) {
            shotCharge = Math.min(1.0, shotCharge + CHARGE_PER_SECOND * deltaSeconds);
        }
    }

    private void startChargingShot() {
        shootHeld = true;
    }

    /// Заканчиваем заряд, сбрасываем накопленную силу и передаем ее в gameEngine.fireArrow(charge)
    private void releaseChargingShot() {
        boolean wasHeld = shootHeld;
        shootHeld = false;

        if (!wasHeld) {
            return;
        }

        double charge = shotCharge;
        shotCharge = 0;
        gameEngine.fireArrow(charge);
        render(gameEngine.snapshot());
    }

    /// Возвращаем фокус корневой панели. Это нужно, чтобы после клика по кнопке клавиатура снова управляла игрой
    private void requestGameFocus() {
        if (rootPane != null) {
            rootPane.requestFocus();
        }
    }

    /// Главный метод отрисовки кадра
    private void render(GameSnapshot snapshot) {
        GraphicsContext gc = gameCanvas.getGraphicsContext2D();

        gc.clearRect(0, 0, WORLD_WIDTH, WORLD_HEIGHT);
        gc.save();
        applyRoundedClip(gc, 0, 0, WORLD_WIDTH, WORLD_HEIGHT, 18);

        drawBackground(gc, WORLD_WIDTH, WORLD_HEIGHT);
        drawGuides(gc, WORLD_HEIGHT, snapshot.nearTarget().x(), snapshot.farTarget().x());
        drawArcher(gc, snapshot.archerX(), snapshot.archerY(), snapshot.aimAngleDegrees(), snapshot.crouched());
        drawTarget(gc, snapshot.nearTarget());
        drawTarget(gc, snapshot.farTarget());
        drawArrow(gc, snapshot.arrow());

        if (snapshot.running() && snapshot.paused()) {
            drawPauseOverlay(gc);
        }

        gc.restore();

        scoreLabel.setText(String.valueOf(snapshot.score()));
        shotsLabel.setText(String.valueOf(snapshot.shots()));
        levelLabel.setText(String.valueOf(snapshot.level()));
        angleLabel.setText(String.format(Locale.US, "%.0f°", snapshot.aimAngleDegrees()));
        postureLabel.setText(snapshot.crouched() ? "Присед" : "Стойка");

        String status = !snapshot.running()
                ? "Остановлена"
                : snapshot.paused() ? "Пауза" : "В игре";

        statusLabel.setText(status);
        pauseButton.setText(snapshot.paused() ? "Продолжить [P]" : "Пауза [P]");
        pauseButton.setDisable(!snapshot.running());
        shootButton.setDisable(!snapshot.running() || snapshot.paused() || snapshot.arrow().active());
        updateShootButtonText(snapshot);
    }

    /// Обновляем надпись на кнопке выстрела. Если идет зарядка, показываем шкалу и процент
    private void updateShootButtonText(GameSnapshot snapshot) {
        int bars = (int) Math.round(shotCharge * 10);
        String bar = "█".repeat(Math.max(0, bars)) + "░".repeat(Math.max(0, 10 - bars));

        if (snapshot.running() && !snapshot.paused() && !snapshot.arrow().active() && shotCharge > 0.001) {
            int percent = (int) Math.round(shotCharge * 100);
            shootButton.setText("Выстрел [Space] " + bar + " " + percent + "%");
        } else {
            shootButton.setText("Выстрел [Space]");
        }
    }

    /// Создаем скругленную область отсечения, чтобы поле выглядело аккуратнее
    private static void applyRoundedClip(GraphicsContext gc, double x, double y, double width, double height, double radius) {
        double r = Math.min(radius, Math.min(width, height) * 0.5);

        gc.beginPath();
        gc.moveTo(x + r, y);
        gc.lineTo(x + width - r, y);
        gc.quadraticCurveTo(x + width, y, x + width, y + r);
        gc.lineTo(x + width, y + height - r);
        gc.quadraticCurveTo(x + width, y + height, x + width - r, y + height);
        gc.lineTo(x + r, y + height);
        gc.quadraticCurveTo(x, y + height, x, y + height - r);
        gc.lineTo(x, y + r);
        gc.quadraticCurveTo(x, y, x + r, y);
        gc.closePath();
        gc.clip();
    }

    /// Рисуем фон, световые пятна и затемнение нижней части поля
    private static void drawBackground(GraphicsContext gc, double width, double height) {
        gc.setFill(new LinearGradient(
                0,
                0,
                0,
                1,
                true,
                null,
                new Stop(0, Color.web("#091224")),
                new Stop(0.38, Color.web("#10233f")),
                new Stop(0.72, Color.web("#164e63")),
                new Stop(1, Color.web("#14532d"))
        ));
        gc.fillRect(0, 0, width, height);

        gc.setFill(Color.rgb(255, 255, 255, 0.08));
        gc.fillOval(-170, -200, 520, 520);
        gc.fillOval(width - 310, -120, 430, 430);

        gc.setFill(Color.rgb(8, 24, 30, 0.35));
        gc.fillRect(0, height * 0.76, width, height * 0.24);
    }

    /// Рисуем полупрозрачную плашку паузы поверх поля
    private static void drawPauseOverlay(GraphicsContext gc) {
        gc.setFill(Color.rgb(4, 10, 22, 0.52));
        gc.fillRect(0, 0, WORLD_WIDTH, WORLD_HEIGHT);

        gc.setFill(Color.web("#e2e8f0"));
        gc.fillRoundRect(WORLD_WIDTH * 0.5 - 170, WORLD_HEIGHT * 0.5 - 48, 340, 96, 20, 20);

        gc.setFill(Color.web("#0f172a"));
        gc.setFont(Font.font("System", FontWeight.BOLD, 34));
        gc.fillText("ПАУЗА", WORLD_WIDTH * 0.5 - 60, WORLD_HEIGHT * 0.5 + 12);
    }

    /// Рисуем вертикальные направляющие по осям движения мишеней
    private static void drawGuides(GraphicsContext gc, double height, double nearX, double farX) {
        gc.setStroke(Color.rgb(188, 205, 255, 0.42));
        gc.setLineWidth(3);
        gc.strokeLine(nearX + 55, 24, nearX + 55, height - 24);

        gc.setStroke(Color.rgb(255, 237, 171, 0.55));
        gc.setLineWidth(2);
        gc.strokeLine(farX + 27.5, 24, farX + 27.5, height - 24);
    }

    /// Рисуем лучника
    private static void drawArcher(GraphicsContext gc, double archerX, double archerY, double aimAngleDegrees, boolean crouched) {
        double bodyX = archerX;
        double crouchDrop = crouched ? 58 : 0;
        double shoulderY = archerY - 38 + crouchDrop;
        double hipY = archerY + (crouched ? 42 : 6);
        double headY = shoulderY - (crouched ? 20 : 28);
        double bodyHeight = crouched ? 42 : 58;

        gc.setFill(Color.rgb(0, 0, 0, 0.30));
        gc.fillOval(bodyX - 46, hipY + 52, 190, 20);

        gc.setFill(Color.web("#0f172a"));
        gc.fillRoundRect(bodyX - 8, shoulderY - 6, 54, bodyHeight, 20, 20);
        gc.setFill(Color.web("#be123c"));
        gc.fillRoundRect(bodyX - 4, shoulderY - 2, 46, bodyHeight - 12, 16, 16);
        gc.setFill(Color.web("#f59e0b"));
        gc.fillRoundRect(bodyX + 12, shoulderY + 6, 8, bodyHeight - 20, 8, 8);

        gc.setFill(Color.web("#f7dfc7"));
        gc.fillOval(bodyX + 2, headY, 34, 34);
        gc.setFill(Color.web("#0f172a"));
        gc.fillArc(bodyX, headY - 4, 38, 20, 188, 166, ArcType.ROUND);
        gc.fillOval(bodyX + 20, headY + 13, 3.8, 3.8);

        gc.setStroke(Color.web("#f7dfc7"));
        gc.setLineWidth(8);
        gc.strokeLine(bodyX + 16, shoulderY + 10, bodyX + 44, shoulderY + 5);

        double aimRad = Math.toRadians(aimAngleDegrees);
        double dirX = Math.cos(aimRad);
        double dirY = -Math.sin(aimRad);
        double perpX = -dirY;
        double perpY = dirX;

        double handX = bodyX + 58;
        double handY = shoulderY + 4;
        double elbowX = handX - dirX * 14;
        double elbowY = handY - dirY * 14;
        gc.strokeLine(bodyX + 14, shoulderY + 14, elbowX, elbowY);

        if (crouched) {
            gc.setStroke(Color.web("#f8fafc"));
            gc.setLineWidth(7);
            gc.strokeLine(bodyX + 14, hipY + 12, bodyX - 34, hipY + 34);
            gc.strokeLine(bodyX + 14, hipY + 12, bodyX + 108, hipY + 32);
        } else {
            gc.setStroke(Color.web("#f8fafc"));
            gc.setLineWidth(7);
            gc.strokeLine(bodyX + 14, hipY + 24, bodyX - 4, hipY + 60);
            gc.strokeLine(bodyX + 14, hipY + 24, bodyX + 30, hipY + 60);
        }

        double bowTopX = handX + perpX * 42 + dirX * 8;
        double bowTopY = handY + perpY * 42 + dirY * 8;
        double bowMidX = handX + dirX * 24;
        double bowMidY = handY + dirY * 24;
        double bowBottomX = handX - perpX * 42 + dirX * 8;
        double bowBottomY = handY - perpY * 42 + dirY * 8;

        gc.setStroke(Color.web("#f59e0b"));
        gc.setLineWidth(4);
        gc.strokeLine(bowTopX, bowTopY, bowMidX, bowMidY);
        gc.strokeLine(bowMidX, bowMidY, bowBottomX, bowBottomY);

        gc.setStroke(Color.web("#fef3c7"));
        gc.setLineWidth(2);
        gc.strokeLine(bowTopX, bowTopY, bowBottomX, bowBottomY);
    }

    /// Рисуем ближнюю и дальнюю мишень разными цветами
    private static void drawTarget(GraphicsContext gc, TargetSnapshot target) {
        double x = target.x();
        double y = target.y();
        double size = target.size();

        if (target.type() == TargetType.NEAR) {
            gc.setFill(Color.web("#ffffff"));
            gc.fillOval(x, y, size, size);
            gc.setFill(Color.web("#ef4444"));
            gc.fillOval(x + size * 0.14, y + size * 0.14, size * 0.72, size * 0.72);
            gc.setFill(Color.web("#ffffff"));
            gc.fillOval(x + size * 0.28, y + size * 0.28, size * 0.44, size * 0.44);
            gc.setFill(Color.web("#ef4444"));
            gc.fillOval(x + size * 0.4, y + size * 0.4, size * 0.2, size * 0.2);
        } else {
            gc.setFill(Color.web("#fde68a"));
            gc.fillOval(x, y, size, size);
            gc.setFill(Color.web("#f97316"));
            gc.fillOval(x + size * 0.17, y + size * 0.17, size * 0.66, size * 0.66);
            gc.setFill(Color.web("#111827"));
            gc.fillOval(x + size * 0.38, y + size * 0.38, size * 0.24, size * 0.24);
        }
    }

    /// Рисуем стрелу с учетом текущего угла
    private static void drawArrow(GraphicsContext gc, ArrowSnapshot arrow) {
        if (!arrow.active()) {
            return;
        }

        double angleRad = Math.toRadians(arrow.angleDegrees());
        double dirX = Math.cos(angleRad);
        double dirY = -Math.sin(angleRad);
        double perpX = -dirY;
        double perpY = dirX;

        double tailX = arrow.x();
        double tailY = arrow.y();
        double tipX = tailX + dirX * arrow.width();
        double tipY = tailY + dirY * arrow.width();

        double headLength = 11;
        double headHalfWidth = 5;
        double shaftEndX = tipX - dirX * headLength;
        double shaftEndY = tipY - dirY * headLength;

        gc.setStroke(Color.web("#f8fafc"));
        gc.setLineWidth(3);
        gc.strokeLine(tailX, tailY, shaftEndX, shaftEndY);

        gc.setFill(Color.web("#f59e0b"));
        gc.fillPolygon(
                new double[]{
                        tipX,
                        shaftEndX + perpX * headHalfWidth,
                        shaftEndX - perpX * headHalfWidth
                },
                new double[]{
                        tipY,
                        shaftEndY + perpY * headHalfWidth,
                        shaftEndY - perpY * headHalfWidth
                },
                3
        );

        gc.setStroke(Color.web("#cbd5e1"));
        gc.setLineWidth(2);
        gc.strokeLine(
                tailX,
                tailY,
                tailX - dirX * 8 + perpX * (arrow.height() * 0.5),
                tailY - dirY * 8 + perpY * (arrow.height() * 0.5)
        );
        gc.strokeLine(
                tailX,
                tailY,
                tailX - dirX * 8 - perpX * (arrow.height() * 0.5),
                tailY - dirY * 8 - perpY * (arrow.height() * 0.5)
        );
    }
}
