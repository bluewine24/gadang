<script setup>
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuth } from '../composables/useAuth.js'

const router = useRouter()
const route  = useRoute()
const { login } = useAuth()

const email    = ref('')
const password = ref('')
const error    = ref('')
const loading  = ref(false)

const NAVER_CLIENT_ID = import.meta.env.VITE_NAVER_CLIENT_ID || ''
const KAKAO_CLIENT_ID = import.meta.env.VITE_KAKAO_CLIENT_ID || ''
const ORIGIN = window.location.origin  // http://localhost:5173

onMounted(() => {
  // 소셜 로그인 오류 파라미터 처리
  if (route.query.error === 'naver') error.value = '네이버 로그인에 실패했습니다.'
  if (route.query.error === 'kakao') error.value = '카카오 로그인에 실패했습니다.'
})

async function submit() {
  if (!email.value || !password.value) { error.value = '이메일과 비밀번호를 입력해주세요.'; return }
  loading.value = true
  error.value   = ''
  try {
    const res = await login(email.value, password.value)
    if (res.success) {
      const next = route.query.redirect || '/home'
      router.push(next)
    } else {
      error.value = res.message || '로그인에 실패했습니다.'
    }
  } catch (e) {
    const msg = e?.response?.data?.message
    error.value = msg || '로그인 중 오류가 발생했습니다. 서버 연결을 확인해 주세요.'
  } finally {
    loading.value = false
  }
}

// 소셜 로그인 — 프론트에서 직접 OAuth URL로 이동, 콜백은 /auth/{provider}/callback에서 처리
function socialLogin(provider) {
  const redirect = route.query.redirect || ''
  // state에 redirect 경로 인코딩해서 콜백 후 복귀에 사용
  const state = redirect ? encodeURIComponent(redirect) : 'home'

  if (provider === 'naver') {
    const callbackUri = encodeURIComponent(`${ORIGIN}/auth/naver/callback`)
    window.location.href =
      `https://nid.naver.com/oauth2.0/authorize?response_type=code` +
      `&client_id=${NAVER_CLIENT_ID}&redirect_uri=${callbackUri}&state=${state}`
  } else if (provider === 'kakao') {
    const callbackUri = encodeURIComponent(`${ORIGIN}/auth/kakao/callback`)
    window.location.href =
      `https://kauth.kakao.com/oauth/authorize?response_type=code` +
      `&client_id=${KAKAO_CLIENT_ID}&redirect_uri=${callbackUri}&state=${state}`
  }
}
</script>

<template>
  <div class="screen-wrap" style="max-width:480px;padding-top:48px">
    <header class="cover" style="margin-bottom:28px">
      <div class="cover-meta"><span class="eyebrow">MEMBER LOGIN</span></div>
      <h1 class="disp" style="font-size:clamp(36px,6vw,56px)">로그인</h1>
      <p class="lede" style="font-size:14px">가당 계정으로 로그인하세요.</p>
    </header>

    <!-- 소셜 로그인 -->
    <div style="display:flex;flex-direction:column;gap:10px;margin-bottom:18px">
      <button class="social-btn naver" @click="socialLogin('naver')">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
          <path d="M16.273 12.845L7.376 0H0v24h7.727V11.155L16.624 24H24V0h-7.727z"/>
        </svg>
        네이버로 로그인
      </button>
      <button class="social-btn kakao" @click="socialLogin('kakao')">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
          <path d="M12 3C6.477 3 2 6.617 2 11.088c0 2.83 1.793 5.322 4.5 6.79L5.4 21.3a.5.5 0 0 0 .72.548l4.55-3.025A11.8 11.8 0 0 0 12 19.175C17.523 19.175 22 15.56 22 11.088 22 6.617 17.523 3 12 3z"/>
        </svg>
        카카오로 로그인
      </button>
    </div>

    <div style="display:flex;align-items:center;gap:10px;margin-bottom:18px">
      <div style="flex:1;height:1px;background:var(--line)" />
      <span style="font-size:12px;color:var(--ink-faint)">또는 이메일로 로그인</span>
      <div style="flex:1;height:1px;background:var(--line)" />
    </div>

    <form class="card" style="padding:28px 24px;display:flex;flex-direction:column;gap:18px" @submit.prevent="submit">
      <label style="display:flex;flex-direction:column;gap:6px">
        <span style="font-size:12px;font-weight:600;color:var(--ink-faint);letter-spacing:0.06em">이메일</span>
        <input v-model="email" type="email" autocomplete="email" placeholder="hello@example.com"
               style="border:1.5px solid var(--line-strong);border-radius:8px;padding:11px 14px;font-size:15px;background:var(--bg);outline:none;width:100%;box-sizing:border-box"
               :style="{ borderColor: error ? 'var(--trend)' : 'var(--line-strong)' }" />
      </label>

      <label style="display:flex;flex-direction:column;gap:6px">
        <span style="font-size:12px;font-weight:600;color:var(--ink-faint);letter-spacing:0.06em">비밀번호</span>
        <input v-model="password" type="password" autocomplete="current-password" placeholder="••••••••"
               style="border:1.5px solid var(--line-strong);border-radius:8px;padding:11px 14px;font-size:15px;background:var(--bg);outline:none;width:100%;box-sizing:border-box"
               :style="{ borderColor: error ? 'var(--trend)' : 'var(--line-strong)' }"
               @keyup.enter="submit" />
      </label>

      <div v-if="error" style="font-size:13px;color:var(--trend);padding:10px 12px;border-radius:8px;background:color-mix(in oklch,var(--trend) 10%,var(--card))">
        {{ error }}
      </div>

      <button type="submit" class="btn btn-primary btn-block" style="border-radius:8px;padding:13px;font-size:15px" :disabled="loading">
        {{ loading ? '로그인 중…' : '로그인' }}
      </button>
    </form>

    <div style="text-align:center;margin-top:20px;font-size:13.5px;color:var(--ink-soft)">
      계정이 없으신가요?
      <button class="btn btn-ghost" style="font-size:13.5px;padding:0 4px;color:var(--accent-deep);font-weight:600"
              @click="router.push('/signup')">회원가입</button>
    </div>
  </div>
</template>

<style scoped>
.social-btn {
  display: flex; align-items: center; justify-content: center; gap: 10px;
  padding: 13px; border-radius: 8px; font-size: 15px; font-weight: 600;
  width: 100%; border: none; cursor: pointer; transition: opacity .15s;
}
.social-btn:hover { opacity: .88; }
.social-btn.naver  { background: #03C75A; color: #fff; }
.social-btn.kakao  { background: #FEE500; color: #191919; }
</style>
