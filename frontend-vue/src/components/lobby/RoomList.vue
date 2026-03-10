<script setup lang="ts">
import { computed } from 'vue'
import { useLobbyStore } from '@/stores/lobby'
import type { RoomInfo, GamePhase } from '@/types'

const emit = defineEmits<{
  join: [roomId: string]
}>()

const lobby = useLobbyStore()

// Мы вычисляем текстовые метки и цвета для каждой фазы комнаты
function phaseLabel(phase: GamePhase): string {
  const map: Record<GamePhase, string> = {
    LOBBY:           'В лобби',
    PLAYING:         'Идёт игра',
    PAUSE_REQUESTED: 'Пауза...',
    PAUSED:          'На паузе',
    FINISHED:        'Завершена',
  }
  return map[phase] ?? phase
}

function phaseClass(phase: GamePhase): string {
  const map: Record<GamePhase, string> = {
    LOBBY:           'badge-waiting',
    PLAYING:         'badge-player',
    PAUSE_REQUESTED: 'badge-spectator',
    PAUSED:          'badge-spectator',
    FINISHED:        'badge-spectator',
  }
  return map[phase] ?? 'badge-spectator'
}

/** Мы вычисляем, можно ли войти в комнату (есть слоты и она не закончила игру) */
function canJoin(room: RoomInfo): boolean {
  return room.playerCount < room.maxPlayers && room.phase !== 'FINISHED'
}

/** Мы отображаем индикатор заполненности слотов игроков */
function slotsText(room: RoomInfo): string {
  return `${room.playerCount} / ${room.maxPlayers}`
}
</script>

<template>
  <div class="space-y-2">

    <!-- ── Шапка таблицы ─────────────────────────────────── -->
    <div class="grid grid-cols-[1fr_auto_auto_auto] items-center gap-4 px-4 py-2">
      <span class="heading-md text-slate-500 text-xs uppercase tracking-widest">Комната</span>
      <span class="heading-md text-slate-500 text-xs uppercase tracking-widest w-20 text-center">Игроки</span>
      <span class="heading-md text-slate-500 text-xs uppercase tracking-widest w-24 text-center">Статус</span>
      <span class="w-20"></span>
    </div>

    <!--
      Мы используем TransitionGroup для анимации добавления и удаления комнат.

      TransitionGroup vs Transition:
        — Transition: для одного элемента (показать/скрыть)
        — TransitionGroup: для списка — каждый элемент получает
          enter/leave анимацию, а остальные плавно сдвигаются (move)

      Когда появляется новая комната (кто-то создал) — строка выезжает справа.
      Когда комната исчезает (все вышли) — строка уходит влево.
      Остальные строки плавно смещаются на освободившееся место.
    -->
    <TransitionGroup
      name="room-list"
      tag="div"
      class="space-y-2"
    >
      <div
        v-for="room in lobby.rooms"
        :key="room.roomId"
        class="room-row group"
        :class="{ 'opacity-60 cursor-default': !canJoin(room) }"
        @click="canJoin(room) && emit('join', room.roomId)"
        role="button"
        :tabindex="canJoin(room) ? 0 : -1"
        :aria-label="`Войти в комнату ${room.roomName}`"
      >
        <!-- Название комнаты -->
        <div class="min-w-0">
          <p class="font-game font-semibold text-white truncate group-hover:text-accent-cyan transition-colors">
            {{ room.roomName }}
          </p>
          <p class="text-slate-500 text-xs font-mono">
            #{{ room.roomId }}
          </p>
        </div>

        <!-- Количество игроков / мест -->
        <div class="flex items-center gap-1.5 w-20 justify-center">
          <!-- Мы отображаем слоты как маленькие квадраты -->
          <div class="flex gap-1">
            <div
              v-for="i in room.maxPlayers"
              :key="i"
              :class="[
                'w-2.5 h-2.5 rounded-sm transition-colors',
                i <= room.playerCount
                  ? 'bg-accent-cyan'
                  : 'bg-dark-600 border border-dark-500',
              ]"
            />
          </div>
          <span class="text-slate-400 text-xs font-mono">
            {{ slotsText(room) }}
          </span>
        </div>

        <!-- Статус-бейдж фазы -->
        <div class="w-24 flex justify-center">
          <span :class="phaseClass(room.phase)">
            {{ phaseLabel(room.phase) }}
          </span>
        </div>

        <!-- Кнопка входа -->
        <div class="w-20 flex justify-end">
          <button
            v-if="canJoin(room)"
            class="btn-cyan text-xs px-3 py-1.5 opacity-0 group-hover:opacity-100 transition-opacity"
            @click.stop="emit('join', room.roomId)"
          >
            Войти
          </button>
          <span
            v-else-if="room.playerCount >= room.maxPlayers"
            class="text-slate-500 text-xs"
          >
            Полная
          </span>
          <span
            v-else
            class="text-slate-500 text-xs"
          >
            Зритель
          </span>
        </div>
      </div>
    </TransitionGroup>

    <!-- Мы показываем заглушку если комнат ещё нет -->
    <Transition name="fade">
      <div
        v-if="lobby.rooms.length === 0 && !lobby.isLoadingRooms"
        class="flex flex-col items-center gap-3 py-16 text-center animate-fade-in"
      >
        <div class="text-4xl opacity-30">🏹</div>
        <p class="text-slate-500 text-sm">
          Пока нет активных комнат
        </p>
        <p class="text-slate-600 text-xs">
          Создайте первую!
        </p>
      </div>
    </Transition>

    <!-- Мы показываем скелетон-строки пока идёт загрузка -->
    <template v-if="lobby.isLoadingRooms">
      <div
        v-for="i in 3"
        :key="`skeleton-${i}`"
        class="room-row animate-pulse"
      >
        <div class="h-4 bg-dark-700 rounded w-48 shimmer" />
        <div class="h-4 bg-dark-700 rounded w-16 shimmer" />
        <div class="h-4 bg-dark-700 rounded w-20 shimmer" />
        <div class="h-7 bg-dark-700 rounded w-16 shimmer" />
      </div>
    </template>
  </div>
