package org.example.marksmanfx.server.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
    //  500 Internal Server Error — запасной обработчик
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Мы перехватываем все незапланированные исключения, которые не были обработаны
     * в конкретных контроллерах. Это гарантирует, что клиент никогда не получит
     * стандартный Spring HTML-стек трейс в теле ответа.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        // Мы логируем полный stacktrace только на сервере — клиенту показываем общую фразу
        log.error("[GLOBAL] Необработанное исключение: {}", ex.getMessage(), ex);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "Внутренняя ошибка сервера");
        body.put("errors",  Map.of());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
