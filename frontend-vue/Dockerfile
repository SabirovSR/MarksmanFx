# ═══════════════════════════════════════════════════════════════════════════════
# Многоэтапная сборка для Vue.js фронтенда
#
# Этап 1 (builder): Node.js для сборки статических файлов (npm run build).
# Этап 2 (runtime): Nginx для раздачи статики — минимальный продакшен-образ.
#
# Nginx также выполняет роль reverse proxy: все /api/* и /ws/* запросы
# проксируются на backend контейнер.
# ═══════════════════════════════════════════════════════════════════════════════

# ───────────────────────────────────────────────────────────────────────────────
# Этап 1: Сборка Vue.js приложения
# ───────────────────────────────────────────────────────────────────────────────
FROM node:20-alpine AS builder

WORKDIR /app

# Мы копируем package.json и package-lock.json отдельно.
# Docker кэширует npm install пока эти файлы не изменились —
# при изменении только .vue файлов зависимости не устанавливаются заново.
COPY package*.json ./
RUN npm ci --silent

# Копируем исходники и собираем продакшен-бандл
COPY . .

# Мы используем переменную окружения для URL API и WebSocket.
# Vite подставит их в бандл при сборке (import.meta.env.VITE_API_URL).
ARG VITE_API_URL=http://localhost:8080
ARG VITE_WS_URL=ws://localhost:8080/ws
ENV VITE_API_URL=$VITE_API_URL
ENV VITE_WS_URL=$VITE_WS_URL

RUN npm run build

# ───────────────────────────────────────────────────────────────────────────────
# Этап 2: Nginx для раздачи статики
# ───────────────────────────────────────────────────────────────────────────────
FROM nginx:1.25-alpine AS runtime

# Удаляем дефолтный конфиг Nginx
RUN rm /etc/nginx/conf.d/default.conf

# Копируем наш конфиг с поддержкой Vue Router (History API) и проксированием
COPY nginx.conf /etc/nginx/conf.d/marksmanfx.conf

# Копируем собранные статические файлы из предыдущего этапа
COPY --from=builder /app/dist /usr/share/nginx/html

# Непривилегированный пользователь nginx (встроен в образ)
USER nginx

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
