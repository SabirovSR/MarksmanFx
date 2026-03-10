package org.example.marksmanfx.server.game.model;

/**
 * Роль участника в игровой комнате.
 *
 * Мы вводим это разделение для поддержки системы зрителей.
 * PLAYER — активный участник матча (не более 4 в комнате).
 * SPECTATOR — наблюдатель, получает GameStateMessage, но не может стрелять.
 *             Может запросить повышение до PLAYER через SpectatorUpgradeRequest.
 */
public enum PlayerRole {
    PLAYER,
    SPECTATOR
}
