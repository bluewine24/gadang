import { ref, computed } from 'vue'
import api from '../api/index.js'

const token = ref(localStorage.getItem('token') || '')
const user  = ref(JSON.parse(localStorage.getItem('user') || 'null'))

// api/index.js의 401 인터셉터가 발행하는 이벤트 — 반응형 상태 동기화
window.addEventListener('auth:logout', () => {
  token.value = ''
  user.value  = null
})

export function useAuth() {
  const isLoggedIn = computed(() => !!token.value)

  function _persist(t, u) {
    token.value = t
    user.value  = u
    localStorage.setItem('token', t)
    localStorage.setItem('user', JSON.stringify(u))
  }

  async function login(email, password) {
    const { data } = await api.post('/auth/login', { email, password })
    if (data.success && data.data?.token) {
      _persist(data.data.token, data.data.user ?? { email, nickname: email.split('@')[0] })
    }
    return data
  }

  async function signup(nickname, email, password) {
    const { data } = await api.post('/auth/signup', { nickname, email, password })
    return data
  }

  function logout() {
    token.value = ''
    user.value  = null
    localStorage.removeItem('token')
    localStorage.removeItem('user')
  }

  function updateUser(patch) {
    const u = { ...user.value, ...patch }
    user.value = u
    localStorage.setItem('user', JSON.stringify(u))
  }

  return { token, user, isLoggedIn, login, signup, logout, updateUser }
}
