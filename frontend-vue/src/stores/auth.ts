import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { useRouter } from 'vue-router'
import type { AuthResponse, UserProfileDto } from '@/types'
import { useToastStore } from './toast'

// Мы используем ключи localStorage — те же, что и JavaFX клиент (для консистентности)
const STORAGE_TOKEN    = 'jwt_token'
const STORAGE_USERNAME = 'username'
const STORAGE_SERVER   = 'server_url'

/**
 * Стор аутентификации.
 *
 * Мы управляем JWT-токеном, данными пользователя и isLoading-состоянием.
 * isLoading блокирует кнопки в UI во время HTTP-запросов — пользователь
 * видит спиннер и не может нажать "Войти" повторно.
 */
export const useAuthStore = defineStore('auth', () => {
  const router = useRouter()
  const toast  = useToastStore()

  // ── Реактивное состояние ─────────────────────────────────────────────

  /** Мы отслеживаем, идёт ли сетевой запрос прямо сейчас */
  const isLoading = ref(false)

  /**
   * Мы храним ошибки валидации конкретных полей, пришедшие с сервера (400 Bad Request).
   * Формат: { "password": "Пароль должен быть от 6 до 128 символов" }
   * Компонент LoginView читает это значение и показывает ошибки под полями.
   * Очищается автоматически при каждой новой попытке.
   */
  const lastFieldErrors = ref<Record<string, string>>({})

  /** Мы храним токен в реактивной переменной и синхронизируем с localStorage */
  const token     = ref<string | null>(localStorage.getItem(STORAGE_TOKEN))
  const username  = ref<string | null>(localStorage.getItem(STORAGE_USERNAME))
  const serverUrl = ref<string>(localStorage.getItem(STORAGE_SERVER) ?? 'http://localhost:8080')

  /** Мы вычисляем флаг авторизации — true если токен присутствует */
  const isAuthenticated = computed(() => !!token.value)

  /** Мы формируем Bearer-заголовок для HTTP и WebSocket запросов */
  const bearerHeader = computed(() =>
    token.value ? `Bearer ${token.value}` : null,
  )

  // ── Методы ──────────────────────────────────────────────────────────

  /**
   * Мы выполняем вход через REST API.
   * isLoading = true → кнопки блокируются → запрос → isLoading = false.
   */
  async function login(
    usernameInput: string,
    password:      string,
    rememberMe:    boolean,
  ): Promise<boolean> {
    isLoading.value = true
    try {
      const res = await fetch(`${serverUrl.value}/api/auth/login`, {
        method:  'POST',
        headers: { 'Content-Type': 'application/json' },
        body:    JSON.stringify({ username: usernameInput, password }),
      })

      if (!res.ok) {
        const err = await res.json().catch(() => ({ error: 'Неверный логин или пароль' }))
        toast.error('Ошибка входа', err.error ?? 'Неверный логин или пароль')
        return false
      }

      const data: AuthResponse = await res.json()
      // Мы сохраняем сессию и переходим в лобби
      _applySession(data, rememberMe)
      toast.success(`Добро пожаловать, ${data.username}!`)
      router.push({ name: 'lobby' })
      return true

    } catch (e) {
      toast.error('Сервер недоступен', `Не удалось подключиться к ${serverUrl.value}`)
      return false
    } finally {
      // Мы всегда снимаем флаг загрузки — даже если произошла ошибка
      isLoading.value = false
    }
  }

  /**
   * Мы выполняем регистрацию нового пользователя.
   * После успешной регистрации сервер сразу возвращает JWT — пользователь залогинен.
   */
  async function register(
    usernameInput: string,
    password:      string,
  ): Promise<boolean> {
    // Мы сбрасываем ошибки полей перед каждой новой попыткой
    lastFieldErrors.value = {}
    isLoading.value = true
    try {
      const res = await fetch(`${serverUrl.value}/api/auth/register`, {
        method:  'POST',
        headers: { 'Content-Type': 'application/json' },
        body:    JSON.stringify({ username: usernameInput, password }),
      })

      if (res.status === 400) {
        // Мы получили ошибку валидации с сервера — разбираем структуру GlobalExceptionHandler:
        // { "message": "Ошибка валидации", "errors": { "password": "...", "username": "..." } }
        const body = await res.json().catch(() => ({}))
        if (body.errors && Object.keys(body.errors).length > 0) {
          // Мы сохраняем ошибки полей — LoginView отобразит их под инпутами
          lastFieldErrors.value = body.errors
          toast.warning(body.message ?? 'Ошибка валидации', 'Проверьте правильность введённых данных')
        } else {
          toast.error('Ошибка регистрации', body.error ?? 'Проверьте введённые данные')
        }
        return false
      }

      if (!res.ok) {
        const err = await res.json().catch(() => ({ error: 'Ошибка регистрации' }))
        toast.error('Ошибка регистрации', err.error ?? 'Неизвестная ошибка')
        return false
      }

      const data: AuthResponse = await res.json()
      _applySession(data, true)
      toast.success('Аккаунт создан!', `Добро пожаловать в игру, ${data.username}`)
      router.push({ name: 'lobby' })
      return true

    } catch (e) {
      toast.error('Сервер недоступен', `Не удалось подключиться к ${serverUrl.value}`)
      return false
    } finally {
      isLoading.value = false
    }
  }

  /**
   * Мы проверяем сохранённый токен при старте приложения (автологин).
   * Возвращает true если токен валиден и сессия восстановлена.
   */
  async function checkSavedSession(): Promise<boolean> {
    if (!token.value) return false
    isLoading.value = true
    try {
      const res = await fetch(`${serverUrl.value}/api/auth/me`, {
        headers: { Authorization: `Bearer ${token.value}` },
      })

      if (res.ok) {
        const profile: UserProfileDto = await res.json()
        username.value = profile.username
        return true
      }

      // Мы очищаем протухший токен
      logout(false)
      return false

    } catch {
      // Мы оставляем токен — сервер мог быть временно недоступен
      return false
    } finally {
      isLoading.value = false
    }
  }

  /** Мы выходим из системы: чистим RAM и localStorage */
  function logout(showToast = true) {
    token.value    = null
    username.value = null
    localStorage.removeItem(STORAGE_TOKEN)
    localStorage.removeItem(STORAGE_USERNAME)

    if (showToast) toast.info('Вы вышли из системы')
    router.push({ name: 'login' })
  }

  /** Мы обновляем адрес сервера (для смены окружения без перезапуска) */
  function setServerUrl(url: string) {
    serverUrl.value = url.replace(/\/$/, '')
    localStorage.setItem(STORAGE_SERVER, serverUrl.value)
  }

  // ── Приватные вспомогательные методы ────────────────────────────────

  function _applySession(data: AuthResponse, persist: boolean) {
    token.value    = data.token
    username.value = data.username
    if (persist) {
      localStorage.setItem(STORAGE_TOKEN,    data.token)
      localStorage.setItem(STORAGE_USERNAME, data.username)
      localStorage.setItem(STORAGE_SERVER,   serverUrl.value)
    }
  }

  /** Мы сбрасываем ошибки полей вручную (например, при переключении вкладок) */
  function clearFieldErrors() {
    lastFieldErrors.value = {}
  }

  return {
    isLoading, token, username, serverUrl,
    isAuthenticated, bearerHeader, lastFieldErrors,
    login, register, logout, checkSavedSession, setServerUrl, clearFieldErrors,
  }
})
