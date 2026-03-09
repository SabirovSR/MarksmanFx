package org.example.marksmanfx.Models;

/// Центральное хранилище состояния игры
/// - флаги `running` и `paused`;
/// - счет и число выстрелов;
/// - координаты лучника;
/// - угол стрельбы;
/// - стойка лучника;
/// - две мишени;
/// - стрела.
public class GameModel {
    private static final double FIELD_WIDTH = 960;
    private static final double FIELD_HEIGHT = 560;

    private static final double ARCHER_MIN_X = 46;
    private static final double ARCHER_MAX_X = 260;
    private static final double ARCHER_MIN_Y = 165;
    private static final double ARCHER_MAX_Y = 500;

    private static final double AIM_MIN_ANGLE = -45;
    private static final double AIM_MAX_ANGLE = 45;
    private static final int MAX_LEVEL = 5;
    private static final double MIN_SHOT_SPEED_MULTIPLIER = 1.0;
    private static final double MAX_SHOT_SPEED_MULTIPLIER = 2.4;

    /// К модели обращаются сразу несколько потоков:
    /// - поток JavaFX через кнопки и отрисовку;
    /// - поток обработки ввода;
    /// - игровой цикл мишеней;
    /// - поток полета стрелы.
    /// Чтобы состояние не ломалось от одновременного доступа, почти все методы завернуты в `synchronized(lock)`
    private final Object lock = new Object();

    private final TargetModel nearTarget = new TargetModel(TargetType.NEAR, 640, 110, 90, 36, FIELD_HEIGHT - 36);
    private final TargetModel farTarget = new TargetModel(TargetType.FAR, 770, 55, 180, 36, FIELD_HEIGHT - 36);
    private final ArrowModel arrow = new ArrowModel(760, -80, FIELD_WIDTH + 80, -40, FIELD_HEIGHT + 40, 54, 12);

    private boolean running;
    private boolean paused;
    private int score;
    private int shots;
    private double archerX;
    private double archerY;
    private double aimAngleDegrees;
    private boolean crouched;

    public GameModel() {
        resetEntities();
    }

    /// Инициализируем новую партию. Подготавливаем стартовое состояние
    public void startNewGame() {
        synchronized (lock) {
            running = true;
            paused = false;
            score = 0;
            shots = 0;
            resetEntities();
            lock.notifyAll();
        }
    }

    /// Останавливаем игру и тоже сбрасываем объекты
    public void stopGame() {
        synchronized (lock) {
            running = false;
            paused = false;
            resetEntities();
            lock.notifyAll();
        }
    }

    /// Меняем состояние паузы, если игра вообще запущена
    public void togglePause() {
        synchronized (lock) {
            if (!running) {
                return;
            }
            paused = !paused;
            if (!paused) {
                lock.notifyAll();
            }
        }
    }

    public boolean isPaused() {
        synchronized (lock) {
            return paused;
        }
    }

