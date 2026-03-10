# ═══════════════════════════════════════════════════════════════════════════════
# Многоэтапная сборка (Multi-stage build) для Spring Boot бэкенда
#
# Этап 1 (builder): Используем полный JDK + Maven для компиляции и сборки JAR.
# Этап 2 (runtime): Используем минимальный JRE Eclipse Temurin для запуска.
#
# Зачем Multi-stage?
#   — Финальный образ содержит только JRE + JAR (~200MB) вместо JDK + Maven (~600MB).
#   — Исходный код и артефакты сборки не попадают в prod-образ.
# ═══════════════════════════════════════════════════════════════════════════════

# ───────────────────────────────────────────────────────────────────────────────
# Этап 1: Сборка JAR через Maven
# ───────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

# Устанавливаем рабочую директорию
WORKDIR /build

# Мы копируем только pom.xml сначала и загружаем зависимости отдельным слоем.
# Docker кэширует этот слой — при изменении только исходников зависимости
# не будут скачиваться заново (экономия 2-5 минут на CI/CD).
COPY pom.xml .

# Скачиваем зависимости (используем offline-friendly goal)
RUN mvn dependency:go-offline -B --no-transfer-progress 2>/dev/null || \
    (apk add --no-cache curl && \
     curl -fsSL https://dlcdn.apache.org/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.tar.gz \
     | tar -xz -C /opt && \
     ln -s /opt/apache-maven-3.9.6/bin/mvn /usr/local/bin/mvn && \
     mvn dependency:go-offline -B --no-transfer-progress)

# Теперь копируем исходники
COPY src ./src

# Собираем fat-jar, пропускаем тесты для ускорения сборки Docker-образа
# Тесты должны проходить в CI/CD пайплайне до сборки образа
RUN mvn package -DskipTests -B --no-transfer-progress

# ───────────────────────────────────────────────────────────────────────────────
# Этап 2: Минимальный runtime образ
# ───────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Создаём непривилегированного пользователя — best practice для безопасности.
# Приложение не должно работать от root в контейнере.
RUN addgroup -S marksmanfx && adduser -S marksmanfx -G marksmanfx

WORKDIR /app

# Копируем только JAR из предыдущего этапа
COPY --from=builder /build/target/marksmanfx-server.jar app.jar

# Устанавливаем curl для healthcheck в docker-compose
RUN apk add --no-cache curl

# Переключаемся на непривилегированного пользователя
USER marksmanfx

# Порт приложения
EXPOSE 8080

# Настройки JVM для контейнерной среды:
#   -XX:+UseContainerSupport        — JVM учитывает лимиты памяти контейнера (не хоста)
#   -XX:MaxRAMPercentage=75.0       — используем 75% от доступной памяти контейнера
#   -XX:+ExitOnOutOfMemoryError     — краш вместо зависания при OOM (Docker перезапустит)
#   -Djava.security.egd             — ускоряем генерацию случайных чисел (важно для JWT)
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
