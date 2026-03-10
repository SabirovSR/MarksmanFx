import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App    from './App.vue'
import router from './router'

// Мы подключаем глобальные стили с Tailwind и @apply компонентами
import './style/main.css'

const app  = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)

app.mount('#app')
