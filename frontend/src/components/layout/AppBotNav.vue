<script setup>
import { useRouter, useRoute } from 'vue-router'
import AppIcon from '../ui/AppIcon.vue'

const router = useRouter()
const route  = useRoute()

const ITEMS = [
  { name: 'home',      label: '홈',     icon: 'home' },
  { name: 'course',    label: '코스',   icon: 'sliders' },
  { name: 'trip',      label: '일정',   icon: 'route' },
  { name: 'community', label: '커뮤니티', icon: 'community' },
  { name: 'mypage',    label: '마이',   icon: 'user' },
]
</script>

<template>
  <nav class="botnav">
    <button v-for="n in ITEMS" :key="n.name"
            :class="['botnav-item', { active: route.name === n.name }]"
            @click="router.push('/' + (n.name === 'mypage' ? 'my' : n.name))">
      <AppIcon :name="n.icon" style="width:22px;height:22px" />
      {{ n.label }}
    </button>
  </nav>
</template>
