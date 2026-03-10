package org.example.marksmanfx.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Точка входа Spring Boot приложения.
 *
 * Мы заменяем старый ServerApp с ручным ServerSocket на Spring Boot,
 * который автоматически поднимает embedded Tomcat, регистрирует все
 * бины из контекста и инициализирует WebSocket-эндпоинт.
 */
@SpringBootApplication
public class ServerSpringApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServerSpringApplication.class, args);
    }
}
