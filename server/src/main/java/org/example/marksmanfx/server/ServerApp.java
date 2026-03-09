package org.example.marksmanfx.server;

import org.example.marksmanfx.server.network.GameServer;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Точка входа выделенного игрового сервера.
 * Использование: java -jar marksmanfx-server-fat.jar [port]
 * Порт по умолчанию: 55555
 */
public final class ServerApp {

    private static final Logger LOG          = Logger.getLogger(ServerApp.class.getName());
    private static final int    DEFAULT_PORT = 55555;

    public static void main(String[] args) {
        // Настраиваем более читаемый формат логов в консоли.
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tT] [%4$s] %5$s%n");
        Logger.getLogger("").setLevel(Level.INFO);

        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                LOG.warning("Некорректный порт '" + args[0] + "', используется значение по умолчанию " + DEFAULT_PORT);
            }
        }

        GameServer server = new GameServer(port);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("[Сервер] Сработал обработчик завершения");
            server.stop();
        }));

        try {
            server.start();
        } catch (IOException e) {
            LOG.severe("[Сервер] Критическая ошибка: " + e.getMessage());
            System.exit(1);
        }
    }
}

