package org.example.marksmanfx.common.message;

import org.example.marksmanfx.common.model.RoomInfo;

import java.io.Serial;
import java.util.List;

/** Broadcast to every client in the lobby whenever the room list changes. */
public record LobbyStateMessage(List<RoomInfo> rooms) implements ServerMessage {
    @Serial private static final long serialVersionUID = 1L;
}
