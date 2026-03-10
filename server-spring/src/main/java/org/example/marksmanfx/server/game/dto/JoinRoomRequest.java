package org.example.marksmanfx.server.game.dto;

/**
 * STOMP payload для входа в существующую комнату.
 *
 * Мы принимаем этот DTO на destination /app/lobby/join.
 * Клиент отправляет: { "roomId": "A1B2C3D4" }
 */
public record JoinRoomRequest(String roomId) {}
