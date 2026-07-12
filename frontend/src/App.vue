<script setup>
import { onMounted } from 'vue'
import { RouterView } from 'vue-router'
import AppMasthead from './components/layout/AppMasthead.vue'
import AppBotNav   from './components/layout/AppBotNav.vue'
import AppFooter   from './components/layout/AppFooter.vue'
import ChatWidget  from './components/ai/ChatWidget.vue'
import { requestLocation } from './utils/geo.js'

// 사용자가 어느 탭으로 들어오든 GPS를 가장 먼저 요청 — Home/Map 진입 시
// 위치 대기로 인한 지연과 콜드/웜 결과 불일치를 없앤다
onMounted(() => { requestLocation() })
</script>

<template>
  <div class="app">
    <AppMasthead />
    <main class="app-main">
      <RouterView />
    </main>
    <AppFooter />
    <AppBotNav />
    <ChatWidget />
  </div>
</template>
