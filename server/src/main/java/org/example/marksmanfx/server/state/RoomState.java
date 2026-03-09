package org.example.marksmanfx.server.state;

import org.example.marksmanfx.server.game.GameRoom;
import org.example.marksmanfx.server.network.ClientHandler;

/**
 * Интерфейс паттерна «Состояние» для жизненного цикла игровой комнаты.
 *
 * Каждый метод принимает инициирующего игрока и саму комнату,
 * а возвращает следующее состояние (или {@code this}, если переход не нужен).
 * Методы по умолчанию ничего не делают — подклассы переопределяют
 * только актуальные для них события.
 */
public interface RoomState {

    default RoomState onPlayerReady(ClientHandler player, boolean ready, GameRoom room) {
        return this;
    }

    default RoomState onPauseRequest(ClientHandler player, boolean pausing, GameRoom room) {
        return this;
    }

    default RoomState onPlayerDisconnect(ClientHandler player, GameRoom room) {
        return this;
    }

    default RoomState onFireArrow(ClientHandler player, double chargeRatio, GameRoom room) {
        return this;
    }

    default RoomState onMove(ClientHandler player, String direction, boolean pressed, GameRoom room) {
        return this;
    }

    default RoomState onAim(ClientHandler player, String direction, boolean pressed, GameRoom room) {
        return this;
    }

    default RoomState onCrouch(ClientHandler player, boolean crouching, GameRoom room) {
        return this;
    }

    /** Обрабатываем запрос реванша после окончания матча. */
    default RoomState onRematchRequest(ClientHandler player, GameRoom room) {
        return this;
    }

    /** Возвращаем читаемое имя текущего состояния для логов и отладки. */
    String name();
}