</template>

<style scoped>
/*
  Мы определяем анимации для TransitionGroup строк таблицы.

  enter-active / leave-active: продолжительность анимации
  enter-from:   начальное состояние при появлении (справа + прозрачный)
  leave-to:     конечное состояние при исчезновении (влево + прозрачный)
  move:         плавный сдвиг оставшихся строк
*/
.room-list-enter-active {
  transition: opacity 0.35s ease-out, transform 0.35s cubic-bezier(0.21, 1.02, 0.73, 1);
}
.room-list-leave-active {
  transition: opacity 0.25s ease-in, transform 0.25s ease-in;
  /* Мы убираем строку из потока, чтобы остальные начали сдвигаться */
  position: absolute;
  width: 100%;
}
.room-list-move {
  transition: transform 0.3s ease;
}

/* Мы анимируем появление новой строки справа */
.room-list-enter-from {
  opacity: 0;
  transform: translateX(24px);
}

/* Мы анимируем уход строки влево */
.room-list-leave-to {
  opacity: 0;
  transform: translateX(-24px);
}

/* Shimmer эффект для скелетон-строк */
.shimmer {
  background: linear-gradient(
    90deg,
    theme('colors.dark.700') 25%,
    theme('colors.dark.600') 50%,
    theme('colors.dark.700') 75%
  );
  background-size: 200% auto;
  animation: shimmer 1.8s ease-in-out infinite;
}

@keyframes shimmer {
  0%   { background-position: -200% center; }
  100% { background-position:  200% center; }
}

.fade-enter-active, .fade-leave-active { transition: opacity 0.3s ease; }
.fade-enter-from, .fade-leave-to       { opacity: 0; }
</style>
