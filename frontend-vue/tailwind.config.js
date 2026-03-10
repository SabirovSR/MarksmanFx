/** @type {import('tailwindcss').Config} */

// Мы определяем кастомную игровую палитру с тёмной темой и неоновыми акцентами
export default {
  // Мы сканируем все файлы проекта для purge неиспользуемых классов в продакшене
  content: ['./index.html', './src/**/*.{vue,js,ts,jsx,tsx}'],

  // Мы активируем тёмный режим через CSS-класс на <html>, а не через prefers-color-scheme
  darkMode: 'class',

  theme: {
    extend: {
      // ─── Цветовая палитра ─────────────────────────────────────────────
      colors: {
        // Мы определяем глубокую тёмную гамму для фонов и поверхностей
        dark: {
          950: '#07070f', // Самый глубокий фон — основной цвет страниц
          900: '#0f0f1a', // Фон карточек и модальных окон
          800: '#1a1a2e', // Поверхность второго уровня (таблицы, боковые панели)
          700: '#1e2240', // Обводки и разделители
          600: '#252a4a', // Hover-состояния тёмных элементов
          500: '#2d3463', // Активные состояния кнопок на тёмном фоне
        },
        // Мы используем фиолетовый как основной брендовый цвет
        primary: {
          DEFAULT: '#7c3aed',
          light:   '#8b5cf6',
          dark:    '#6d28d9',
          glow:    'rgba(124, 58, 237, 0.35)',
        },
        // Мы используем циановый для нашего игрока, подсказок и успеха
        'accent-cyan': {
          DEFAULT: '#06b6d4',
          light:   '#22d3ee',
          dark:    '#0891b2',
          glow:    'rgba(6, 182, 212, 0.35)',
        },
        // Мы используем красный для соперников, ошибок и опасности
        'accent-red': {
          DEFAULT: '#ef4444',
          light:   '#f87171',
          dark:    '#dc2626',
          glow:    'rgba(239, 68, 68, 0.35)',
        },
        // Мы используем золотой для очков, победителей и предупреждений
        'accent-gold': {
          DEFAULT: '#f59e0b',
          light:   '#fbbf24',
          dark:    'd97706',
          glow:    'rgba(245, 158, 11, 0.35)',
        },
        // Мы используем зелёный только для состояния "готов" и успеха
        'accent-green': {
          DEFAULT: '#10b981',
          light:   '#34d399',
          dark:    '#059669',
        },
      },

      // ─── Типографика ─────────────────────────────────────────────────
      fontFamily: {
        // Мы используем Rajdhani для заголовков — угловатый игровой шрифт
        game: ['Rajdhani', 'system-ui', 'sans-serif'],
        // JetBrains Mono для числовых показателей и пинг-таймеров
        mono: ['"JetBrains Mono"', 'ui-monospace', 'monospace'],
      },

      // ─── Пользовательские тени (неоновые эффекты) ────────────────────
      boxShadow: {
        'neon-cyan':   '0 0 12px rgba(6,182,212,0.5), 0 0 32px rgba(6,182,212,0.2)',
        'neon-purple': '0 0 12px rgba(124,58,237,0.5), 0 0 32px rgba(124,58,237,0.2)',
        'neon-red':    '0 0 12px rgba(239,68,68,0.5),  0 0 32px rgba(239,68,68,0.2)',
        'neon-gold':   '0 0 12px rgba(245,158,11,0.5), 0 0 32px rgba(245,158,11,0.2)',
        'card':        '0 4px 24px rgba(0,0,0,0.4)',
        'card-hover':  '0 8px 40px rgba(0,0,0,0.6)',
      },

      // ─── Анимации ────────────────────────────────────────────────────
      animation: {
        // Базовые анимации появления элементов
        'fade-in':       'fadeIn 0.3s ease-out both',
        'fade-out':      'fadeOut 0.3s ease-in both',
        'slide-up':      'slideUp 0.35s cubic-bezier(0.21, 1.02, 0.73, 1) both',
        'slide-in-right':'slideInRight 0.4s cubic-bezier(0.21, 1.02, 0.73, 1) both',
        'slide-out-left':'slideOutLeft 0.35s ease-in both',

        // Анимации Toast-уведомлений
        'toast-in':  'toastIn 0.4s cubic-bezier(0.21, 1.02, 0.73, 1) both',
        'toast-out': 'toastOut 0.35s ease-in both',

        // Обратный отсчёт перед матчем
        'countdown': 'countdownPulse 0.9s ease-out both',

        // Мерцание неонового свечения для активных элементов
        'glow-pulse': 'glowPulse 2.5s ease-in-out infinite',

        // Пульсация точки "онлайн" для игроков
        'ping-slow':  'ping 2s cubic-bezier(0, 0, 0.2, 1) infinite',

        // Горизонтальная загрузочная полоска
        'shimmer':    'shimmer 1.8s ease-in-out infinite',
      },

      keyframes: {
        // Мы определяем keyframes появления/исчезновения для страничных переходов
        fadeIn: {
          from: { opacity: '0', transform: 'translateY(10px)' },
          to:   { opacity: '1', transform: 'translateY(0)' },
        },
        fadeOut: {
          from: { opacity: '1', transform: 'translateY(0)' },
          to:   { opacity: '0', transform: 'translateY(-10px)' },
        },
        slideUp: {
          from: { opacity: '0', transform: 'translateY(24px)' },
          to:   { opacity: '1', transform: 'translateY(0)' },
        },
        slideInRight: {
          from: { opacity: '0', transform: 'translateX(32px)' },
          to:   { opacity: '1', transform: 'translateX(0)' },
        },
        slideOutLeft: {
          from: { opacity: '1', transform: 'translateX(0)' },
          to:   { opacity: '0', transform: 'translateX(-32px)' },
        },

        // Мы определяем анимацию появления Toast — вылет справа с пружинным замедлением
        toastIn: {
          from: { opacity: '0', transform: 'translateX(110%) scale(0.9)' },
          to:   { opacity: '1', transform: 'translateX(0) scale(1)' },
        },
        toastOut: {
          from: { opacity: '1', transform: 'translateX(0) scale(1)' },
          to:   { opacity: '0', transform: 'translateX(110%) scale(0.9)' },
        },

        // Мы создаём эффект "взрыва" для обратного отсчёта:
        // число появляется из центра, кратко держится, затем растворяется и расширяется
        countdownPulse: {
          '0%':   { opacity: '0', transform: 'scale(0.2)',  filter: 'blur(12px)' },
          '25%':  { opacity: '1', transform: 'scale(1.15)', filter: 'blur(0)' },
          '55%':  { opacity: '1', transform: 'scale(1.0)',  filter: 'blur(0)' },
          '100%': { opacity: '0', transform: 'scale(2.2)',  filter: 'blur(8px)' },
        },

        glowPulse: {
          '0%, 100%': { boxShadow: '0 0 8px rgba(6,182,212,0.3)' },
          '50%':      { boxShadow: '0 0 28px rgba(6,182,212,0.7)' },
        },

        shimmer: {
          '0%':   { backgroundPosition: '-200% center' },
          '100%': { backgroundPosition: '200% center' },
        },
      },
    },
  },

  plugins: [],
}
