import api from './index.js'
import * as mock from '../data/mock.js'

const unwrap = (res) => {
  const { success, message, data } = res.data
  if (!success) throw new Error(message || '서버 오류')
  return data
}

const apiErrorMessage = (error) =>
  error?.response?.data?.message
  || error?.response?.data?.error
  || error?.message

const fallback = (fn, fallbackValue = null) => async (...args) => {
  try {
    return await fn(...args)
  } catch {
    return fallbackValue
  }
}

const pageItems = (data) => Array.isArray(data) ? data : data?.items || []

const toCommunityCard = (post) => ({
  id:       post.postId  ?? post.id,
  postId:   post.postId  ?? post.id,
  title:    post.title,
  region:   post.regionName || post.region || '공유',
  author:   post.authorNickname || post.author || '익명',
  cost:     Number(post.totalCost   ?? post.cost  ?? 0),
  min:      Number(post.totalDurationMin ?? post.min ?? 0),  // ← 백엔드 필드명 수정
  places:   Array.isArray(post.places) ? post.places.length : Number(post.placeCount ?? 0),
  likes:    post.likes    || 0,
  comments: post.commentCount ?? post.comments ?? 0,
  saves:    post.saves    || 0,
  tags:     post.tags     || ['공유코스'],
  hot:      Boolean(post.hot),
})

const toNoticeCard = (notice) => ({
  id: notice.noticeId || notice.id,
  tag: notice.tag || '공지',
  title: notice.title,
  date: String(notice.createdAt || notice.date || '').slice(5, 10),
  content: notice.content,
})

const toMe = (summary, trips, favorites, posts) => {
  const profile = summary?.profile || {}
  return {
    nick: profile.nickname || mock.ME.nick,
    email: profile.email || mock.ME.email,
    region: profile.region || mock.ME.region,
    joined: String(profile.createdAt || mock.ME.joined).slice(0, 10),
    stats: {
      trips: summary?.tripCount || 0,
      spent: summary?.totalUsedCost || 0,
      favorites: summary?.favoriteCount || 0,
      shared: summary?.postCount || 0,
    },
    trips: pageItems(trips).map((trip) => ({
      id: trip.tripId,
      title: trip.title,
      region: trip.regionName || '여행',
      date: String(trip.tripDate || '').slice(5, 10),
      cost: Number(trip.totalCost || 0) + Number(trip.foodCostEst || 0),
      status: '저장',
    })),
    favorites: pageItems(favorites).map((place) => String(place.placeId)),
    posts: pageItems(posts).map((post) => ({
      id: post.postId,
      title: post.title,
      likes: post.likes || 0,
      comments: post.commentCount || 0,
    })),
  }
}

export const geocodeCoord = fallback(async (lat, lng) =>
  unwrap(await api.get('/regions/geocode', { params: { lat, lng } })), null)

export const searchRegions = async ({ from, dep, arr, lat, lng } = {}) => {
  const params = { from: from || '서울역', dep: dep || '08:00', arr: arr || '20:00' }
  if (lat != null) params.lat = lat
  if (lng != null) params.lng = lng
  // 첫 검색(콜드 캐시)은 외부 API 수십 회 호출로 오래 걸림 — 전역 10초 대신 넉넉히
  const res = await api.get('/regions/search', { params, timeout: 120000 })
  return unwrap(res)
}

export const getPlaces = fallback(async (regionId) =>
  unwrap(await api.get(`/places?region=${regionId}`)), mock.PLACES)

// 좌표 기반 스코어링 장소 후보 (지도 탭) — 첫 조회는 외부 API 수집으로 오래 걸려 타임아웃 넉넉히
// topPercent: 카테고리 내 인기 상위 N%만 응답 (15/30/45). 같은 캐시를 재사용해 즉시 재필터링됨
export const suggestPlaces = async (lat, lng, radius = 20000, topPercent = 45) =>
  unwrap(await api.get('/places/suggest', { params: { lat, lng, radius, topPercent }, timeout: 180000 }))

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'

/**
 * 장소 후보 SSE 스트리밍 — 콜드 캐시(첫 조회)일 때 구역(zone)이 끝날 때마다 partial 콜백,
 * 전체 채점이 끝나면 complete 콜백(최종 결과, 이 시점 이후 partial로 받은 건 무시하고 교체).
 * 캐시 적중이면 partial 없이 바로 complete.
 *
 * @param {{lat?, lng?, region?, radius?, topPercent?}} params
 * @returns {() => void} 구독 해제 함수 (컴포넌트 언마운트/재검색 시 호출)
 */
