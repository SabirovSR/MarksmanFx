package org.example.marksmanfx.server.game;

import org.example.marksmanfx.common.message.GameStateMessage;
import org.example.marksmanfx.common.model.ArrowDto;
import org.example.marksmanfx.common.model.GamePhase;
import org.example.marksmanfx.common.model.PlayerStateDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Авторитетный игровой цикл сервера. Запускается ровно на 60 тиков в секунду.
 *
 * За каждый тик мы:
 *   1. Применяем накопленные флаги ввода к позициям игроков
 *   2. Двигаем мишени с учётом текущего множителя скорости
 *   3. Обновляем полёт всех стрел
 *   4. Проверяем попадания и обновляем счёт
 *   5. Проверяем условие победы (WIN_SCORE очков)
 *   6. Рассылаем снимок состояния всем клиентам комнаты
 *
 * Ввод игроков передаётся через volatile-поля {@link ServerPlayerState},
 * чтобы избежать блокировок между потоком ClientHandler и потоком игрового цикла.
 */
public final class ServerGameSession {

    private static final Logger LOG = Logger.getLogger(ServerGameSession.class.getName());

    private static final int    TPS         = 60;
    private static final long   TICK_MS     = 1000L / TPS;
    private static final int    WIN_SCORE   = 6;
    private static final int    MAX_LEVEL   = 5;

    // Константы мира (совпадают с GameModel из однопользовательской версии)
    private static final double NEAR_X          = 640.0;
    private static final double NEAR_SIZE       = 110.0;
    private static final double NEAR_SPEED      = 90.0;
    private static final double FAR_X           = 770.0;
    private static final double FAR_SIZE        = 55.0;
    private static final double FAR_SPEED       = 180.0;
    private static final double TARGET_TOP_Y    = 36.0;
    private static final double TARGET_BOTTOM_Y = 524.0;

    /** Коллбэк для уведомления GameRoom об окончании матча. */
    public interface GameOverCallback {
        void onGameOver(String winnerId, String winnerNickname);
    }

    // Состояния всех игроков и стрел в текущей сессии
    private final Map<String, ServerPlayerState> players = new ConcurrentHashMap<>();
    private final Map<String, ServerArrowState>  arrows  = new ConcurrentHashMap<>();

    private final ServerTargetState nearTarget;
    private final ServerTargetState farTarget;
    private final GameOverCallback  callback;

