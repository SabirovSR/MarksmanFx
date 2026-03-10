<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useLobbyStore } from '@/stores/lobby'
import { useAuthStore }  from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import { useWebSocketService } from '@/services/websocket'
import RoomList from '@/components/lobby/RoomList.vue'

const lobby = useLobbyStore()
const auth  = useAuthStore()
const toast = useToastStore()
const ws    = useWebSocketService()

// ── Состояние формы создания комнаты ─────────────────────────────────
const newRoomName  = ref('')
const showCreateForm = ref(false)

// ── Жизненный цикл ───────────────────────────────────────────────────

onMounted(async () => {
  try {
    // Мы подключаемся к WebSocket при открытии лобби
    await ws.connect()
    lobby.isLoadingRooms = true
    // Список комнат придёт через WebSocket подписку /topic/lobby
  } catch (e) {
    toast.error('Ошибка подключения', 'Не удалось подключиться к серверу')
  } finally {
    lobby.isLoadingRooms = false
  }
})

onUnmounted(() => {
  // Мы НЕ отключаем WebSocket при уходе в игру — соединение нужно там тоже
})

// ── Действия ─────────────────────────────────────────────────────────

async function handleCreateRoom() {
  if (!newRoomName.value.trim()) return
  await lobby.createRoom(newRoomName.value.trim())
  newRoomName.value   = ''
  showCreateForm.value = false
}

async function handleJoinRoom(roomId: string) {
  await lobby.joinRoom(roomId)
}

async function handleQuickMatch() {
  await lobby.quickMatch()
}

function handleLogout() {
  ws.disconnect()
  auth.logout()
}
</script>

<template>
  <div class="min-h-screen bg-dark-950 flex flex-col">

    <!-- ── Шапка ──────────────────────────────────────────────────── -->
    <header
      class="border-b border-dark-700 bg-dark-900/80 backdrop-blur-md sticky top-0 z-10"
    >
      <div class="max-w-5xl mx-auto px-6 py-4 flex items-center justify-between">
        <div class="flex items-center gap-3">
          <h1 class="text-gradient-cyan-purple font-game font-bold text-xl tracking-wider uppercase">
            Меткий стрелок
          </h1>
          <span class="badge-player animate-glow-pulse">Лобби</span>
        </div>

        <div class="flex items-center gap-4">
          <!-- Мы показываем имя текущего пользователя -->
          <div class="flex items-center gap-2 text-sm">
            <div class="w-2 h-2 rounded-full bg-accent-green animate-ping-slow" />
            <span class="text-slate-300 font-game font-semibold">
              {{ auth.username }}
            </span>
          </div>

          <button class="btn-ghost text-xs" @click="handleLogout">
            Выйти
          </button>
        </div>
      </div>
    </header>

    <!-- ── Основное содержимое ────────────────────────────────────── -->
    <main class="flex-1 max-w-5xl mx-auto w-full px-6 py-8">

      <!-- ── Панель действий ──────────────────────────────────────── -->
      <div class="flex flex-wrap items-center gap-3 mb-6">
        <h2 class="heading-lg flex-1">
          Игровые комнаты
          <span class="text-slate-500 text-sm font-normal font-sans ml-2">
            ({{ lobby.rooms.length }})
          </span>
        </h2>

        <!-- Кнопка быстрого матча с анимацией при загрузке -->
        <button
          class="btn-ghost relative"
          :disabled="lobby.isLoadingAction"
          @click="handleQuickMatch"
        >
          <svg v-if="lobby.isLoadingAction" class="animate-spin w-4 h-4" viewBox="0 0 24 24" fill="none">
            <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="3" stroke-dasharray="40"/>
          </svg>
          <span v-else>⚡</span>
          Быстрый матч
        </button>

        <button
          class="btn-primary"
          :disabled="lobby.isLoadingAction"
          @click="showCreateForm = !showCreateForm"
        >
          {{ showCreateForm ? '✕ Отмена' : '+ Создать комнату' }}
        </button>
      </div>

      <!-- ── Форма создания комнаты ───────────────────────────────── -->
      <Transition name="create-form">
        <div
          v-if="showCreateForm"
          class="card p-4 mb-4 border-primary/30 bg-primary/5"
        >
          <p class="heading-md mb-3">Новая комната</p>
          <form class="flex gap-3" @submit.prevent="handleCreateRoom">
            <input
              v-model="newRoomName"
              class="input-field flex-1"
              placeholder="Название комнаты..."
              maxlength="40"
              autofocus
            />
            <button
              type="submit"
              class="btn-primary"
              :disabled="!newRoomName.trim() || lobby.isLoadingAction"
            >
              Создать
            </button>
          </form>
        </div>
      </Transition>

      <!-- ── Таблица комнат с TransitionGroup ─────────────────────── -->
      <div class="card p-4">
        <RoomList @join="handleJoinRoom" />
      </div>
    </main>
  </div>
</template>

<style scoped>
/* Мы анимируем появление/скрытие формы создания комнаты */
.create-form-enter-active,
.create-form-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease, max-height 0.3s ease;
  max-height: 200px;
  overflow: hidden;
}
.create-form-enter-from,
.create-form-leave-to {
  opacity: 0;
  transform: translateY(-8px);
  max-height: 0;
}
</style>
