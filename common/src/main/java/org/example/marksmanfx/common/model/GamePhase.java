package org.example.marksmanfx.common.model;

import java.io.Serializable;

/**
 * Фазы жизненного цикла комнаты и матча.
 */
public enum GamePhase implements Serializable {
    /** Игроки находятся в комнате ожидания и готовятся к старту. */
    WAITING,
    /** Матч активен, игровой мир обновляется в реальном времени. */
    PLAYING,
    /** Кто-то запросил паузу, остальные игроки ещё голосуют. */
    PAUSE_REQUESTED,
    /** Игра официально поставлена на паузу. */
    PAUSED,
    /** Матч завершён и доступен реванш или выход. */
    FINISHED
}