    /** Функция рассылки снимка — передаётся из GameRoom при создании сессии. */
    private final Consumer<GameStateMessage> broadcaster;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "marksman-game-loop");
                t.setDaemon(true);
                return t;
            });

    private volatile boolean paused  = false;
    private volatile boolean stopped = false;
    private ScheduledFuture<?> tickFuture;

    public ServerGameSession(List<ServerPlayerState> initialPlayers,
                             GameOverCallback callback,
                             Consumer<GameStateMessage> broadcaster) {
        this.callback    = callback;
        this.broadcaster = broadcaster;

        // Создаём мишени и сбрасываем их в центральное положение
        nearTarget = new ServerTargetState(NEAR_X, NEAR_SIZE, NEAR_SPEED, TARGET_TOP_Y, TARGET_BOTTOM_Y, 1);
        farTarget  = new ServerTargetState(FAR_X,  FAR_SIZE,  FAR_SPEED,  TARGET_TOP_Y, TARGET_BOTTOM_Y, 2);

        // Регистрируем всех игроков и создаём для каждого слот стрелы
        for (ServerPlayerState p : initialPlayers) {
            players.put(p.playerId, p);
            arrows.put(p.playerId, new ServerArrowState(p.playerId));
        }
    }

    // ─── Управление жизненным циклом сессии ──────────────────────────────────

    /** Запускаем игровой цикл. */
    public void start() {
        LOG.info("[Сессия] Игровой цикл запущен: " + TPS + " тиков/сек");
        tickFuture = scheduler.scheduleAtFixedRate(this::tick, 0, TICK_MS, TimeUnit.MILLISECONDS);
    }

    /** Останавливаем игровой цикл и освобождаем ресурсы. */
    public void stop() {
        stopped = true;
        if (tickFuture != null) tickFuture.cancel(false);
        scheduler.shutdown();
        LOG.info("[Сессия] Игровой цикл остановлен");
    }

    /** Замораживаем физику (мишени и стрелы не двигаются). */
    public void pause() {
        paused = true;
        LOG.info("[Сессия] Игра поставлена на паузу");
    }

    /** Снимаем паузу — физика возобновляется. */
    public void resume() {
        paused = false;
        LOG.info("[Сессия] Игра возобновлена");
    }

    // ─── Сеттеры ввода (вызываются из потоков ClientHandler) ─────────────────

    public void playerMove(String playerId, String direction, boolean pressed) {
        ServerPlayerState p = players.get(playerId);
        if (p == null) return;
        switch (direction) {
            case "UP"    -> p.moveUp    = pressed;
            case "DOWN"  -> p.moveDown  = pressed;
            case "LEFT"  -> p.moveLeft  = pressed;
            case "RIGHT" -> p.moveRight = pressed;
        }
    }

    public void playerAim(String playerId, String direction, boolean pressed) {
        ServerPlayerState p = players.get(playerId);
        if (p == null) return;
        switch (direction) {
            case "UP"   -> p.aimUp   = pressed;
            case "DOWN" -> p.aimDown = pressed;
        }
    }

    public void playerCrouch(String playerId, boolean crouching) {
        ServerPlayerState p = players.get(playerId);
        if (p == null) return;
        if (crouching != p.isCrouched()) p.toggleCrouch();
    }

    /**
     * Обрабатываем команду выстрела от игрока.
     *
     * Игнорируем выстрел, если стрела этого игрока ещё в полёте.
     * Новая стрела создаётся только когда предыдущая достигла мишени
     * или вылетела за границу поля.
     */
    public void playerFireArrow(String playerId, double chargeRatio) {
        ServerPlayerState p = players.get(playerId);
        ServerArrowState  a = arrows.get(playerId);
        if (p == null || a == null || paused || stopped) return;

        // Пока стрела летит — новый выстрел невозможен
        if (a.isActive()) {
            LOG.fine("[Сессия] " + p.nickname + " пытается выстрелить, но стрела ещё в полёте — игнорируем");
            return;
        }

        a.activate(p.arrowStartX(), p.arrowStartY(), p.getAimAngleDegrees(), chargeRatio);
        LOG.fine("[Сессия] " + p.nickname + " выпускает стрелу (заряд="
                + String.format("%.2f", chargeRatio) + ")");
    }

    // ─── Основной тик ─────────────────────────────────────────────────────────

    private void tick() {
        if (stopped) return;

        final double dt = TICK_MS / 1000.0; // шаг времени в секундах

        if (!paused) {
            // Применяем накопленный ввод всех игроков
            for (ServerPlayerState p : players.values()) p.applyInput(dt);

            // Двигаем мишени с учётом текущего уровня скорости
            double mult = speedMultiplier();
            nearTarget.advance(dt, mult);
            farTarget.advance(dt, mult);

            // Обновляем полёт стрел и проверяем попадания
            for (ServerArrowState a : arrows.values()) a.advance(dt);
            checkCollisions();
        }

        // Рассылаем снимок состояния независимо от паузы
        broadcastState();
    }

    /** Проверяем, попала ли кончик каждой стрелы в зону мишени. */
    private void checkCollisions() {
        for (ServerArrowState a : arrows.values()) {
            if (!a.isActive()) continue;

            ServerPlayerState owner = players.get(a.getOwnerId());
            if (owner == null) continue;

            double tx = a.tipX();
            double ty = a.tipY();

            if (nearTarget.containsPoint(tx, ty)) {
                a.deactivate();
                owner.addScore(1);
                LOG.info("[Сессия] " + owner.nickname + " поразил ближнюю мишень (+1) — итого: " + owner.getScore());
                checkWin(owner);

            } else if (farTarget.containsPoint(tx, ty)) {
                a.deactivate();
                owner.addScore(2);
                LOG.info("[Сессия] " + owner.nickname + " поразил дальнюю мишень (+2) — итого: " + owner.getScore());
                checkWin(owner);
            }
        }
    }

    /** Проверяем, набрал ли игрок нужное количество очков для победы. */
    private void checkWin(ServerPlayerState scorer) {
        if (scorer.getScore() >= WIN_SCORE && !stopped) {
            stopped = true;
            LOG.info("[Сессия] ИГРА ОКОНЧЕНА — победитель: " + scorer.nickname
                    + " (" + scorer.getScore() + " очков)");
            if (tickFuture != null) tickFuture.cancel(false);
            // Уведомляем GameRoom из отдельного потока, чтобы избежать дедлока
            scheduler.execute(() -> callback.onGameOver(scorer.playerId, scorer.nickname));
        }
    }

    /** Формируем и рассылаем снимок мира всем клиентам комнаты. */
    private void broadcastState() {
        List<PlayerStateDto> playerDtos = new ArrayList<>(players.size());
        for (ServerPlayerState p : players.values()) playerDtos.add(p.toDto());

        List<ArrowDto> arrowDtos = new ArrayList<>(arrows.size());
        for (ServerArrowState a : arrows.values()) arrowDtos.add(a.toDto());

        GamePhase phase = stopped ? GamePhase.FINISHED
                        : paused  ? GamePhase.PAUSED
                                  : GamePhase.PLAYING;

        try {
            broadcaster.accept(new GameStateMessage(
                    playerDtos, arrowDtos,
                    nearTarget.toDto(), farTarget.toDto(),
                    phase));
        } catch (Exception e) {
            LOG.warning("[Сессия] Ошибка при рассылке снимка: " + e.getMessage());
        }
    }

    // ─── Управление составом игроков во время матча ───────────────────────────

    /** Добавляем нового игрока в уже запущенную сессию. */
    public void addPlayer(ServerPlayerState p) {
        players.put(p.playerId, p);
        arrows.put(p.playerId, new ServerArrowState(p.playerId));
    }

    /** Удаляем отключившегося игрока и деактивируем его стрелу. */
    public void removePlayer(String playerId) {
        players.remove(playerId);
        ServerArrowState a = arrows.remove(playerId);
        if (a != null) a.deactivate();
    }

    public int getPlayerCount() { return players.size(); }

    // ─── Вспомогательные методы ───────────────────────────────────────────────

    /**
     * Скорость мишеней растёт вместе с максимальным счётом в комнате,
     * повторяя формулу уровней из однопользовательской игры.
     */
    private double speedMultiplier() {
        int maxScore = players.values().stream()
                .mapToInt(ServerPlayerState::getScore).max().orElse(0);
        int level = Math.min(MAX_LEVEL, maxScore / 10 + 1);
        return 1.0 + (level - 1) * 0.20;
    }
}
