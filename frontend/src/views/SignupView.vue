<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuth } from '../composables/useAuth.js'

const router = useRouter()
const { signup, login } = useAuth()

const nickname = ref('')
const email = ref('')
const password = ref('')
const password2 = ref('')
const error = ref('')
const loading = ref(false)

async function submit() {
  error.value = ''

  if (!nickname.value || !email.value || !password.value) {
    error.value = '모든 항목을 입력해 주세요.'
    return
  }
  if (password.value.length < 6) {
    error.value = '비밀번호는 6자 이상이어야 합니다.'
    return
  }
  if (password.value !== password2.value) {
    error.value = '비밀번호가 일치하지 않습니다.'
    return
  }

  loading.value = true
  try {
    const res = await signup(nickname.value, email.value, password.value)
    if (res.success) {
      await login(email.value, password.value)
      router.push('/home')
    } else {
      error.value = res.message || '회원가입에 실패했습니다.'
    }
  } catch (e) {
    const msg = e?.response?.data?.message
    error.value = msg || '회원가입 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="screen-wrap" style="max-width:480px;padding-top:48px">
    <header class="cover" style="margin-bottom:28px">
      <div class="cover-meta"><span class="eyebrow">NEW MEMBER</span></div>
      <h1 class="disp" style="font-size:clamp(36px,6vw,56px)">회원가입</h1>
      <p class="lede" style="font-size:14px">가당과 함께 여행 일정을 시작해 보세요.</p>
    </header>

    <form class="card" style="padding:28px 24px;display:flex;flex-direction:column;gap:16px" @submit.prevent="submit">
      <label style="display:flex;flex-direction:column;gap:6px">
        <span style="font-size:12px;font-weight:600;color:var(--ink-faint);letter-spacing:0.06em">닉네임</span>
        <input
          v-model="nickname"
          type="text"
          placeholder="홍길동"
          style="border:1.5px solid var(--line-strong);border-radius:8px;padding:11px 14px;font-size:15px;background:var(--card);outline:none;width:100%;box-sizing:border-box"
        />
      </label>

      <label style="display:flex;flex-direction:column;gap:6px">
        <span style="font-size:12px;font-weight:600;color:var(--ink-faint);letter-spacing:0.06em">이메일</span>
        <input
          v-model="email"
          type="email"
          autocomplete="email"
          placeholder="hello@example.com"
          style="border:1.5px solid var(--line-strong);border-radius:8px;padding:11px 14px;font-size:15px;background:var(--card);outline:none;width:100%;box-sizing:border-box"
        />
      </label>

      <label style="display:flex;flex-direction:column;gap:6px">
        <span style="font-size:12px;font-weight:600;color:var(--ink-faint);letter-spacing:0.06em">비밀번호 (6자 이상)</span>
        <input
          v-model="password"
          type="password"
          autocomplete="new-password"
          placeholder="••••••••"
          style="border:1.5px solid var(--line-strong);border-radius:8px;padding:11px 14px;font-size:15px;background:var(--card);outline:none;width:100%;box-sizing:border-box"
        />
      </label>

      <label style="display:flex;flex-direction:column;gap:6px">
        <span style="font-size:12px;font-weight:600;color:var(--ink-faint);letter-spacing:0.06em">비밀번호 확인</span>
        <input
          v-model="password2"
          type="password"
          autocomplete="new-password"
          placeholder="••••••••"
          style="border:1.5px solid var(--line-strong);border-radius:8px;padding:11px 14px;font-size:15px;background:var(--card);outline:none;width:100%;box-sizing:border-box"
          :style="{ borderColor: password2 && password !== password2 ? 'var(--trend)' : 'var(--line-strong)' }"
        />
      </label>

      <div v-if="error" style="font-size:13px;color:var(--trend);padding:10px 12px;border-radius:8px;background:color-mix(in oklch,var(--trend) 10%,var(--card))">
        {{ error }}
      </div>

      <button type="submit" class="btn btn-primary btn-block" style="border-radius:8px;padding:13px;font-size:15px" :disabled="loading">
        {{ loading ? '가입 중...' : '가입하기' }}
      </button>
    </form>

    <div style="text-align:center;margin-top:20px;font-size:13.5px;color:var(--ink-soft)">
      이미 계정이 있으신가요?
      <button
        class="btn btn-ghost"
        style="font-size:13.5px;padding:0 4px;color:var(--accent-deep);font-weight:600"
        @click="router.push('/login')"
      >
        로그인
      </button>
    </div>
  </div>
</template>
