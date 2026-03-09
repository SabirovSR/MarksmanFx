package org.example.marksmanfx.client.network;

import org.example.marksmanfx.common.message.ServerMessage;

/** Реализуется контроллерами экранов для получения сообщений от сервера. */
public interface MessageListener {
    /** Вызывается в потоке JavaFX Application Thread. */
    void onMessage(ServerMessage message);

    /** Вызывается в потоке JavaFX Application Thread при разрыве соединения. */
    void onDisconnected();
}

