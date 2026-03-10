package org.example.marksmanfx.server.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Глобальный обработчик исключений для всех @RestController.
 *
 * Мы используем @RestControllerAdvice вместо try/catch в каждом контроллере:
 *   — Один класс — одна ответственность (DRY).
 *   — Формат ошибок одинаков для всего API — фронтенд парсит единообразно.
 *   — Контроллеры остаются чистыми от инфраструктурного кода.
 *
 * Без этого класса Spring Boot возвращает при ошибке валидации:
 *   — Стандартный /error redirect (HTML или большой JSON с техническими деталями)
 *   — Клиент не может понять, какое именно поле неверно
 *
 * С этим классом клиент получает:
 *   {
 *     "message": "Ошибка валидации",
 *     "errors": {
 *       "password": "Пароль должен быть от 6 до 128 символов",
 *       "username": "Имя пользователя не может быть пустым"
 *     }
 *   }
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ──────────────────────────────────────────────────────────────────────
    //  400 Bad Request — ошибки Bean Validation (@Valid на теле запроса)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Мы перехватываем MethodArgumentNotValidException, которое Spring бросает
     * когда @Valid не проходит на @RequestBody параметре контроллера.
     *
     * Алгоритм:
     *   1. Извлекаем все FieldError из BindingResult.
     *   2. Собираем Map: имя поля → сообщение об ошибке.
     *   3. putIfAbsent — если одно поле нарушает несколько правил (@NotBlank + @Size),
     *      показываем только первое сообщение (наиболее конкретное).
     *   4. Возвращаем 400 Bad Request с читаемым JSON.
     *
     * @param ex  исключение с результатами валидации всех полей DTO
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        // Мы собираем ошибки всех полей в упорядоченную Map
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            // Мы используем putIfAbsent: при нескольких нарушениях одного поля
            // оставляем первое сообщение — оно обычно самое понятное пользователю
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }

        // Мы логируем только имена полей с ошибками, не данные пользователя
        log.debug("[VALIDATION] Ошибки валидации для полей: {}", fieldErrors.keySet());

        // Мы формируем тело ответа с предсказуемой структурой для фронтенда
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "Ошибка валидации");
        body.put("errors",  fieldErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // ──────────────────────────────────────────────────────────────────────
    //  404 Not Found — маршрут не существует
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Мы перехватываем NoResourceFoundException — это обычный 404, не ошибка сервера.
     *
     * Без этого хэндлера исключение доходило до запасного @ExceptionHandler(Exception.class),
     * который логировал его как ERROR со стектрейсом и отвечал 500.
     * Docker healthcheck (curl /actuator/health) до добавления actuator-зависимости
     * генерировал этот exception при каждой проверке — отсюда спам в логах.
     *
     * Теперь:  404 тихо логируется на уровне DEBUG и возвращает корректный HTTP 404.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException ex) {
        // Мы не логируем это как ERROR — это штатный сценарий, не баг сервера
        log.debug("[404] Ресурс не найден: {}", ex.getMessage());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "Ресурс не найден: " + ex.getResourcePath());
        body.put("errors",  Map.of());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    // ──────────────────────────────────────────────────────────────────────
    //  500 Internal Server Error — запасной обработчик
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Мы перехватываем все незапланированные исключения.
     * Клиент никогда не получит стандартный Spring HTML-стектрейс в теле ответа.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        log.error("[GLOBAL] Необработанное исключение: {}", ex.getMessage(), ex);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "Внутренняя ошибка сервера");
        body.put("errors",  Map.of());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
