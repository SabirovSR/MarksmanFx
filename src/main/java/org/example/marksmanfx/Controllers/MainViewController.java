package org.example.marksmanfx.Controllers;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.ArcType;
import org.example.marksmanfx.Engine.GameEngine;
import org.example.marksmanfx.Models.ArrowSnapshot;
import org.example.marksmanfx.Models.GameModel;
import org.example.marksmanfx.Models.GameSnapshot;
import org.example.marksmanfx.Models.TargetSnapshot;
import org.example.marksmanfx.Models.TargetType;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public class MainViewController {
    private static final double WORLD_WIDTH = 960;
    private static final double WORLD_HEIGHT = 560;
    private static final double FIELD_PADDING = 10;

    private final GameModel gameModel = new GameModel();
    private final GameEngine gameEngine = new GameEngine(gameModel, this::render);

    private volatile boolean inputLoopActive;
    private Thread inputLoopThread;
    private boolean keyboardBound;

    private volatile boolean moveUpHeld;
    private volatile boolean moveDownHeld;
    private volatile boolean moveLeftHeld;
    private volatile boolean moveRightHeld;
    private volatile boolean aimUpHeld;
    private volatile boolean aimDownHeld;
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

    @FXML
    private void initialize() {
        configureCanvasHost();
        configureHoldButtons();
        startInputLoop();
        render(gameModel.snapshot());
        bindKeyboard();
    }

    @FXML
    private void onStartGame() {
        gameEngine.startNewGame();
        requestGameFocus();
    }

    @FXML
    private void onStopGame() {
        gameEngine.stopGame();
        requestGameFocus();
    }

    @FXML
    private void onPauseResume() {
        gameEngine.togglePause();
        requestGameFocus();
    }

    @FXML
    private void onShoot() {
        gameEngine.fireArrow();
        requestGameFocus();
    }

    @FXML
    private void onAimUp() {
        gameEngine.aimUp();
        requestGameFocus();
    }

    @FXML
    private void onAimDown() {
        gameEngine.aimDown();
        requestGameFocus();
    }

    @FXML
    private void onMoveUp() {
        gameEngine.moveArcherUp();
        requestGameFocus();
    }

    @FXML
    private void onMoveDown() {
        gameEngine.moveArcherDown();
        requestGameFocus();
    }

    @FXML
    private void onMoveLeft() {
        gameEngine.moveArcherLeft();
        requestGameFocus();
    }

    @FXML
    private void onMoveRight() {
        gameEngine.moveArcherRight();
        requestGameFocus();
    }

    @FXML
    private void onToggleCrouch() {
        gameEngine.toggleCrouch();
        requestGameFocus();
    }

    public void shutdown() {
        inputLoopActive = false;
        gameEngine.shutdown();
    }

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
            case SPACE -> onShoot();
            case C -> onToggleCrouch();
            default -> {
                return;
            }
        }

        event.consume();
    }

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
            default -> {
                return;
            }
        }
        event.consume();
    }

    private void configureCanvasHost() {
        double hostWidth = WORLD_WIDTH + FIELD_PADDING * 2;
        double hostHeight = WORLD_HEIGHT + FIELD_PADDING * 2;

        gameFieldContainer.setMinSize(hostWidth, hostHeight);
        gameFieldContainer.setPrefSize(hostWidth, hostHeight);
        gameFieldContainer.setMaxSize(hostWidth, hostHeight);

        gameCanvas.setWidth(WORLD_WIDTH);
        gameCanvas.setHeight(WORLD_HEIGHT);
    }

    private void configureHoldButtons() {
        bindHold(moveUpButton, () -> moveUpHeld = true, () -> moveUpHeld = false);
        bindHold(moveDownButton, () -> moveDownHeld = true, () -> moveDownHeld = false);
        bindHold(moveLeftButton, () -> moveLeftHeld = true, () -> moveLeftHeld = false);
        bindHold(moveRightButton, () -> moveRightHeld = true, () -> moveRightHeld = false);
        bindHold(aimUpButton, () -> aimUpHeld = true, () -> aimUpHeld = false);
        bindHold(aimDownButton, () -> aimDownHeld = true, () -> aimDownHeld = false);
    }

    private void bindHold(Button button, Runnable onPress, Runnable onRelease) {
        button.setOnMousePressed(event -> {
            onPress.run();
            requestGameFocus();
        });
        button.setOnMouseReleased(event -> onRelease.run());
        button.setOnMouseExited(event -> onRelease.run());
    }

    private void startInputLoop() {
        inputLoopActive = true;
        inputLoopThread = new Thread(() -> {
            while (inputLoopActive) {
                double deltaX = (moveRightHeld ? 1 : 0) - (moveLeftHeld ? 1 : 0);
                double deltaY = (moveDownHeld ? 1 : 0) - (moveUpHeld ? 1 : 0);
                double deltaAim = (aimUpHeld ? 1 : 0) - (aimDownHeld ? 1 : 0);

                if (deltaX != 0 || deltaY != 0) {
                    gameEngine.moveArcher(deltaX * 3.8, deltaY * 3.8);
                }
                if (deltaAim != 0) {
                    gameEngine.aim(deltaAim * 1.35);
                }

                sleep(16);
            }
        }, "marksman-input");

        inputLoopThread.setDaemon(true);
        inputLoopThread.start();
    }

    private void requestGameFocus() {
        if (rootPane != null) {
            rootPane.requestFocus();
        }
    }

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
    }

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

    private static void drawGuides(GraphicsContext gc, double height, double nearX, double farX) {
        gc.setStroke(Color.rgb(188, 205, 255, 0.42));
        gc.setLineWidth(3);
        gc.strokeLine(nearX + 55, 24, nearX + 55, height - 24);

        gc.setStroke(Color.rgb(255, 237, 171, 0.55));
        gc.setLineWidth(2);
        gc.strokeLine(farX + 27.5, 24, farX + 27.5, height - 24);
    }

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

    private static void sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
