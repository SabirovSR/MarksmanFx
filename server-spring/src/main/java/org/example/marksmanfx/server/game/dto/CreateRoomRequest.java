package org.example.marksmanfx.server.game.dto;

/**
 * STOMP payload для создания новой комнаты.
 *
 * Мы принимаем этот DTO на destination /app/lobby/create.
 * Клиент отправляет: { "roomName": "Арена Льва" }
 */
public record CreateRoomRequest(String roomName) {}
