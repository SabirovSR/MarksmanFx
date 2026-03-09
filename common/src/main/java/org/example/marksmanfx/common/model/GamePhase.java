package org.example.marksmanfx.common.model;

import java.io.Serializable;

public enum GamePhase implements Serializable {
    WAITING,
    PLAYING,
    PAUSE_REQUESTED,
    PAUSED,
    FINISHED
}
