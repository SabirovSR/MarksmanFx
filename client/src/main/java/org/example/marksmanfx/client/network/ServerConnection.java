package org.example.marksmanfx.client.network;

import javafx.application.Platform;
import org.example.marksmanfx.common.event.ClientEvent;
import org.example.marksmanfx.common.message.ServerMessage;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.logging.Logger;

/**
 * Управляем постоянным TCP-соединением с игровым сервером.
 *
 * Фоновый поток-демон читает входящие {@link ServerMessage} и
 * передаёт их активному {@link MessageListener} в потоке JavaFX.
 * Исходящие {@link ClientEvent} отправляются синхронно из потока вызывающей стороны.
 */
public final class ServerConnection {

    private static final Logger LOG = Logger.getLogger(ServerConnection.class.getName());

    private Socket             socket;
    private ObjectOutputStream out;
    private ObjectInputStream  in;
    private Thread             readerThread;
    private volatile MessageListener listener;

    /** @throws IOException если не удалось установить TCP-соединение. */
    public void connect(String host, int port) throws IOException {
        socket = new Socket(host, port);
        socket.setTcpNoDelay(true);

        // Сначала создаём ObjectOutputStream, чтобы избежать взаимной блокировки.
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        in  = new ObjectInputStream(socket.getInputStream());

        readerThread = new Thread(this::readLoop, "marksman-client-reader");
        readerThread.setDaemon(true);
        readerThread.start();

        LOG.info("[Клиент] Подключение к " + host + ":" + port + " установлено");
    }

    public void disconnect() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {}
    }

    /** Потокобезопасная отправка. Можно вызывать из любого потока. */
    public void send(ClientEvent event) {
        if (out == null) return;
        try {
            synchronized (out) {
                out.writeObject(event);
                out.flush();
                out.reset();
            }
        } catch (IOException e) {
            LOG.warning("[Клиент] Не удалось отправить сообщение: " + e.getMessage());
        }
    }

    /** Заменяем активного слушателя. Новый слушатель получает все последующие сообщения. */
    public void setListener(MessageListener listener) {
        this.listener = listener;
    }

    // Фоновое чтение входящих сообщений.
    private void readLoop() {
        try {
            while (!socket.isClosed()) {
                Object obj = in.readObject();
                if (obj instanceof ServerMessage msg) {
                    MessageListener l = listener;
                    if (l != null) {
                        Platform.runLater(() -> l.onMessage(msg));
                    }
                }
            }
        } catch (IOException e) {
            LOG.info("[Клиент] Соединение закрыто: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            LOG.warning("[Клиент] Получен объект неизвестного класса: " + e.getMessage());
        } finally {
            MessageListener l = listener;
            if (l != null) {
                Platform.runLater(l::onDisconnected);
            }
        }
    }
}

