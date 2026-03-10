<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

// ── Состояние формы ────────────────────────────────────────────────────
const username   = ref(localStorage.getItem('username') ?? '')
const password   = ref('')
const serverUrl  = ref(auth.serverUrl)
const rememberMe = ref(true)
const showPassword = ref(false)
const activeTab  = ref<'login' | 'register'>('login')

const isLoginMode = computed(() => activeTab.value === 'login')
const buttonLabel = computed(() => {
  if (auth.isLoading) return 'Загрузка...'
  return isLoginMode.value ? 'Войти' : 'Создать аккаунт'
})

// ── Ошибки валидации ───────────────────────────────────────────────────

/**
 * Мы храним ошибки клиентской валидации (до отправки запроса).
 * Это быстрая проверка без сетевого запроса — UX становится мгновенным.
 */
const localErrors = ref<Record<string, string>>({})

/**
 * Мы объединяем ошибки: клиентские (localErrors) имеют приоритет,
 * серверные (auth.lastFieldErrors) отображаются когда клиентских нет.
 * Это позволяет показывать и «пароль слишком короткий», и «имя уже занято».
 */
const fieldError = computed(() => (field: string): string =>
  localErrors.value[field] ?? auth.lastFieldErrors[field] ?? '',
)

// ── Очистка ошибок при переключении вкладок ───────────────────────────

/**
 * Мы очищаем все ошибки при смене режима (логин ↔ регистрация),
 * чтобы ошибки регистрации не мешали при попытке войти.
 */
watch(activeTab, () => {
  localErrors.value = {}
  auth.clearFieldErrors()
})

/**
 * Мы очищаем ошибку конкретного поля при каждом вводе —
 * пользователь не должен видеть ошибку, пока редактирует поле.
 */
watch(username, () => { delete localErrors.value.username; auth.lastFieldErrors.username = '' })
watch(password, () => { delete localErrors.value.password; auth.lastFieldErrors.password = '' })

// ── Клиентская валидация ───────────────────────────────────────────────

/**
 * Мы проверяем форму на клиенте перед отправкой сетевого запроса.
 * Это экономит трафик и даёт мгновенный отклик без ожидания сервера.
 *
 * Правила зеркалят серверные аннотации из RegisterRequest:
 *   — username: @Size(min=3, max=50)
 *   — password: @Size(min=6, max=128) только для регистрации
 *
 * @returns true если форма валидна и можно отправлять запрос
 */
function validateForm(): boolean {
  localErrors.value = {}

  // Мы проверяем длину имени пользователя (актуально и для входа, и для регистрации)
  if (username.value.trim().length < 3) {
    localErrors.value.username = 'Имя должно содержать не менее 3 символов'
  }
  if (username.value.trim().length > 50) {
    localErrors.value.username = 'Имя не должно превышать 50 символов'
  }

  // Мы проверяем пароль только для регистрации — при входе пароль может быть любым
  if (!isLoginMode.value) {
    if (password.value.length < 6) {
      localErrors.value.password = 'Пароль должен содержать не менее 6 символов'
    }
    if (password.value.length > 128) {
      localErrors.value.password = 'Пароль не должен превышать 128 символов'
    }
  } else {
    // Мы проверяем минимальную длину и при входе — чтобы не отправлять пустую строку
    if (password.value.length === 0) {
      localErrors.value.password = 'Введите пароль'
    }
  }

  // Мы возвращаем true только если словарь ошибок пустой
  return Object.keys(localErrors.value).length === 0
}

// ── Автологин при монтировании ─────────────────────────────────────────

onMounted(async () => {
  if (auth.token) {
    await auth.checkSavedSession()
  }
})

// ── Обработчик отправки формы ──────────────────────────────────────────

async function handleSubmit() {
  // Мы запускаем клиентскую валидацию перед любым сетевым запросом
  if (!validateForm()) return

  if (serverUrl.value !== auth.serverUrl) {
    auth.setServerUrl(serverUrl.value)
  }

  if (isLoginMode.value) {
    await auth.login(username.value.trim(), password.value, rememberMe.value)
  } else {
    await auth.register(username.value.trim(), password.value)
  }
}
</script>

