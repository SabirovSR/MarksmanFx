package org.example.marksmanfx.server.game.model;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Игровая комната в памяти сервера (не JPA-сущность).
 *
 * Мы используем CopyOnWriteArrayList для хранения участников, потому что
 * операций чтения (рассылка GameStateMessage 60 раз в секунду) значительно
 * больше, чем операций записи (вход/выход игрока). COW гарантирует
 * безопасную итерацию без блокировок.
 *
 * Для операций, изменяющих список (join, leave, upgrade), мы всё равно
 * используем synchronized-блоки, чтобы атомарно проверить и изменить состояние.
 */
public class GameRoom {

    /** Максимум активных игроков (PLAYER) в одной комнате */
    public static final int MAX_PLAYERS = 4;

    @Getter private final String roomId;
    @Getter private final String roomName;

    /**
     * CopyOnWriteArrayList: операции итерации (broadcast) работают без блокировок,
     * мутирующие операции (add/remove) атомарно копируют весь массив — это приемлемо
     * для редких событий join/leave.
     */
    private final List<RoomParticipant> participants = new CopyOnWriteArrayList<>();

    @Getter private volatile GamePhase phase = GamePhase.LOBBY;

    public GameRoom(String roomId, String roomName) {
        this.roomId   = roomId;
        this.roomName = roomName;
    }

    /**
     * Мы добавляем нового участника в комнату.
     *
     * Если слотов для игроков меньше MAX_PLAYERS — добавляем как PLAYER.
     * Иначе — добавляем как SPECTATOR (зрителей неограниченное количество).
     */
    public synchronized RoomParticipant addParticipant(String username, String sessionId) {
        PlayerRole role = (getPlayerCount() < MAX_PLAYERS) ? PlayerRole.PLAYER : PlayerRole.SPECTATOR;
        RoomParticipant participant = new RoomParticipant(username, sessionId, role, false);
        participants.add(participant);
        return participant;
    }

    /**
     * Мы удаляем участника по его sessionId (при дисконнекте или выходе из комнаты).
     */
    public synchronized void removeParticipant(String sessionId) {
        participants.removeIf(p -> p.getSessionId().equals(sessionId));
    }

    /**
     * Мы пытаемся повысить зрителя до статуса игрока.
     *
     * Возвращаем true, если слот нашёлся и роль изменена.
     * Возвращаем false, если все 4 слота заняты — клиент получит RoomFullEvent.
     */
    public synchronized boolean upgradeSpectatorToPlayer(String sessionId) {
        // Проверяем наличие свободных слотов для игроков
        if (getPlayerCount() >= MAX_PLAYERS) {
            return false;
        }

        // Находим зрителя и повышаем его роль до игрока
        participants.stream()
                .filter(p -> p.getSessionId().equals(sessionId) && p.isSpectator())
                .findFirst()
                .ifPresent(p -> {
                    p.setRole(PlayerRole.PLAYER);
                    p.setReady(false);
                });
        return true;
    }

    /** Мы считаем только активных игроков (не зрителей) */
    public long getPlayerCount() {
        return participants.stream().filter(RoomParticipant::isPlayer).count();
    }

    /** Мы отдаём снимок списка участников для сериализации в JSON */
    public List<RoomParticipant> getParticipants() {
        return new ArrayList<>(participants);
    }

    public void setPhase(GamePhase phase) {
        this.phase = phase;
    }

    public boolean isEmpty() {
        return participants.isEmpty();
    }

    /** Находим участника по sessionId */
    public RoomParticipant findBySessionId(String sessionId) {
        return participants.stream()
                .filter(p -> p.getSessionId().equals(sessionId))
                .findFirst()
                .orElse(null);
    }
}
