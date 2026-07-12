<script setup>
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuth } from '../../composables/useAuth.js'

const router = useRouter()
const route = useRoute()
const { isLoggedIn, user, logout } = useAuth()

const TABS = [
  { name: 'home', ko: '홈', en: 'Home', slug: 'home' },
  { name: 'map', ko: '지도', en: 'Map', slug: 'map' },
  { name: 'course', ko: '코스', en: 'Course', slug: 'course' },
  { name: 'trip', ko: '일정', en: 'Itinerary', slug: 'trip/0042' },
  { name: 'community', ko: '커뮤니티', en: 'Stories', slug: 'community' },
]

const isAdminRoute = () => route.name === 'admin'
const canSeeAdmin = computed(() => isLoggedIn.value && user.value?.role === 'ADMIN')

const slug = () => {
  const s = route.name || 'home'
  const map = {
    home: 'home',
    map: 'map',
    course: 'course',
    trip: 'trip/0042',
    community: 'community',
    mypage: 'my',
    admin: 'admin',
    login: 'login',
    signup: 'signup',
  }
  return map[s] || s
}

const avatarLabel = computed(() => {
  if (!isLoggedIn.value) return null
  const n = user.value?.nickname || user.value?.email || '?'
  return n.charAt(0).toUpperCase()
})

function handleLogout() {
  logout()
  router.push('/login')
}
</script>

<template>
  <div class="masthead">
    <div class="breadcrumb">
      <span class="slug">
        <template v-if="isAdminRoute()">admin.gadang.kr</template>
        <template v-else>gadang.kr / <b>{{ slug() }}</b></template>
      </span>
      <span>기획 · 2026 · 06 · 02 · 서울</span>
    </div>

    <div class="mast-row">
      <div class="mast-brand" @click="router.push('/home')">
        <span class="wm">가당</span>
        <span class="est">GaDang · EST. 2026</span>
      </div>

      <div class="mast-right">
        <button
          v-if="canSeeAdmin"
          :class="['mast-admin', { active: isAdminRoute() }]"
          @click="router.push('/admin')"
        >
          관리자 Admin
        </button>

        <template v-if="isLoggedIn">
          <span
            class="avatar"
            :style="{ borderRadius:'9px', border: route.name === 'mypage' ? '2px solid var(--accent)' : '2px solid transparent', cursor:'pointer' }"
            @click="router.push('/my')"
          >
            {{ avatarLabel }}
          </span>
          <button class="btn btn-ghost btn-sm" style="font-size:12px" @click="handleLogout">로그아웃</button>
        </template>

        <template v-else>
          <button class="btn btn-outline btn-sm" style="border-radius:6px" @click="router.push('/login')">로그인</button>
          <button class="btn btn-primary btn-sm" style="border-radius:6px" @click="router.push('/signup')">회원가입</button>
        </template>
      </div>
    </div>

    <nav v-if="!isAdminRoute()" class="tabnav">
      <button
        v-for="t in TABS"
        :key="t.name"
        :class="['tab', { active: route.name === t.name }]"
        @click="router.push('/' + t.slug)"
      >
        {{ t.ko }}<span class="en">{{ t.en }}</span>
      </button>
    </nav>
  </div>
</template>
