import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

// Мы используем ленивую загрузку (lazy import) для каждого вью —
// Vite создаёт отдельные чанки, браузер скачивает только нужный экран
const LoginView  = () => import('@/views/LoginView.vue')
const LobbyView  = () => import('@/views/LobbyView.vue')
const GameView   = () => import('@/views/GameView.vue')

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/login',
    },
    {
      path: '/login',
      name: 'login',
      component: LoginView,
      // Мы перенаправляем уже авторизованного пользователя в лобби
      meta: { requiresGuest: true },
    },
    {
      path: '/lobby',
      name: 'lobby',
      component: LobbyView,
      // Мы защищаем маршрут — только авторизованные пользователи
      meta: { requiresAuth: true },
    },
    {
      path: '/game/:roomId',
      name: 'game',
      component: GameView,
      meta: { requiresAuth: true },
    },
    // Мы перехватываем все несуществующие маршруты
    {
      path: '/:pathMatch(.*)*',
      redirect: '/',
    },
  ],
})

// ─── Навигационный охранник ───────────────────────────────────────────────
// Мы проверяем аутентификацию перед каждым переходом
router.beforeEach((to) => {
  const auth = useAuthStore()

  // Мы блокируем доступ к защищённым маршрутам для неавторизованных
  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return { name: 'login' }
  }

  // Мы перенаправляем авторизованных пользователей со страницы логина
  if (to.meta.requiresGuest && auth.isAuthenticated) {
    return { name: 'lobby' }
  }
})

export default router
