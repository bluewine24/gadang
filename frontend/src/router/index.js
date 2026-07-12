import { createRouter, createWebHistory } from 'vue-router'

function storedUser() {
  try {
    return JSON.parse(localStorage.getItem('user') || 'null')
  } catch {
    return null
  }
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/', redirect: '/home' },
    { path: '/home', name: 'home', component: () => import('../views/HomeView.vue') },
    { path: '/map', name: 'map', component: () => import('../views/MapView.vue') },
    { path: '/course', name: 'course', component: () => import('../views/CourseView.vue') },
    { path: '/trip/:id?', name: 'trip', component: () => import('../views/TripView.vue') },
    { path: '/community', name: 'community', component: () => import('../views/CommunityView.vue') },
    { path: '/community/write', name: 'postWrite', component: () => import('../views/PostWriteView.vue') },
    { path: '/community/:id', name: 'communityPost', component: () => import('../views/CommunityPostView.vue') },
    { path: '/my', name: 'mypage', component: () => import('../views/MypageView.vue') },
    { path: '/login', name: 'login', component: () => import('../views/LoginView.vue') },
    { path: '/auth/naver/callback', name: 'naverCallback', component: () => import('../views/OAuthCallbackView.vue') },
    { path: '/auth/kakao/callback', name: 'kakaoCallback', component: () => import('../views/OAuthCallbackView.vue') },
    { path: '/signup', name: 'signup', component: () => import('../views/SignupView.vue') },
    {
      path: '/admin',
      name: 'admin',
      meta: { requiresAdmin: true },
      component: () => import('../views/AdminView.vue'),
    },
  ],
})

router.beforeEach((to) => {
  if (!to.meta.requiresAdmin) return true
  const user = storedUser()
  if (user?.role === 'ADMIN') return true
  return {
    name: localStorage.getItem('token') ? 'home' : 'login',
    query: localStorage.getItem('token') ? {} : { redirect: to.fullPath },
  }
})

export default router
