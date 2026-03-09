package org.example.marksmanfx.common.message;

import org.example.marksmanfx.common.model.ArrowDto;
import org.example.marksmanfx.common.model.GamePhase;
import org.example.marksmanfx.common.model.PlayerStateDto;
import org.example.marksmanfx.common.model.TargetDto;

import java.io.Serial;
import java.util.List;

/**
 * Authoritative world snapshot broadcast by the server every game tick (~60 Hz).
 * Clients render exclusively from this data — no local physics extrapolation.
 */
public record GameStateMessage(
        List<PlayerStateDto> players,
        List<ArrowDto> arrows,
        TargetDto nearTarget,
        TargetDto farTarget,
        GamePhase phase
) implements ServerMessage {
    @Serial private static final long serialVersionUID = 1L;
}
