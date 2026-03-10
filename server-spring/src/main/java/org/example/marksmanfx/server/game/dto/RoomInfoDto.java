package org.example.marksmanfx.server.game.dto;

/**
 * DTO описания комнаты в общем списке лобби.
 *
 * Мы рассылаем этот объект всем подписчикам /topic/lobby и отправляем
 * в ответ на @SubscribeMapping("/lobby") для первоначального заполнения таблицы.
 *
 * Поля зеркалят интерфейс RoomInfo на стороне Vue.js-клиента.
 */
public record RoomInfoDto(
        String roomId,
        String roomName,
        int    playerCount,
        int    maxPlayers,
        /** Строковое имя GamePhase: "LOBBY", "PLAYING", "PAUSED" и т.д. */
        String phase
) {}
