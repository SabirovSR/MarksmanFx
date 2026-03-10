package org.example.marksmanfx.server.auth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * JPA-сущность пользователя.
 *
 * Мы реализуем UserDetails напрямую в Entity — это удобный подход для
 * небольших проектов. При росте проекта UserDetails лучше вынести в
 * отдельный класс-адаптер (чтобы не смешивать слой персистентности и безопасности).
 *
 * Почему Instant для created_at?
 *   — Instant хранит UTC-момент времени без привязки к часовому поясу.
 *   — Это правило при работе с БД: всегда храним UTC, конвертируем на клиенте.
 *
 * telegram_chat_id — задел для 2FA через Telegram Bot.
 *   При реализации 2FA: после ввода пароля генерируем одноразовый код,
 *   отправляем через Telegram Bot API на этот chat_id и ждём подтверждения.
 */
@Entity
@Table(name = "app_users", // 'user' — зарезервированное слово в PostgreSQL
    uniqueConstraints = @UniqueConstraint(columnNames = "username"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppUser implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Имя пользователя — уникальный логин.
     * nullable=false + unique constraint обеспечивают целостность на уровне БД.
     */
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /**
     * Хэш пароля BCrypt.
     * Мы никогда не храним пароль в открытом виде.
     * BCrypt автоматически добавляет соль, поэтому одинаковые пароли дают разные хэши.
     */
    @Column(name = "password_hash", nullable = false)
    private String password;

    /**
     * Задел для 2FA через Telegram.
     * Пользователь привязывает аккаунт командой /link в боте — бот сохраняет chat_id сюда.
     * При логине с включённой 2FA сервер отправляет OTP через Telegram Bot API.
     */
    @Column(name = "telegram_chat_id")
    private Long telegramChatId;

    /**
     * Время регистрации. Устанавливается один раз перед первым сохранением.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    // ──────────────────────────────────────────────────────────────────────
    //  UserDetails interface — используется Spring Security
    // ──────────────────────────────────────────────────────────────────────

    /** Мы не используем роли для игрового сервера — все пользователи равны */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public boolean isAccountNonExpired()    { return true; }

    @Override
    public boolean isAccountNonLocked()     { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled()              { return true; }
}
