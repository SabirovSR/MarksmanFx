# ⚔ Меткий стрелок — Multiplayer

Многопользовательская клиент-серверная игра на JavaFX (до 4 игроков).

---

## Архитектура проекта

```
MarksmanFx/               ← Parent POM (packaging=pom)
├── common/               ← Shared library: DTO, события, сообщения
├── server/               ← Сервер (pure Java, никакого UI)
└── client/               ← JavaFX-клиент
```

### Принцип работы

```
Client ──[ClientEvent]──► Server ──[ServerMessage]──► Client(s)
         (TCP socket)              (broadcast)
```

Сервер — **авторитетный**: он полностью контролирует физику игры
(движение мишеней, полёт стрел, коллизии, счёт).
Клиент отправляет только «намерения» (нажатые клавиши) и занимается
исключительно отрисовкой полученного снимка состояния.

---

## Структура пакетов

### `common` — общая библиотека

| Пакет | Содержимое |
|---|---|
| `common.event` | `ClientEvent` (sealed) + 11 реализаций-record |
| `common.message` | `ServerMessage` (sealed) + 10 реализаций-record |
| `common.model` | `GamePhase`, `RoomInfo`, `PlayerInfo`, `PlayerStateDto`, `ArrowDto`, `TargetDto` |

**Ключевые DTO:**

```
GameStateMessage              — 60 Гц, вся сцена целиком
  ├── List<PlayerStateDto>    — позиция/угол/счёт каждого игрока
  ├── List<ArrowDto>          — полёт каждой стрелы
  ├── TargetDto near/far      — позиция мишеней
  └── GamePhase phase         — PLAYING / PAUSED / ...
```

### `server` — игровой сервер

| Пакет | Класс | Роль |
|---|---|---|
| `server` | `ServerApp` | Точка входа |
| `server.network` | `GameServer` | `ServerSocket`, пул потоков |
| `server.network` | `ClientHandler` | Один поток на клиента; читает `ClientEvent`, пишет `ServerMessage` |
| `server.lobby` | `LobbyManager` | Создание/поиск комнат, быстрый матч, рассылка `LobbyStateMessage` |
| `server.game` | `GameRoom` | Хранит список игроков, делегирует к `RoomState` |
| `server.game` | `ServerGameSession` | `ScheduledExecutorService` @60 TPS, физика, коллизии, рассылка |
| `server.game` | `ServerPlayerState` | Авторитетное состояние игрока |
| `server.game` | `ServerArrowState` | Авторитетное состояние стрелы |
| `server.game` | `ServerTargetState` | Авторитетное состояние мишени |
| `server.state` | `RoomState` (interface) | **Паттерн Состояние** — жизненный цикл комнаты |
| `server.state` | `WaitingState` | Ожидание готовности всех игроков |
| `server.state` | `PlayingState` | Матч идёт |
| `server.state` | `PauseRequestedState` | Ожидание подтверждения паузы от всех |
| `server.state` | `PausedState` | Пауза; ждёт «Готов» для возобновления |
| `server.state` | `FinishedState` | Матч окончен; ждёт «Играть снова» |

### `client` — JavaFX-клиент (MVC)

| Слой | Класс | Роль |
|---|---|---|
| Entry | `ClientApp` | `Application.start()`, создаёт `SceneManager` |
| Network | `ServerConnection` | Фоновый поток чтения; `Platform.runLater` для UI |
| UI | `SceneManager` | Переключение сцен: Login → Lobby → Game |
| Login | `LoginController` | Ввод никнейма и адреса сервера |
| Lobby | `LobbyController` | Список комнат (`TableView`), кнопки Create/Join/Quick |
| Game | `GameController` | Клавиатурный ввод → `ClientEvent`; `AnimationTimer` |
| Game | `GameRenderer` | Отрисовка на `Canvas` из `GameStateMessage` |

---

## Игровые события (Client → Server)

| Событие | Когда |
|---|---|
| `JoinLobbyEvent(nickname)` | После TCP-подключения |
| `CreateRoomEvent(name)` | Кнопка «Создать комнату» |
| `JoinRoomEvent(roomId)` | Кнопка «Войти» |
| `QuickMatchEvent()` | Кнопка «Быстрый матч» |
| `PlayerReadyEvent(ready)` | Клавиша R / кнопка «Готов» |
| `MoveEvent(dir, pressed)` | W/A/S/D — нажатие/отпускание |
| `AimEvent(dir, pressed)` | Q/E — прицеливание |
| `CrouchEvent(crouching)` | C — присед |
| `FireArrowEvent(chargeRatio)` | Пробел (отпускание) |
| `PauseRequestEvent(pausing)` | P — запрос/отмена паузы |
| `LeaveRoomEvent()` | Кнопка «Выйти» |

## Сообщения (Server → Client)

| Сообщение | Когда |
|---|---|
| `ConnectedMessage` | Ответ на `JoinLobbyEvent` |
| `LobbyStateMessage` | Список комнат (при любом изменении) |
| `RoomJoinedMessage` | Клиент вошёл в комнату |
| `RoomUpdatedMessage` | Состав комнаты / готовность изменились |
| `GameStartMessage` | Все готовы — матч начался |
| `GameStateMessage` | ~60 Гц — авторитетный снимок сцены |
| `GameOverMessage` | Победитель определён |
| `PauseStateMessage` | Смена фазы паузы |
| `PlayerDisconnectedMessage` | Игрок потерял соединение |
| `ErrorMessage` | Сервер сообщает об ошибке |

---

## Жизненный цикл комнаты (State pattern)

```
WAITING ──(все нажали Ready)──► PLAYING
PLAYING ──(один нажал P)──────► PAUSE_REQUESTED
PAUSE_REQUESTED ──(все нажали P)──► PAUSED
PAUSE_REQUESTED ──(инициатор нажал P ещё раз)──► PLAYING
PAUSED ──(все нажали Ready)──► PLAYING
PLAYING / PAUSED ──(кто-то набрал 6 очков)──► FINISHED
FINISHED ──(все нажали Ready)──► WAITING
```

---

## Сборка и запуск

### Требования

- Java 21+
- Maven (или `./mvnw`)

### Сборка

```bash
./mvnw clean install -DskipTests
```

### Запуск сервера

```bash
java -jar server/target/server-1.0-SNAPSHOT-fat.jar [port]
# Default port: 55555
```

### Запуск клиента

```bash
cd client && ../mvnw javafx:run
```

---

## Управление (клиент)

| Клавиша | Действие |
|---|---|
| W / ↑ | Вверх |
| S / ↓ | Вниз |
| A | Влево |
| D | Вправо |
| Q / ← | Прицелиться вверх |
| E / → | Прицелиться вниз |
| C | Присед |
| Пробел | Зарядить и выстрелить |
| P | Запросить паузу |
| R | Готов / Играть снова |

---

## Визуальные обозначения

| Элемент | Отображение |
|---|---|
| Локальный игрок | Полная непрозрачность, белый никнейм |
| Соперники | Тень (40% прозрачность), красный никнейм |
| Стрелы соперников | Красный оттенок, 45% прозрачность |
| Шкала заряда | Полоса внизу поля (зелёный→жёлтый→красный) |
| Ближняя мишень (+1) | Красно-белые кольца |
| Дальняя мишень (+2) | Жёлто-оранжевые кольца |
