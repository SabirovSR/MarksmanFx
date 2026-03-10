# ═══════════════════════════════════════════════════════════════════════════════
# Multi-stage build для Spring Boot бэкенда
#
# Почему была ошибка 404 + "invalid magic"?
# ──────────────────────────────────────────
# Прежний Dockerfile использовал образ eclipse-temurin:21-jdk-alpine, в котором
# Maven не предустановлен. В качестве обходного пути Maven скачивался вручную
# через curl с dlcdn.apache.org. Apache периодически перемещает старые версии
# в архив (archive.apache.org), и прямые ссылки начинают возвращать 404.
# curl с флагом -fsSL при 404 получает HTML-страницу ошибки вместо архива,
# tar пытается распаковать HTML и падает с "invalid magic" — заголовок не совпадает.
#
# Как мы это исправляем:
# ──────────────────────
# Мы используем официальный образ maven:3.9-eclipse-temurin-21, в котором
# Maven уже установлен командой сопровождающих образа. Docker Hub хранит
# образ в своём реестре навсегда — загрузка происходит из Docker Hub, а не
# с сайта Apache. 404 с внешних CDN физически невозможна.
# ═══════════════════════════════════════════════════════════════════════════════


# ───────────────────────────────────────────────────────────────────────────────
# build-stage: Maven + JDK для компиляции и сборки fat-JAR
# ───────────────────────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build-stage

# Мы работаем в /build — отдельно от системных директорий, легко различимо в логах
WORKDIR /build

# Мы копируем ТОЛЬКО pom.xml первым слоем.
# Docker кэширует результат mvn dependency:go-offline пока pom.xml не изменился.
# При изменении только .java-файлов зависимости НЕ скачиваются заново —
# экономия 2–4 минуты на каждой пересборке в CI/CD.
COPY pom.xml .

# Мы предзагружаем все зависимости в локальный репозиторий Maven внутри образа.
# -B              — batch mode: без интерактивных запросов и ANSI-цветов (чище в логах)
# --no-transfer-progress — убираем прогресс-бары загрузки (логи читаемее в CI)
RUN mvn dependency:go-offline -B --no-transfer-progress

# Мы копируем исходники ПОСЛЕ загрузки зависимостей.
# Изменение любого .java-файла инвалидирует только этот слой и следующий (package),
# но НЕ слой с зависимостями — это ключевой паттерн кэширования Docker-сборок.
COPY src ./src

# Мы собираем fat-JAR (executable jar со всеми зависимостями внутри).
# -DskipTests  — тесты запускаются в CI-пайплайне ДО сборки образа, не здесь.
# Spring Boot Maven Plugin упакует всё в target/marksmanfx-server.jar
# (имя задано через <finalName> в pom.xml).
RUN mvn clean package -DskipTests -B --no-transfer-progress


# ───────────────────────────────────────────────────────────────────────────────
# runtime-stage: минимальный JRE для запуска собранного JAR
# ───────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Мы создаём системного пользователя и группу без домашней директории и без shell.
# Приложение не должно работать от root: если уязвимость позволит выйти из JAR,
# злоумышленник попадёт в контейнер с минимальными привилегиями.
RUN addgroup -S marksmanfx \
 && adduser  -S marksmanfx -G marksmanfx

WORKDIR /app

# Мы устанавливаем curl только в runtime-образ — он нужен для healthcheck
# в docker-compose (CMD ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health"]).
# В build-stage curl не нужен: maven:* содержит wget и иные инструменты.
RUN apk add --no-cache curl

# Мы копируем ТОЛЬКО JAR из build-stage.
# В runtime-образ не попадают: исходники, pom.xml, node_modules Maven (~300 MB),
# JDK (~200 MB). Итоговый образ: ~200 MB вместо ~700 MB.
COPY --from=build-stage /build/target/marksmanfx-server.jar app.jar

# Мы передаём владение файлом нашему непривилегированному пользователю.
# Без этого root-принадлежность файла конфликтует с USER-директивой ниже.
RUN chown marksmanfx:marksmanfx app.jar

# Мы переключаемся на непривилегированного пользователя для всех последующих
# инструкций (включая ENTRYPOINT при запуске контейнера).
USER marksmanfx

EXPOSE 8080

# Настройки JVM для контейнерной среды:
#
#   UseContainerSupport    — JVM читает cgroup-лимиты контейнера, а не параметры хоста.
#                            Без этого JVM думает, что у неё весь RAM хоста (например, 32 ГБ)
#                            и выделяет heap под него → OOM от Docker.
#
#   MaxRAMPercentage=75.0  — максимальный heap = 75% от лимита контейнера.
#                            Оставляем 25% на: Metaspace, стек потоков, off-heap буферы Netty.
#
#   ExitOnOutOfMemoryError — краш вместо зависшего процесса при OutOfMemoryError.
#                            Docker перезапустит контейнер (restart: unless-stopped).
#
#   security.egd           — /dev/urandom вместо /dev/random для генерации seed энтропии.
#                            На виртуальных машинах /dev/random может блокироваться на секунды,
#                            что критично при инициализации JWT-ключей при старте.
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
