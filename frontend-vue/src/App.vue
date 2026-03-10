<script setup lang="ts">
// Мы импортируем контейнер Toast — он рендерится поверх всего приложения
import ToastContainer from '@/components/ui/ToastContainer.vue'
import { useRoute } from 'vue-router'

const route = useRoute()
</script>

<template>
  <!--
    Мы оборачиваем router-view в <Transition>, чтобы при смене маршрута
    контент плавно исчезал (leave) и появлялся (enter) с эффектом fade+slide.
    mode="out-in" гарантирует: старый экран полностью ушёл ДО появления нового.
  -->
  <Transition name="page" mode="out-in">
    <!-- Мы используем :key="route.name" как триггер анимации при смене маршрута -->
    <component :is="$route.matched[0]?.components?.default" :key="route.name" />
  </Transition>

  <!--
    Мы размещаем ToastContainer вне router-view, чтобы он оставался
    на экране даже во время страничных переходов — тосты не мигают.
  -->
  <ToastContainer />
</template>
