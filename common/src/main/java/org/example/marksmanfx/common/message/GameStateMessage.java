package org.example.marksmanfx.common.message;

import org.example.marksmanfx.common.model.ArrowDto;
import org.example.marksmanfx.common.model.GamePhase;
import org.example.marksmanfx.common.model.PlayerStateDto;
import org.example.marksmanfx.common.model.TargetDto;

import java.io.Serial;
import java.util.List;

/**
 * Авторитетный снимок игрового мира, который сервер рассылает каждый тик.
 *
 * @param players    состояние всех игроков в текущем кадре
 * @param arrows     состояние всех стрел в полёте
 * @param nearTarget состояние ближней мишени
 * @param farTarget  состояние дальней мишени
 * @param phase      текущая фаза матча
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