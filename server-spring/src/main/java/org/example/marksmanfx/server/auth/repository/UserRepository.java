package org.example.marksmanfx.server.auth.repository;

import org.example.marksmanfx.server.auth.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Репозиторий для работы с пользователями в PostgreSQL.
 *
 * Мы используем Spring Data JPA: достаточно объявить интерфейс,
 * Spring автоматически генерирует реализацию с SQL-запросами.
 * findByUsername генерирует: SELECT * FROM app_users WHERE username = ?
 */
@Repository
public interface UserRepository extends JpaRepository<AppUser, Long> {

    /** Поиск пользователя по имени (для логина и проверки при регистрации) */
    Optional<AppUser> findByUsername(String username);

    /** Проверка уникальности имени при регистрации */
    boolean existsByUsername(String username);
}
