package org.example.marksmanfx.client.network;

import org.example.marksmanfx.common.message.ServerMessage;

/** Implemented by each screen controller to receive server messages. */
public interface MessageListener {
    /** Called on the JavaFX Application Thread. */
    void onMessage(ServerMessage message);

    /** Called on the JavaFX Application Thread when the connection drops. */
    void onDisconnected();
}