<template>
  <div class="min-h-screen bg-dark-950 flex items-center justify-center p-4">
    <!-- Мы добавляем декоративный градиентный фон поверх базового цвета страницы -->
    <div
      class="absolute inset-0 bg-gradient-to-br from-primary-dark/20 via-transparent to-accent-cyan-dark/10
             pointer-events-none"
    />

    <div class="card w-full max-w-md p-8 animate-slide-up relative">

      <!-- ── Логотип ──────────────────────────────────────────────────── -->
      <div class="text-center mb-8">
        <h1 class="text-gradient-cyan-purple font-game font-bold text-4xl tracking-wider uppercase">
          Меткий стрелок
        </h1>
        <p class="text-slate-500 text-sm mt-2 tracking-widest uppercase">
          Сетевая игра для лучников
        </p>
      </div>

      <!-- ── Переключатель вкладок ─────────────────────────────────────── -->
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

      <!-- ── Форма ─────────────────────────────────────────────────────── -->
      <form class="space-y-4" @submit.prevent="handleSubmit" novalidate>

        <!-- Поле логина -->
        <div>
          <label class="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1.5">
            Логин
          </label>
          <input
            v-model="username"
            type="text"
            :class="['input-field', fieldError('username') ? 'input-field-error' : '']"
            placeholder="Введите имя игрока"
            autocomplete="username"
            :disabled="auth.isLoading"
          />
          <!--
            Мы показываем ошибку под полем с плавным появлением.
            fieldError('username') возвращает локальную ошибку или серверную,
            в зависимости от того, что случилось позже.
          -->
          <Transition name="field-error">
            <p
              v-if="fieldError('username')"
              class="mt-1.5 text-xs text-accent-red-light flex items-center gap-1"
              role="alert"
            >
              <span>⚠</span>
              {{ fieldError('username') }}
            </p>
          </Transition>
        </div>

        <!-- Поле пароля -->
        <div>
          <label class="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1.5">
            Пароль
            <!-- Мы показываем подсказку о требованиях только в режиме регистрации -->
            <span
              v-if="!isLoginMode"
              class="ml-1 text-slate-600 font-normal normal-case tracking-normal"
            >
              (не менее 6 символов)
            </span>
          </label>
          <div class="relative">
            <input
              v-model="password"
              :type="showPassword ? 'text' : 'password'"
              :class="['input-field pr-10', fieldError('password') ? 'input-field-error' : '']"
              placeholder="Введите пароль"
              autocomplete="current-password"
              :disabled="auth.isLoading"
            />
            <button
              type="button"
              class="absolute right-3 top-1/2 -translate-y-1/2 text-slate-500 hover:text-white transition-colors"
              @click="showPassword = !showPassword"
              :aria-label="showPassword ? 'Скрыть пароль' : 'Показать пароль'"
            >
              {{ showPassword ? '🙈' : '👁' }}
            </button>
          </div>
          <!-- Мы отображаем ошибку пароля под полем -->
          <Transition name="field-error">
            <p
              v-if="fieldError('password')"
              class="mt-1.5 text-xs text-accent-red-light flex items-center gap-1"
              role="alert"
            >
              <span>⚠</span>
              {{ fieldError('password') }}
            </p>
          </Transition>
        </div>

        <!-- Адрес сервера (скрыт по умолчанию) -->
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
              'w-5 h-5 rounded border-2 flex items-center justify-center transition-all shrink-0',
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

        <!-- Кнопка отправки -->
        <button
          type="submit"
          class="btn-primary w-full mt-2 relative overflow-hidden"
          :disabled="auth.isLoading || !username || !password"
        >
          <span v-if="auth.isLoading" class="absolute inset-0 flex items-center justify-center">
            <svg class="animate-spin w-5 h-5" viewBox="0 0 24 24" fill="none">
              <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="3"
                      stroke-dasharray="40" stroke-linecap="round"/>
            </svg>
          </span>
          <span :class="{ 'opacity-0': auth.isLoading }">{{ buttonLabel }}</span>
        </button>

      </form>
    </div>
  </div>
</template>

<style scoped>
/*
  Мы анимируем появление и исчезновение сообщений об ошибках под полями.
  Без анимации они "прыгают" — это раздражает пользователя.
  С анимацией — плавно появляются снизу, как будто выдвигаются.
*/
.field-error-enter-active,
.field-error-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease, max-height 0.25s ease;
  max-height: 40px;
  overflow: hidden;
}
.field-error-enter-from,
.field-error-leave-to {
  opacity: 0;
  transform: translateY(-4px);
  max-height: 0;
}
</style>
