package org.example.marksmanfx.server.network;

import org.example.marksmanfx.server.lobby.LobbyManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Принимает TCP-подключения и создаёт отдельный поток {@link ClientHandler} для каждого клиента.
 * Использует кэшируемый пул потоков, чего достаточно для небольшого числа одновременных игроков.
 */
public final class GameServer {

    private static final Logger LOG = Logger.getLogger(GameServer.class.getName());

    private final int             port;
    private final LobbyManager    lobbyManager = new LobbyManager();
    private final ExecutorService executor     = Executors.newCachedThreadPool(r -> {
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
        LOG.info("[Сервер] Прослушивается порт " + port);

        while (running) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                LOG.info("[Сервер] Принято подключение: " + socket.getRemoteSocketAddress());
                executor.execute(new ClientHandler(socket, lobbyManager));
            } catch (IOException e) {
                if (running) {
                    LOG.warning("[Сервер] Ошибка при accept: " + e.getMessage());
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
        LOG.info("[Сервер] Остановлен");
    }
}
