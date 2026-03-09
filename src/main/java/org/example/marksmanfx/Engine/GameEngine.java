package org.example.marksmanfx.Engine;

import javafx.application.Platform;
import org.example.marksmanfx.Models.GameModel;
import org.example.marksmanfx.Models.GameSnapshot;

import java.util.function.Consumer;

/// Прослойка между UI и моделью
/// - принимать команды от контроллера;
/// - запускать и останавливать игровые циклы;
/// - после каждого изменения публиковать снимок состояния в UI.
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

    /// Сбрасываем игру через модель, убеждаемся, что главный игровой цикл запущен, и публикуем первый кадр
    public void startNewGame() {
        model.startNewGame();
        ensureLoopStarted();
        publishFrame();
    }

    /// Останавливаем игру в модели, отдельно прерывает поток стрелы и обновляет экран
    public void stopGame() {
        model.stopGame();
        stopArrowThread();
        publishFrame();
    }

    /// Переключаем паузу в модели и обновляет кадр
    public void togglePause() {
        model.togglePause();
        publishFrame();
    }

    /// Просим модель выпустить стрелу. Если выстрел разрешен, запускаем поток стрелы и публикует кадр
    public void fireArrow(double chargeRatio) {
        if (!model.fireArrow(chargeRatio)) {
            return;
        }

        launchArrowThread();
        publishFrame();
    }

    /// Сдвигаем лучника на переданную величину и обновляем экран.
    public void moveArcher(double deltaX, double deltaY) {
        model.moveArcher(deltaX, deltaY);
        publishFrame();
    }

    /// Меняем угол на переданную величину и обновляем экран
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

    /// Переключаем стойку и публикуем кадр
    public void toggleCrouch() {
        model.toggleCrouch();
        publishFrame();
    }

    /// Останавливаем основной цикл и поток стрелы
    public void shutdown() {
        loopActive = false;
        model.wakeWaitingThreads();
        stopArrowThread();
    }

    /// Запускаем поток `marksman-game-loop`, если он еще не работает
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

                boolean wasPaused = model.isPaused();
                if (!model.waitWhilePaused()) {
                    break;
                }
                if (wasPaused) {
                    lastTick = System.nanoTime();
                    continue;
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

    /// Запускаем отдельный поток `marksman-arrow` для полета стрелы
    private void launchArrowThread() {
        stopArrowThread();

        arrowThread = new Thread(() -> {
            long lastTick = System.nanoTime();
            boolean active = true;

            while (active) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                boolean wasPaused = model.isPaused();
                if (!model.waitWhilePaused()) {
                    break;
                }
                if (wasPaused) {
                    lastTick = System.nanoTime();
                    continue;
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

    /// Создаем `snapshot()` модели и передаем его в UI через `Platform.runLater(...)`.
    // Почему нельзя просто вызвать `render(...)` из фонового потока:
    // JavaFX запрещает менять UI не из JavaFX Application Thread.
    // Поэтому кадр сначала превращается в снимок данных, а потом уже безопасно отдается в поток интерфейса.
    private void publishFrame() {
        GameSnapshot snapshot = model.snapshot();
        Platform.runLater(() -> onFrame.accept(snapshot));
    }

    // Прерываем поток стрелы, если он существует
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
