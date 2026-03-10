<script setup lang="ts">
import { onMounted, onUnmounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useGameStore }        from '@/stores/game'
import { useLobbyStore }       from '@/stores/lobby'
import { useAuthStore }        from '@/stores/auth'
import { useWebSocketService } from '@/services/websocket'
import GameCanvas              from '@/components/game/GameCanvas.vue'
import CountdownOverlay        from '@/components/game/CountdownOverlay.vue'

const route  = useRoute()
const router = useRouter()
const game   = useGameStore()
const lobby  = useLobbyStore()
const auth   = useAuthStore()
const ws     = useWebSocketService()

const roomId = computed(() => route.params.roomId as string)

// ── Жизненный цикл ───────────────────────────────────────────────────

onMounted(() => {
  // Мы подписываемся на события конкретной комнаты
  ws.subscribeToRoom(roomId.value)
})

onUnmounted(() => {
  // Мы сбрасываем игровое состояние при выходе из комнаты
  game.reset()
})

// ── Действия ─────────────────────────────────────────────────────────

/** Мы обрабатываем нажатие мыши на канвас — отправляем выстрел */
function handleCanvasClick(e: MouseEvent) {
  if (!game.isPlaying) return

  const canvas = e.target as HTMLCanvasElement
  const rect   = canvas.getBoundingClientRect()
  const aimX   = e.clientX - rect.left
  const aimY   = e.clientY - rect.top
  const charge = 0.75 // TODO: реализовать удержание кнопки для заряда

  ws.sendShot(roomId.value, aimX, aimY, charge)
}

/** Мы обрабатываем запрос зрителя стать игроком */
function requestBePlayer() {
  ws.requestUpgradeToPlayer(roomId.value)
}

function leaveGame() {
  lobby.leaveRoom()
  router.push({ name: 'lobby' })
}

// ── Вычисляемые ─────────────────────────────────────────────────────
const currentRoom   = computed(() => lobby.currentRoom)
const isPlayer      = computed(() => lobby.isCurrentUserPlayer)
const participantCount = computed(() =>
  (currentRoom.value?.participants.length ?? 0),
)
</script>

<template>
  <div class="min-h-screen bg-dark-950 flex flex-col">

    <!-- ── HUD-шапка ──────────────────────────────────────────────── -->
    <header
      class="h-12 border-b border-dark-700 bg-dark-900/90 backdrop-blur-md
             flex items-center justify-between px-4 shrink-0"
    >
      <!-- Название комнаты -->
      <div class="flex items-center gap-3">
        <span class="font-game font-bold text-accent-cyan text-sm tracking-wider">
          {{ currentRoom?.roomName ?? '...' }}
        </span>
        <span class="text-slate-600 text-xs">·</span>
        <span class="text-slate-400 text-xs font-mono">
          {{ participantCount }} участников
        </span>
      </div>

      <!-- Счёт и FPS -->
      <div class="flex items-center gap-4">
        <span class="text-slate-600 font-mono text-xs">
          {{ game.fps }} fps
        </span>
        <button class="btn-danger text-xs py-1.5" @click="leaveGame">
          Покинуть
        </button>
      </div>
    </header>

    <!-- ── Основное игровое поле ──────────────────────────────────── -->
    <main class="flex-1 relative overflow-hidden">
      <!-- Canvas занимает всё доступное пространство -->
      <GameCanvas
        :room-id="roomId"
        class="absolute inset-0"
        @click="handleCanvasClick"
      />

      <!-- Мы накладываем обратный отсчёт поверх Canvas -->
      <CountdownOverlay />

      <!-- ── Оверлей экрана победителя ──────────────────────────── -->
      <Transition name="page">
        <div
          v-if="game.isFinished"
          class="absolute inset-0 flex items-center justify-center bg-black/60 backdrop-blur-sm z-30"
        >
          <div class="card p-10 text-center animate-slide-up max-w-sm w-full">
            <div class="text-5xl mb-4">🏆</div>
            <h2 class="heading-xl text-gradient-gold mb-2">Победитель!</h2>
            <p class="text-accent-gold font-game font-semibold text-xl mb-6">
              {{ game.winner }}
            </p>
            <div class="flex gap-3 justify-center">
              <button class="btn-primary" @click="ws.setReady(roomId, true)">
                Реванш
              </button>
              <button class="btn-ghost" @click="leaveGame">
                В лобби
              </button>
            </div>
          </div>
        </div>
      </Transition>

      <!-- ── Оверлей паузы ──────────────────────────────────────── -->
      <Transition name="page">
        <div
          v-if="game.isPaused"
          class="absolute inset-0 flex items-center justify-center bg-black/50 backdrop-blur-sm z-30"
        >
          <div class="card p-8 text-center animate-slide-up">
            <p class="heading-lg text-accent-gold mb-2">⏸ Пауза</p>
            <p class="text-slate-400 text-sm">Ожидаем согласования игроков...</p>
          </div>
        </div>
      </Transition>

      <!-- ── Панель зрителя ────────────────────────────────────── -->
      <Transition name="page">
        <div
          v-if="!isPlayer"
          class="absolute bottom-4 left-1/2 -translate-x-1/2 z-20"
        >
          <div
            class="flex items-center gap-4 bg-dark-900/90 backdrop-blur-md
                   border border-accent-gold/30 rounded-xl px-5 py-3 shadow-neon-gold"
          >
            <span class="badge-spectator">Зритель</span>
            <p class="text-slate-300 text-sm">
              Хотите сыграть?
            </p>
            <button
              class="btn-cyan text-sm"
              @click="requestBePlayer"
            >
              Стать игроком
            </button>
          </div>
        </div>
      </Transition>
    </main>
  </div>
</template>
