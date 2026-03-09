package org.example.marksmanfx.server.network;

import org.example.marksmanfx.server.lobby.LobbyManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Accepts TCP connections and spawns a {@link ClientHandler} thread per client.
 * Uses a cached thread pool — fine for ≤16 simultaneous players.
 */
public final class GameServer {

    private static final Logger LOG = Logger.getLogger(GameServer.class.getName());

    private final int           port;
    private final LobbyManager  lobbyManager = new LobbyManager();
    private final ExecutorService executor   = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });

    private volatile boolean running;
    private ServerSocket serverSocket;

    public GameServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running      = true;
        LOG.info("[Server] Listening on port " + port);

        while (running) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                LOG.info("[Server] Accepted: " + socket.getRemoteSocketAddress());
                executor.execute(new ClientHandler(socket, lobbyManager));
            } catch (IOException e) {
                if (running) {
                    LOG.warning("[Server] Accept error: " + e.getMessage());
                }
            }
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {}
        executor.shutdownNow();
        LOG.info("[Server] Stopped");
    }
}
