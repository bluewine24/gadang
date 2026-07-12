<script setup>
/**
 * 소셜 로그인 콜백 — /auth/naver/callback, /auth/kakao/callback
 * OAuth 제공자가 code를 이 페이지로 전달 → 백엔드 POST exchange → JWT 저장
 */
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuth } from '../composables/useAuth.js'
import api from '../api/index.js'

const route  = useRoute()
const router = useRouter()
const { token: authToken, user: authUser } = useAuth()

const errorMsg = ref('')

onMounted(async () => {
  const code     = route.query.code
  const state    = route.query.state  // redirect 경로가 인코딩되어 있음
  const errorParam = route.query.error

  if (errorParam) {
    router.replace('/login?error=' + errorParam)
    return
  }
  if (!code) {
    router.replace('/login')
    return
  }

  // provider는 현재 경로에서 추출 (/auth/naver/callback → naver)
  const provider = route.path.includes('naver') ? 'naver' : 'kakao'
  const redirectUri = window.location.origin + route.path  // 콜백 URL (쿼리 제외)
  const redirectTo  = state && state !== 'home' ? decodeURIComponent(state) : '/home'

  try {
    const { data } = await api.post(`/auth/${provider}/exchange`, { code, redirectUri })

    if (data.success && data.data?.token) {
      const jwt  = data.data.token
      const user = data.data.user ?? {}

      authToken.value = jwt
      authUser.value  = user
      localStorage.setItem('token', jwt)
      localStorage.setItem('user', JSON.stringify(user))

      router.replace(redirectTo)
    } else {
      errorMsg.value = data.message || '로그인에 실패했습니다.'
    }
  } catch (e) {
    const msg = e?.response?.data?.message
    errorMsg.value = msg || '소셜 로그인 처리 중 오류가 발생했습니다.'
  }
})
</script>

<template>
  <div style="display:flex;align-items:center;justify-content:center;height:60vh;flex-direction:column;gap:14px">
    <template v-if="!errorMsg">
      <div style="width:32px;height:32px;border:3px solid var(--accent);border-top-color:transparent;border-radius:50%;animation:spin 0.8s linear infinite" />
      <p style="color:var(--ink-soft);font-size:14px">로그인 처리 중...</p>
    </template>
    <template v-else>
      <p style="color:var(--trend);font-size:14px">{{ errorMsg }}</p>
      <button class="btn btn-outline btn-sm" @click="$router.replace('/login')">로그인 페이지로</button>
    </template>
  </div>
</template>

<style scoped>
@keyframes spin { to { transform: rotate(360deg); } }
</style>
