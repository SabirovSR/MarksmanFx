package org.example.marksmanfx.client.auth;

import java.util.logging.Logger;

/**
 * Менеджер активной сессии пользователя — единственный источник правды
 * о состоянии аутентификации в оперативной памяти клиента.
 *
 * Паттерн: Singleton с двойной проверкой блокировки (Double-Checked Locking).
 * В Java 21 с {@code volatile} это потокобезопасно — гарантируется корректная
 * публикация через барьер памяти.
 *
 * Зачем нам два слоя?
 *   — {@link TokenStorage} (Preferences) — переживает перезапуск JVM.
 *     Читается один раз при старте приложения.
 *   — {@link SessionManager} (RAM) — быстрый доступ без I/O в игровом цикле.
 *     Актуален всё время, пока приложение запущено.
 *
 * Связка: при логине мы обновляем оба слоя; при логауте — очищаем оба.
 */
public final class SessionManager {

    private static final Logger LOG = Logger.getLogger(SessionManager.class.getName());

    /** Volatile гарантирует видимость инициализации между потоками */
    private static volatile SessionManager instance;

    // ──────────────────────────────────────────────────────────────────────
    //  Состояние активной сессии в оперативной памяти
    // ──────────────────────────────────────────────────────────────────────

    /** Имя текущего аутентифицированного пользователя (из sub claim JWT) */
    private volatile String currentUser;

    /** JWT Bearer токен для HTTP и WebSocket запросов */
    private volatile String jwtToken;

    /**
     * Базовый URL REST API, например "http://localhost:8080".
     * Сохраняем в сессии, чтобы не передавать через весь стек вызовов.
     */
    private volatile String serverBaseUrl;

    private SessionManager() {}

    /**
     * Мы возвращаем единственный экземпляр SessionManager.
     *
     * Двойная проверка блокировки: первая проверка без блокировки для 99% вызовов
     * (когда экземпляр уже создан), вторая — под блокировкой при первом создании.
     */
    public static SessionManager getInstance() {
        if (instance == null) {
            synchronized (SessionManager.class) {
                if (instance == null) {
                    instance = new SessionManager();
                    LOG.fine("[SessionManager] Экземпляр создан");
                }
            }
        }
        return instance;
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Управление сессией
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Мы фиксируем успешный вход пользователя.
     *
     * Обновляем состояние в RAM и — при наличии флага «Запомнить меня» —
     * сохраняем токен и URL в {@link TokenStorage} для автологина.
     *
     * @param username    имя пользователя из JWT-токена
     * @param token       JWT Bearer токен
     * @param serverUrl   базовый URL сервера (например "http://game.example.com")
     * @param rememberMe  если true — сохраняем токен в Preferences
     */
    public void login(String username, String token, String serverUrl, boolean rememberMe) {
        this.currentUser   = username;
        this.jwtToken      = token;
        this.serverBaseUrl = serverUrl;

        if (rememberMe) {
            // Мы сохраняем токен и URL в постоянное хранилище, чтобы
            // при следующем запуске пользователь сразу попал в лобби
            TokenStorage.saveToken(token);
            TokenStorage.saveServerUrl(serverUrl);
            TokenStorage.saveLastUsername(username);
            LOG.info("[SessionManager] Сессия сохранена для автологина: " + username);
        } else {
            // Мы не сохраняем токен — только данные сессии в RAM
            LOG.info("[SessionManager] Вход без сохранения: " + username);
        }
    }

    /**
     * Мы восстанавливаем сессию из постоянного хранилища при автологине.
     *
     * Вызывается из {@link AutoLoginService} после успешной проверки токена на сервере.
     * Токен уже есть в Preferences — в RAM его просто не было (приложение перезапустилось).
     *
     * @param username  имя пользователя, полученное с /api/auth/me
     * @param token     токен, прочитанный из TokenStorage
     * @param serverUrl URL сервера, прочитанный из TokenStorage
     */
    public void restore(String username, String token, String serverUrl) {
        this.currentUser   = username;
        this.jwtToken      = token;
        this.serverBaseUrl = serverUrl;
        LOG.info("[SessionManager] Сессия восстановлена для: " + username);
    }

    /**
     * Мы выполняем полный выход из системы.
     *
     * Очищаем состояние в RAM и удаляем токен из Preferences.
     * После этого isLoggedIn() вернёт false, и следующий запуск покажет LoginScreen.
     */
    public void logout() {
        String prevUser = this.currentUser;
        this.currentUser   = null;
        this.jwtToken      = null;
        this.serverBaseUrl = null;

        // Мы удаляем токен из постоянного хранилища — автологин больше не сработает
        TokenStorage.clearToken();
        LOG.info("[SessionManager] Выход: " + prevUser);
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Запросы состояния (используются в контроллерах и сервисах)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Мы проверяем, активна ли сессия.
     * Возвращает true только если и токен, и имя пользователя присутствуют в RAM.
     */
    public boolean isLoggedIn() {
        return jwtToken != null && currentUser != null;
    }

    /**
     * Мы формируем строку заголовка Authorization для HTTP и WebSocket запросов.
     * Пример: "Bearer eyJhbGciOiJIUzI1NiJ9..."
     */
    public String getBearerHeader() {
        return jwtToken != null ? "Bearer " + jwtToken : null;
    }

    /** Мы возвращаем WebSocket URL из HTTP URL, заменяя http(s) на ws(s). */
    public String getWebSocketUrl() {
        if (serverBaseUrl == null) return null;
        return serverBaseUrl
                .replace("https://", "wss://")
                .replace("http://",  "ws://")
                + "/ws";
    }

    public String getCurrentUser()   { return currentUser; }
    public String getJwtToken()      { return jwtToken; }
    public String getServerBaseUrl() { return serverBaseUrl; }
}