export function streamPlaces(params, { onPartial, onComplete, onError } = {}) {
  const qs = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== null && v !== '') qs.set(k, v)
  }
  const es = new EventSource(`${API_BASE}/places/suggest/stream?${qs}`)

  es.addEventListener('partial', (e) => {
    try { onPartial?.(JSON.parse(e.data)) } catch { /* ignore malformed chunk */ }
  })
  es.addEventListener('complete', (e) => {
    try { onComplete?.(JSON.parse(e.data)) } catch { onError?.(e) }
    es.close()
  })
  es.addEventListener('error', (e) => {
    try { onError?.(JSON.parse(e.data)) } catch { onError?.(e) }
    es.close()
  })
  es.onerror = (e) => {
    if (es.readyState === EventSource.CLOSED) return
    onError?.(e)
    es.close()
  }

  return () => es.close()
}

// 코스 생성은 콜드 캐시 시 외부 API 수집으로 오래 걸려 타임아웃 넉넉히 (기본 10초로는 끊김)
export const getCourse = async (params) => {
  try {
    return unwrap(await api.post('/course/generate', params, { timeout: 120000 }))
  } catch (error) {
    throw new Error(apiErrorMessage(error) || '코스 생성에 실패했습니다.')
  }
}

export const regenerateCourse = async (params) => {
  try {
    return unwrap(await api.post('/course/regenerate', params, { timeout: 120000 }))
  } catch (error) {
    throw new Error(apiErrorMessage(error) || '코스 재생성에 실패했습니다.')
  }
}

export const getCommunity = fallback(async (params) =>
  pageItems(unwrap(await api.get('/community/posts', { params }))).map(toCommunityCard), mock.COMMUNITY)

export const getMe = fallback(async () =>
  toMe(
    unwrap(await api.get('/mypage/summary')),
    unwrap(await api.get('/mypage/trips')),
    unwrap(await api.get('/mypage/favorites')),
    unwrap(await api.get('/mypage/posts')),
  ), mock.ME)

export const getNotices = fallback(async () =>
  pageItems(unwrap(await api.get('/notices'))).map(toNoticeCard), mock.NOTICES)

export const saveCourse = fallback(async (courseId) =>
  unwrap(await api.post(`/community/${courseId}/save`)))

export const getAdminStats = fallback(async () =>
  {
    const summary = unwrap(await api.get('/admin/operation-data/summary'))
    return {
      kpi: [
        { label: '신규 가입', value: summary.userCount, unit: '명', delta: '-', up: true },
        { label: '일정 생성', value: summary.tripCount, unit: '건', delta: '-', up: true },
        { label: '코스 공유', value: summary.postCount, unit: '건', delta: '-', up: true },
        { label: '등록 장소', value: summary.placeCount, unit: '곳', delta: '-', up: true },
      ],
    }
  })

export const getTourDetail = fallback(async (name, cat) =>
  unwrap(await api.get('/tour/detail', { params: { name, cat } })))

// ── Auth ───────────────────────────────────────────────
export const apiLogin = async (email, password) => {
  const { data } = await api.post('/auth/login', { email, password })
  return data
}

export const apiSignup = async (nickname, email, password) => {
  const { data } = await api.post('/auth/signup', { nickname, email, password })
  return data
}

// ── User / Profile ─────────────────────────────────────
export const getMyProfile = fallback(async () =>
  unwrap(await api.get('/users/me')), null)

export const updateProfile = async (nickname, email, region) => {
  const { data } = await api.patch('/users/me', { nickname, email, region })
  return data
}

export const deleteAccount = async () => {
  const { data } = await api.delete('/users/me')
  return data
}

// ── Favorites ──────────────────────────────────────────
export const addFavorite = fallback(async (placeId) =>
  unwrap(await api.post(`/favorites/${placeId}`)), null)

export const removeFavorite = fallback(async (placeId) =>
  unwrap(await api.delete(`/favorites/${placeId}`)), null)

// ── Community ──────────────────────────────────────────
export const getCommunityPost = fallback(async (postId) =>
  unwrap(await api.get(`/community/posts/${postId}`)), null)

export const createPost = async (payload) => {
  const { data } = await api.post('/community/posts', payload)
  return data
}

export const updatePost = async (postId, payload) => {
  const { data } = await api.patch(`/community/posts/${postId}`, payload)
  return data
}

export const deletePost = async (postId) => {
  const { data } = await api.delete(`/community/posts/${postId}`)
  return data
}

export const createComment = async (postId, content) => {
  const { data } = await api.post(`/community/posts/${postId}/comments`, { content })
  return data
}

export const deleteComment = async (commentId) => {
  const { data } = await api.delete(`/community/posts/comments/${commentId}`)
  return data
}

// ── 확정 일정 (Trip / F126) ────────────────────────────
// 추천 코스를 확정 저장 (로그인 필요) → { tripId }
export const saveTrip = async (title, course) => {
  const { data } = await api.post('/trips', { title, course })
  return data?.data
}

// 내 확정 일정 목록 (비로그인 시 빈 배열)
export const getTrips = fallback(async () =>
  unwrap(await api.get('/trips')), [])

