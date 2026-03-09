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
 * Manages the persistent TCP connection to the game server.
 *
 * A background daemon thread reads incoming {@link ServerMessage}s and
 * dispatches them to the active {@link MessageListener} on the FX thread.
 * Outbound {@link ClientEvent}s are written synchronously on the caller's thread.
 */
public final class ServerConnection {

    private static final Logger LOG = Logger.getLogger(ServerConnection.class.getName());

    private Socket             socket;
    private ObjectOutputStream out;
    private ObjectInputStream  in;
    private Thread             readerThread;
    private volatile MessageListener listener;

    /** @throws IOException if the TCP connection cannot be established. */
    public void connect(String host, int port) throws IOException {
        socket = new Socket(host, port);
        socket.setTcpNoDelay(true);

        // ObjectOutputStream first to avoid deadlock (mirrors server-side order)
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        in  = new ObjectInputStream(socket.getInputStream());

        readerThread = new Thread(this::readLoop, "marksman-client-reader");
        readerThread.setDaemon(true);
        readerThread.start();

        LOG.info("[Client] Connected to " + host + ":" + port);
    }

    public void disconnect() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {}
    }

    /** Thread-safe send. May be called from any thread. */
    public void send(ClientEvent event) {
        if (out == null) return;
        try {
            synchronized (out) {
                out.writeObject(event);
                out.flush();
                out.reset();
            }
        } catch (IOException e) {
            LOG.warning("[Client] Send failed: " + e.getMessage());
        }
    }

    /** Replace the active listener. The new listener receives all subsequent messages. */
    public void setListener(MessageListener listener) {
        this.listener = listener;
    }

    // ─── Background reader ────────────────────────────────────────────────────

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
            LOG.info("[Client] Connection closed: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            LOG.warning("[Client] Unknown class: " + e.getMessage());
        } finally {
            MessageListener l = listener;
            if (l != null) {
                Platform.runLater(l::onDisconnected);
            }
        }
    }
}
