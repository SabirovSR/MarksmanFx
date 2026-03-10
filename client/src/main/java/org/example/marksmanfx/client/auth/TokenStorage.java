package org.example.marksmanfx.client.auth;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Постоянное хранилище JWT-токена на устройстве пользователя.
 *
 * Мы используем {@link java.util.prefs.Preferences} — стандартный Java API,
 * который на каждой платформе сохраняет данные в нативное хранилище:
 *   — Windows: реестр (HKEY_CURRENT_USER\Software\JavaSoft\Prefs)
 *   — macOS:   ~/Library/Preferences/<bundle>.plist
 *   — Linux:   ~/.java/.userPrefs/
 *
 * Почему Preferences, а не файл?
 *   — Не нужно управлять путями и правами доступа вручную.
 *   — Данные изолированы по пользователю ОС (userNodeForPackage).
 *   — API атомарный: put/remove не ломают данные при краше.
 *
 * Класс намеренно оставлен утилитным (статические методы, приватный конструктор),
 * поскольку Preferences — singleton по природе.
 */
public final class TokenStorage {

    private static final Logger    LOG  = Logger.getLogger(TokenStorage.class.getName());
    private static final Preferences PREFS = Preferences.userNodeForPackage(TokenStorage.class);

    /** Ключи хранилища */
    private static final String KEY_TOKEN      = "jwt_token";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_USERNAME   = "last_username";

    // Запрещаем создание экземпляров — это утилитный класс
    private TokenStorage() {}

    // ──────────────────────────────────────────────────────────────────────
    //  JWT-токен
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Мы сохраняем JWT-токен в системное хранилище пользователя.
     * Вызывается при успешном логине с флагом «Запомнить меня».
     *
     * @param token строка JWT в формате header.payload.signature
     */
    public static void saveToken(String token) {
        PREFS.put(KEY_TOKEN, token);
        flush();
        LOG.fine("[TokenStorage] JWT-токен сохранён");
    }

    /**
     * Мы читаем сохранённый JWT-токен.
     * Возвращаем Optional.empty() если токена нет (не логинился, либо clearToken был вызван).
     *
     * @return Optional со строкой токена, либо empty
     */
    public static Optional<String> getToken() {
        String token = PREFS.get(KEY_TOKEN, null);
        return Optional.ofNullable(token);
    }

    /**
     * Мы удаляем сохранённый токен — полный выход из системы.
     * Вызывается при явном логауте или при получении 401 с сервера.
     */
    public static void clearToken() {
        PREFS.remove(KEY_TOKEN);
        flush();
        LOG.fine("[TokenStorage] JWT-токен удалён");
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Адрес сервера (сохраняем, чтобы при автологине не спрашивать снова)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Мы сохраняем базовый URL сервера, чтобы при следующем запуске
     * не показывать пользователю форму ввода адреса.
     * Пример: "http://localhost:8080"
     */
    public static void saveServerUrl(String url) {
        PREFS.put(KEY_SERVER_URL, url);
        flush();
    }

    /**
     * Мы читаем последний использованный адрес сервера.
     * По умолчанию — localhost для разработки.
     */
    public static String getServerUrl() {
        return PREFS.get(KEY_SERVER_URL, "http://localhost:8080");
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Последний логин (для prefill поля username)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Мы сохраняем имя последнего вошедшего пользователя.
     * Используем для предзаполнения поля логина на экране входа.
     */
    public static void saveLastUsername(String username) {
        PREFS.put(KEY_USERNAME, username);
        flush();
    }

    public static String getLastUsername() {
        return PREFS.get(KEY_USERNAME, "");
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Полная очистка всех данных сессии
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Мы очищаем все сохранённые данные сессии разом.
     * Используется при логауте, если пользователь хочет полностью «выйти».
     */
    public static void clearAll() {
        try {
            PREFS.clear();
            flush();
            LOG.info("[TokenStorage] Все данные сессии очищены");
        } catch (BackingStoreException e) {
            LOG.log(Level.WARNING, "[TokenStorage] Не удалось очистить Preferences", e);
        }
    }

    // ──────────────────────────────────────────────────────────────────────

    /**
     * Мы принудительно сбрасываем данные в постоянное хранилище.
     * Preferences может буферизировать запись — flush() гарантирует немедленное сохранение.
     */
    private static void flush() {
        try {
            PREFS.flush();
        } catch (BackingStoreException e) {
            LOG.log(Level.WARNING, "[TokenStorage] Ошибка записи Preferences на диск", e);
        }
    }
}