// 확정 일정 상세 (코스 타임라인 포함)
export const getTripDetail = fallback(async (tripId) =>
  unwrap(await api.get(`/trips/${tripId}`)), null)

// ── AI 컨시어지 챗봇 ───────────────────────────────────
// 콜드 캐시 시 지역추천 Tool 이 외부 API 수십 회 호출로 오래 걸려 타임아웃 넉넉히
export const askAI = async (message) => {
  const res = await api.post('/ai/chat', { message }, { timeout: 120000 })
  return unwrap(res) // { reply }
}

// ── Region Info ────────────────────────────────────────
export const getRegionInfo = fallback(async (region, lat, lng) =>
  unwrap(await api.get('/region/info', { params: { region, lat, lng } })), null)

// ── Admin ──────────────────────────────────────────────
export const getAdminUsers = fallback(async (page = 1, size = 20, q = '') =>
  unwrap(await api.get('/admin/users', { params: { page, size, q } })), { items: [], page: 1, size: 20, totalCount: 0 })

export const updateUserRole = async (userId, role) => {
  const { data } = await api.patch(`/admin/users/${userId}/role`, { role })
  return data
}

export const deleteAdminUser = async (userId) => {
  const { data } = await api.delete(`/admin/users/${userId}`)
  return data
}

export const getAdminPosts = fallback(async (page = 1, size = 20) =>
  unwrap(await api.get('/community/posts/admin', { params: { page, size } })), { items: [], page: 1, size: 20, totalCount: 0 })

export const blindPost = async (postId, blind) => {
  const { data } = await api.patch(`/community/posts/${postId}/blind`, null, { params: { blind } })
  return data
}

export const getPostsByPlace = fallback(async (placeName) =>
  pageItems(unwrap(await api.get('/community/posts', { params: { placeName, size: 6 } }))).map(toCommunityCard), [])

export const deleteAdminPost = async (postId) => {
  const { data } = await api.delete(`/community/posts/${postId}`)
  return data
}

export const getAdminNotices = fallback(async (page = 1, size = 20) =>
  unwrap(await api.get('/notices', { params: { page, size } })), { items: [], page: 1, size: 20, totalCount: 0 })

export const createNotice = async (title, content) => {
  const { data } = await api.post('/admin/notices', { title, content })
  return data
}

export const updateNotice = async (noticeId, title, content) => {
  const { data } = await api.patch(`/admin/notices/${noticeId}`, { title, content })
  return data
}

export const deleteNotice = async (noticeId) => {
  const { data } = await api.delete(`/admin/notices/${noticeId}`)
  return data
}

export const getAdmissionFees = fallback(async (page = 1, size = 20) =>
  unwrap(await api.get('/admin/operation-data/admission-fees', { params: { page, size } })), { items: [], page: 1, size: 20, totalCount: 0 })

export const getPlaceAggregates = fallback(async (page = 1, size = 20, filters = {}) =>
  unwrap(await api.get('/admin/operation-data/place-aggregates', {
    params: {
      page,
      size,
      trimPercent: filters.trimPercent,
      minSamples: filters.minSamples,
      minCost: filters.minCost ?? undefined,
      maxCost: filters.maxCost ?? undefined,
      minDuration: filters.minDuration ?? undefined,
      maxDuration: filters.maxDuration ?? undefined,
    },
  })), { items: [], page: 1, size: 20, totalCount: 0 })

export const createAdmissionFee = async (placeId, feeType, fee) => {
  const { data } = await api.post('/admin/operation-data/admission-fees', { placeId, feeType, fee })
  return data
}

export const deleteAdmissionFee = async (feeId) => {
  const { data } = await api.delete(`/admin/operation-data/admission-fees/${feeId}`)
  return data
}

export const getBlacklist = fallback(async (page = 1, size = 20) =>
  unwrap(await api.get('/admin/operation-data/franchise-blacklist', { params: { page, size } })), { items: [], page: 1, size: 20, totalCount: 0 })

export const createBlacklistBrand = async (brandName) => {
  const { data } = await api.post('/admin/operation-data/franchise-blacklist', { brandName })
  return data
}

export const deleteBlacklistBrand = async (id) => {
  const { data } = await api.delete(`/admin/operation-data/franchise-blacklist/${id}`)
  return data
}

// ── File Upload ────────────────────────────────────────
const BACKEND_ORIGIN = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api')
  .replace(/\/api$/, '')

export const toAbsoluteUrl = (url) => {
  if (!url) return url
  if (url.startsWith('http')) return url
  return BACKEND_ORIGIN + url
}

export const uploadImage = async (file) => {
  const form = new FormData()
  form.append('file', file)
  const { data } = await api.post('/files/upload', form, { headers: { 'Content-Type': 'multipart/form-data' } })
  const url = data?.data?.url ?? null
  return url ? toAbsoluteUrl(url) : null
}

export { mock }
