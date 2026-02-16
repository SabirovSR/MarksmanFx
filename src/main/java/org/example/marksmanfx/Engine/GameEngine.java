package org.example.marksmanfx.Engine;

import javafx.application.Platform;
import org.example.marksmanfx.Models.GameModel;
import org.example.marksmanfx.Models.GameSnapshot;

import java.util.function.Consumer;

public class GameEngine {
    private static final long FRAME_SLEEP_MS = 16;
    private static final long ARROW_SLEEP_MS = 12;

    private final GameModel model;
    private final Consumer<GameSnapshot> onFrame;

    private volatile boolean loopActive;
    private Thread gameLoopThread;
    private Thread arrowThread;

    public GameEngine(GameModel model, Consumer<GameSnapshot> onFrame) {
        this.model = model;
        this.onFrame = onFrame;
    }

    public void startNewGame() {
        model.startNewGame();
        ensureLoopStarted();
        publishFrame();
    }

    public void stopGame() {
        model.stopGame();
        stopArrowThread();
        publishFrame();
    }

    public void togglePause() {
        model.togglePause();
        publishFrame();
    }

    public boolean fireArrow() {
        if (!model.fireArrow()) {
            return false;
        }

        launchArrowThread();
        publishFrame();
        return true;
    }

    public void moveArcher(double deltaX, double deltaY) {
        model.moveArcher(deltaX, deltaY);
        publishFrame();
    }

    public void aim(double deltaAngle) {
        model.aim(deltaAngle);
        publishFrame();
    }

    public void moveArcherUp() {
        model.moveArcherUp();
        publishFrame();
    }

    public void moveArcherDown() {
        model.moveArcherDown();
        publishFrame();
    }

    public void moveArcherLeft() {
        model.moveArcherLeft();
        publishFrame();
    }

    public void moveArcherRight() {
        model.moveArcherRight();
        publishFrame();
    }

    public void aimUp() {
        model.aimUp();
        publishFrame();
    }

    public void aimDown() {
        model.aimDown();
        publishFrame();
    }

    public void toggleCrouch() {
        model.toggleCrouch();
        publishFrame();
    }

    public void shutdown() {
        loopActive = false;
        stopArrowThread();
    }

    private void ensureLoopStarted() {
        if (gameLoopThread != null && gameLoopThread.isAlive()) {
            return;
        }

        loopActive = true;
        gameLoopThread = new Thread(() -> {
            long lastTick = System.nanoTime();

            while (loopActive) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                long now = System.nanoTime();
                double deltaSeconds = (now - lastTick) / 1_000_000_000.0;
                lastTick = now;

                model.updateTargets(deltaSeconds);
                publishFrame();

                if (!sleep(FRAME_SLEEP_MS)) {
                    break;
                }
            }
        }, "marksman-game-loop");

        gameLoopThread.setDaemon(true);
        gameLoopThread.start();
    }

    private void launchArrowThread() {
        stopArrowThread();

        arrowThread = new Thread(() -> {
            long lastTick = System.nanoTime();
            boolean active = true;

            while (active) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                long now = System.nanoTime();
                double deltaSeconds = (now - lastTick) / 1_000_000_000.0;
                lastTick = now;

                active = model.updateArrow(deltaSeconds);
                publishFrame();

                if (!sleep(ARROW_SLEEP_MS)) {
                    break;
                }
            }
        }, "marksman-arrow");

        arrowThread.setDaemon(true);
        arrowThread.start();
    }

    private void publishFrame() {
        GameSnapshot snapshot = model.snapshot();
        Platform.runLater(() -> onFrame.accept(snapshot));
    }

    private void stopArrowThread() {
        if (arrowThread == null) {
            return;
        }

        Thread thread = arrowThread;
        arrowThread = null;

        if (thread.isAlive()) {
            thread.interrupt();
        }
    }

    private static boolean sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
            return true;
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}