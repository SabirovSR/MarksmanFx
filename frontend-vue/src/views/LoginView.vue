<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

// ── Состояние формы ────────────────────────────────────────────────────
const username  = ref(localStorage.getItem('username') ?? '')
const password  = ref('')
const serverUrl = ref(auth.serverUrl)
const rememberMe = ref(true)

/** Мы переключаемся между вкладками "Войти" и "Регистрация" */
const activeTab = ref<'login' | 'register'>('login')

/** Мы скрываем/показываем пароль по клику на иконку глаза */
const showPassword = ref(false)

const isLoginMode  = computed(() => activeTab.value === 'login')
const buttonLabel  = computed(() => {
  if (auth.isLoading) return 'Загрузка...'
  return isLoginMode.value ? 'Войти' : 'Создать аккаунт'
})

// ── Автологин при монтировании ─────────────────────────────────────────

/**
 * Мы проверяем сохранённый токен при открытии страницы.
 * Если токен валиден — переходим в лобби автоматически.
 */
onMounted(async () => {
  if (auth.token) {
    await auth.checkSavedSession()
  }
})

// ── Обработчики ────────────────────────────────────────────────────────

async function handleSubmit() {
  // Мы обновляем URL сервера, если он изменился
  if (serverUrl.value !== auth.serverUrl) {
    auth.setServerUrl(serverUrl.value)
  }

  if (isLoginMode.value) {
    await auth.login(username.value, password.value, rememberMe.value)
  } else {
    await auth.register(username.value, password.value)
  }
}
</script>

<template>
  <div
    class="min-h-screen bg-dark-950 flex items-center justify-center p-4"
  >
    <!-- Декоративный градиентный фон -->
    <div
      class="absolute inset-0 bg-gradient-to-br from-primary-dark/20 via-transparent to-accent-cyan-dark/10
             pointer-events-none"
    />

    <!-- Карточка формы — анимируется при появлении страницы (page transition) -->
    <div class="card w-full max-w-md p-8 animate-slide-up">

      <!-- ── Логотип / заголовок ─────────────────────────── -->
      <div class="text-center mb-8">
        <h1 class="text-gradient-cyan-purple font-game font-bold text-4xl tracking-wider uppercase">
          Меткий стрелок
        </h1>
        <p class="text-slate-500 text-sm mt-2 tracking-widest uppercase">
          Сетевая игра для лучников
        </p>
      </div>

      <!-- ── Переключатель вкладок ───────────────────────── -->
      <div class="flex gap-1 bg-dark-800 p-1 rounded-lg mb-6">
        <button
          v-for="tab in ['login', 'register'] as const"
          :key="tab"
          :class="[
            'flex-1 py-2 px-4 rounded-md text-sm font-game font-semibold transition-all duration-200',
            activeTab === tab
              ? 'bg-primary text-white shadow-neon-purple'
              : 'text-slate-400 hover:text-white',
          ]"
          @click="activeTab = tab"
        >
          {{ tab === 'login' ? 'Войти' : 'Регистрация' }}
        </button>
      </div>

      <!-- ── Форма ───────────────────────────────────────── -->
      <form class="space-y-4" @submit.prevent="handleSubmit">

        <!-- Логин -->
        <div>
          <label class="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1.5">
            Логин
          </label>
          <input
            v-model="username"
            type="text"
            class="input-field"
            placeholder="Введите имя игрока"
            autocomplete="username"
            required
            :disabled="auth.isLoading"
          />
        </div>

        <!-- Пароль -->
        <div>
          <label class="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1.5">
            Пароль
          </label>
          <div class="relative">
            <input
              v-model="password"
              :type="showPassword ? 'text' : 'password'"
              class="input-field pr-10"
              placeholder="Введите пароль"
              autocomplete="current-password"
              required
              :disabled="auth.isLoading"
            />
            <!-- Мы добавляем кнопку показа/скрытия пароля для удобства -->
            <button
              type="button"
              class="absolute right-3 top-1/2 -translate-y-1/2 text-slate-500 hover:text-white transition-colors"
              @click="showPassword = !showPassword"
              :aria-label="showPassword ? 'Скрыть пароль' : 'Показать пароль'"
            >
              {{ showPassword ? '🙈' : '👁' }}
            </button>
          </div>
        </div>

        <!-- Адрес сервера (скрытый по умолчанию) -->
        <details class="group">
          <summary class="text-xs text-slate-500 hover:text-slate-300 cursor-pointer transition-colors
                          flex items-center gap-1 select-none">
            <span class="group-open:rotate-90 transition-transform inline-block">▶</span>
            Настройки подключения
          </summary>
          <div class="mt-2">
            <input
              v-model="serverUrl"
              type="url"
              class="input-field text-sm"
              placeholder="http://localhost:8080"
              :disabled="auth.isLoading"
            />
          </div>
        </details>

        <!-- Чекбокс "Запомнить меня" -->
        <label class="flex items-center gap-3 cursor-pointer group">
          <div
            :class="[
              'w-5 h-5 rounded border-2 flex items-center justify-center transition-all',
              rememberMe
                ? 'bg-primary border-primary'
                : 'bg-transparent border-dark-600 group-hover:border-dark-500',
            ]"
            @click="rememberMe = !rememberMe"
          >
            <span v-if="rememberMe" class="text-white text-xs">✓</span>
          </div>
          <span class="text-slate-400 text-sm group-hover:text-slate-300 transition-colors">
            Запомнить меня
          </span>
        </label>

        <!-- Кнопка отправки — блокируется во время загрузки -->
        <button
          type="submit"
          class="btn-primary w-full mt-2 relative overflow-hidden"
          :disabled="auth.isLoading || !username || !password"
        >
          <!-- Мы показываем спиннер вместо текста во время запроса -->
          <span
            v-if="auth.isLoading"
            class="absolute inset-0 flex items-center justify-center"
          >
            <svg class="animate-spin w-5 h-5" viewBox="0 0 24 24" fill="none">
              <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="3" stroke-dasharray="40" stroke-linecap="round"/>
            </svg>
          </span>
          <span :class="{ 'opacity-0': auth.isLoading }">
            {{ buttonLabel }}
          </span>
        </button>
      </form>
    </div>
  </div>
</template>