    public boolean waitWhilePaused() {
        synchronized (lock) {
            while (running && paused) {
                try {
                    lock.wait();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }

            return true;
        }
    }

    public void wakeWaitingThreads() {
        synchronized (lock) {
            lock.notifyAll();
        }
    }

    /// Проверяем, можно ли стрелять, и если можно, активируем стрелу
    public boolean fireArrow(double chargeRatio) {
        synchronized (lock) {
            if (!running || paused || arrow.isActive()) {
                return false;
            }

            shots++;
            double clampedCharge = clamp(chargeRatio, 0.0, 1.0);
            double speedMultiplier = MIN_SHOT_SPEED_MULTIPLIER
                    + (MAX_SHOT_SPEED_MULTIPLIER - MIN_SHOT_SPEED_MULTIPLIER) * clampedCharge;

            // Стрела активируется с нужными координатами и углом
            arrow.activate(arrowStartX(), arrowStartY(), aimAngleDegrees, speedMultiplier);
            return true;
        }
    }

    /// Перемещаем лучника в пределах разрешенной зоны
    public void moveArcher(double deltaX, double deltaY) {
        synchronized (lock) {
            if (paused) {
                return;
            }
            archerX = clamp(archerX + deltaX, ARCHER_MIN_X, ARCHER_MAX_X);
            archerY = clamp(archerY + deltaY, ARCHER_MIN_Y, ARCHER_MAX_Y);
        }
    }

    /// Меняем угол и ограничиваем его диапазоном от AIM_MIN_ANGLE = `-45` до AIM_MAX_ANGLE = `45` градусов
    public void aim(double deltaAngleDegrees) {
        synchronized (lock) {
            aimAngleDegrees = clamp(aimAngleDegrees + deltaAngleDegrees, AIM_MIN_ANGLE, AIM_MAX_ANGLE);
        }
    }

    /// Дискретные шаги движения по 14 пикселей
    public void moveArcherUp() {
        moveArcher(0, -14);
    }

    public void moveArcherDown() {
        moveArcher(0, 14);
    }

    public void moveArcherLeft() {
        moveArcher(-14, 0);
    }

    public void moveArcherRight() {
        moveArcher(14, 0);
    }

    /// Шаги изменения угла по 4 градуса
    public void aimUp() {
        aim(4);
    }

    public void aimDown() {
        aim(-4);
    }

    /// Меняем флаг приседа, если игра не на паузе
    public void toggleCrouch() {
        synchronized (lock) {
            if (paused) {
                return;
            }
            crouched = !crouched;
        }
    }

    /// Двигаем обе мишени, если игра идет и не стоит на паузе
    public void updateTargets(double deltaSeconds) {
        synchronized (lock) {
            if (!running || paused) {
                return;
            }

            // Считаем множитель скорости по уровню
            double speedMultiplier = 1.0 + (currentLevel() - 1) * 0.20;
            nearTarget.advance(deltaSeconds, speedMultiplier);
            farTarget.advance(deltaSeconds, speedMultiplier);
        }
    }

    /// Обновляем полет стрелы и проверяет попадания
    public boolean updateArrow(double deltaSeconds) {
        synchronized (lock) {
            if (!running) {
                return false;
            }
            if (paused || !arrow.isActive()) {
                return arrow.isActive();
            }

            arrow.advance(deltaSeconds);
            if (!arrow.isActive()) {
                return false;
            }

            double tipX = arrow.tipX();
            double tipY = arrow.tipY();

            if (nearTarget.containsPoint(tipX, tipY)) {
                score += 1;
                arrow.deactivate();
                return false;
            }

            if (farTarget.containsPoint(tipX, tipY)) {
                score += 2;
                arrow.deactivate();
                return false;
            }

            return true;
        }
    }

    /// Создаем неизменяемый снимок всего состояния игры
    public GameSnapshot snapshot() {
        synchronized (lock) {
            return new GameSnapshot(
                    running,
                    paused,
                    score,
                    shots,
                    currentLevel(),
                    archerX,
                    archerY,
                    aimAngleDegrees,
                    crouched,
                    nearTarget.snapshot(),
                    farTarget.snapshot(),
                    arrow.snapshot()
            );
        }
    }

    /// Инициализируем объекты в стартовые позиции
    private void resetEntities() {
        nearTarget.resetToCenter();
        farTarget.resetToCenter();
        arrow.deactivate();
        archerX = 70;
        archerY = FIELD_HEIGHT * 0.5 + 28;
        aimAngleDegrees = 0;
        crouched = false;
    }

    /// Считаем стартовую `x`-координату стрелы относительно лучника
    private double arrowStartX() {
        return archerX + 58;
    }

    /// Считаем стартовую `y`-координату стрелы. Учитываем, присел лучник или нет
    private double arrowStartY() {
        double shoulderY = archerY - (crouched ? 8 : 42);
        return shoulderY + 2;
    }

    private static double clamp(double value, double minValue, double maxValue) {
        return Math.max(minValue, Math.min(maxValue, value));
    }

    /// Возвращаем
    private int currentLevel() {
        return Math.min(MAX_LEVEL, score / 10 + 1);
    }
}
