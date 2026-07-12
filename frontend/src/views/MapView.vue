<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import AppIcon    from '../components/ui/AppIcon.vue'
import TrendMeter from '../components/ui/TrendMeter.vue'
import { PLACES as MOCK_PLACES, CATS, CAT_HUE } from '../data/mock.js'
import { getTourDetail, getRegionInfo, streamPlaces, getPostsByPlace } from '../api/gadang.js'
import { requestLocation, setLocationName } from '../utils/geo.js'

const router = useRouter()
const route  = useRoute()

// 백엔드 RealScoredPlaceProvider.CACHE_VERSION과 별개로 관리 — 프론트 캐시 스키마(필드 추가 등)나
// 백엔드 스코어링 알고리즘이 바뀔 때마다 올려서 브라우저에 박제된 옛 결과를 무효화한다.
const MAP_CACHE_VERSION = 3

/* ── reactive state ─────────────────────────────────────── */
const mapEl    = ref(null)
const listEl   = ref(null)   // 왼쪽 목록 컨테이너
const filter   = ref('all')
const q        = ref('')
const sel      = ref(null)
const favs     = ref([])
const allPlaces = ref([])   // 전체 카테고리 통합 결과 (지도 마커 소스)
const loading   = ref(false)
const mockMode  = ref(false)
const topPercent = ref(45)   // 카테고리 내 인기 상위 N% — 15(엄선)/30/45(다양하게) 선택 가능
const MAP_PLACE_RADIUS = 20000
const placePosts = ref([])
const placePostsLoading = ref(false)

/* ── non-reactive map handles ───────────────────────────── */
let _map  = null
let _ps   = null
let _mMap = new Map()   // place.id → { overlay, div, span, place }

/* ── region / GPS ───────────────────────────────────────── */
// 홈에서 한글 지역명("경주")과 hub(도착 역/터미널)를 query로 넘김
const regionName = computed(() => route.query.region || '')
const hubName    = computed(() => route.query.hub || '')
const regionCoord = ref(null)   // 지역명을 카카오로 지오코딩한 [lat, lng]
const gpsCoords  = ref(null)    // [lat, lng] — GPS로 받은 좌표
const gpsLoading = ref(false)
const isGPSMode  = computed(() => !regionName.value)

// 실제 지도 중심 좌표: 선택 지역(지오코딩) > GPS > 서울 기본값
const searchCenter = computed(() =>
  regionCoord.value ?? gpsCoords.value ?? [37.5665, 126.9780])

const gpsRegionName = ref('')   // GPS 좌표를 역지오코딩한 현재 지역명

// 헤더 표시용 지역명 — GPS 모드여도 실제 지역명으로
const locationLabel = computed(() =>
  regionName.value || gpsRegionName.value || (gpsCoords.value ? '현재 위치' : '위치 확인 중…'))

// 좌표 → 시·군 이름 (카카오 역지오코딩)
function reverseGeocode(lat, lng) {
  return new Promise(resolve => {
    if (!window.kakao?.maps?.services) { resolve(''); return }
    new kakao.maps.services.Geocoder().coord2RegionCode(lng, lat, (res, status) => {
      if (status === kakao.maps.services.Status.OK && res.length) {
        const r = res.find(x => x.region_type === 'H') || res[0]
        // 광역시·특별시는 시 단위 유지("부산광역시 강서구" → "부산")
        // 도는 시/군 단위 사용("전라남도 순천시" → "순천")
        const depth1 = r.region_1depth_name || ''
        const isMetro = /(특별시|광역시|특별자치시)$/.test(depth1)
        let name = depth1.replace(/(특별시|광역시|특별자치시|특별자치도|도)$/, '')
        if (!isMetro && r.region_2depth_name) {
          name = r.region_2depth_name.split(' ')[0].replace(/[시군구]$/, '')
        }
        resolve(name)
      } else resolve('')
    })
  })
}

/* ── 최근 본 지역 + 관심 지역 (지도 탭 빠른 전환) ───────── */
const recentRegions = ref(JSON.parse(localStorage.getItem('gadang_recent') || '[]'))
const pinnedRegions = ref(JSON.parse(localStorage.getItem('gadang_pinned') || '[]'))

// 핀(관심)을 앞에, 최근을 뒤에 — 중복 제거, 현재 지역 제외
const quickRegions = computed(() => {
  const seen = new Set()
  const out = []
  for (const r of [...pinnedRegions.value, ...recentRegions.value]) {
    if (r && !seen.has(r)) { seen.add(r); out.push(r) }
  }
  return out
})

function pushRecent(name) {
  if (!name) return
  const list = [name, ...recentRegions.value.filter(r => r !== name)].slice(0, 10)
  recentRegions.value = list
  localStorage.setItem('gadang_recent', JSON.stringify(list))
}
function togglePin(name) {
  const has = pinnedRegions.value.includes(name)
  pinnedRegions.value = has
    ? pinnedRegions.value.filter(r => r !== name)
    : [...pinnedRegions.value, name]
  localStorage.setItem('gadang_pinned', JSON.stringify(pinnedRegions.value))
}
const isPinned = (name) => pinnedRegions.value.includes(name)

function switchRegion(name) {
  if (name === regionName.value) return
  router.push({ path: '/map', query: { region: name } })
}

// 카카오 키워드 검색으로 지역 중심 좌표 얻기 (SDK 로드 후 호출, 결과는 캐시)
function geocodeKeyword(kw) {
  const cacheKey = 'gadang_geo_' + kw
  const cached = sessionStorage.getItem(cacheKey)
  if (cached) return Promise.resolve(JSON.parse(cached))
  return new Promise(resolve => {
    if (!window.kakao?.maps?.services) { resolve(null); return }
    new kakao.maps.services.Places().keywordSearch(kw, (res, status) => {
      if (status === kakao.maps.services.Status.OK && res[0]) {
        const c = [parseFloat(res[0].y), parseFloat(res[0].x)]
        sessionStorage.setItem(cacheKey, JSON.stringify(c))
        resolve(c)
      } else resolve(null)
    })
  })
}

/* ── 필터 칩 선택 스타일 (카테고리별 컬러) ──────────────── */
function chipStyle(f) {
  if (filter.value !== f.k) return {}
  const color = f.k === 'all' ? 'var(--accent)' : (CAT_HUE[f.k] ?? 'var(--accent)')
  return {
    background: `color-mix(in oklch, ${color} 14%, var(--card))`,
    borderColor: color,
    color,
    fontWeight: '600',
  }
}

/* ── GPS 위치 조회 ───────────────────────────────────────── */
// App.vue가 진입 시점에 이미 선요청했다면 여기서는 캐시를 즉시 재사용한다 —
// 서버 재시작 직후 첫 진입과 재진입의 결과가 달랐던 건 이 컴포넌트가 별도로
// 새 측위를 돌렸기 때문이라 공유 모듈(requestLocation)로 단일화한다.
async function resolveCenter() {
  // 지역 선택됨 → 지역명만으로 중심 좌표 (hub는 중심에 영향 주지 않음 → hub만 달라도 재로딩 없음)
  if (regionName.value) {
    regionCoord.value = await geocodeKeyword(regionName.value)
    return
  }
  gpsLoading.value = true
  const loc = await requestLocation()
  if (!loc) {
    // GPS 거부·타임아웃 — searchCenter는 이미 서울 기본값 좌표로 폴백하므로
    // gpsCoords도 같이 채워서 좌표 표시·지역명 라벨이 "위치 확인 중…"에 영원히
    // 멈춰있지 않게 한다 (searchCenter 폴백과 화면 표시를 일치시킴).
    const fallback = [37.5665, 126.9780]
    gpsCoords.value = fallback
    gpsRegionName.value = (await reverseGeocode(fallback[0], fallback[1])) || '서울'
    gpsLoading.value = false
    return
  }

  gpsCoords.value = loc.c
  if (loc.n) {
    gpsRegionName.value = loc.n
  } else {
    gpsRegionName.value = await reverseGeocode(loc.c[0], loc.c[1])
    if (gpsRegionName.value) setLocationName(gpsRegionName.value)
  }
  gpsLoading.value = false
}

/* ── filter ─────────────────────────────────────────────── */
const FILTERS = [
  { k: 'all',     l: '전체' },
  { k: 'sight',   l: '관광명소' },
  { k: 'food',    l: '음식점' },
  { k: 'cafe',    l: '카페' },
  { k: 'culture', l: '문화·전시' },
  { k: 'photo',   l: '포토스팟' },
  { k: 'park',    l: '공원' },
  { k: 'shop',    l: '쇼핑' },
]

const CODE_TO_CAT = { AT4: 'sight', CT1: 'culture', FD6: 'food', CE7: 'cafe' }

/* ── 왼쪽 목록: allPlaces를 필터·검색어로 좁힘 ──────────── */
const list = computed(() => {
  let r = allPlaces.value
  if (filter.value !== 'all') r = r.filter(p => p.cat === filter.value)
  if (q.value) r = r.filter(p => p.name.includes(q.value) || (p.addr ?? '').includes(q.value))
  return r
})

/* ── mock 지도 마커 위치 ─────────────────────────────────── */
function mockPos(p) {
  const lats = allPlaces.value.map(x => x.lat)
  const lngs = allPlaces.value.map(x => x.lng)
  const minLat = Math.min(...lats), maxLat = Math.max(...lats)
  const minLng = Math.min(...lngs), maxLng = Math.max(...lngs)
  const x = (p.lng - minLng) / ((maxLng - minLng) || 1)
  const y = (maxLat - p.lat) / ((maxLat - minLat) || 1)
  return { left: (10 + x * 78) + '%', top: (14 + y * 70) + '%' }
}

/* ── Kakao SDK 로더 ──────────────────────────────────────── */
function loadKakao() {
  return new Promise((resolve, reject) => {
    if (window.kakao?.maps) { resolve(); return }
    const s = document.createElement('script')
    s.src = `//dapi.kakao.com/v2/maps/sdk.js?appkey=${import.meta.env.VITE_KAKAO_MAP_KEY}&libraries=services&autoload=false`
    s.onload  = () => window.kakao.maps.load(resolve)
    s.onerror = () => reject(new Error('SDK load failed'))
    document.head.appendChild(s)
  })
}

/* ── 지도 초기화 ─────────────────────────────────────────── */
function initMap() {
  const [lat, lng] = searchCenter.value
  _map = new kakao.maps.Map(mapEl.value, {
    center: new kakao.maps.LatLng(lat, lng),
    level: 7,
  })
  _ps = new kakao.maps.services.Places()
}

/* ── 마커 ────────────────────────────────────────────────── */
function applyMarkerStyle(div, span, place, selected) {
  const color = place.pending ? 'var(--ink-faint)' : (CAT_HUE[place.cat] ?? 'var(--accent)')
  const size  = selected ? '34px' : '24px'
  div.style.cssText = [
    `display:grid;place-items:center`,
    `width:${size};height:${size}`,
    `border-radius:50% 50% 2px 50%`,   // rotate(45deg) 시 BR(2px)이 아래 = 핀 방향
    `transform:rotate(45deg)`,
    `background:${selected ? color : 'var(--card)'}`,
    `border:2px ${place.pending ? 'dashed' : 'solid'} ${color}`,
    `box-shadow:${selected ? `0 3px 12px rgba(0,0,0,.32)` : '0 2px 8px rgba(0,0,0,.22)'}`,
    `cursor:pointer;transition:all .12s`,
    `pointer-events:all`,
    `opacity:${place.pending ? '0.55' : '1'}`,
  ].join(';')
  span.style.cssText = [
    `transform:rotate(-45deg)`,
    `font-size:${selected ? '13px' : '10px'}`,
    `color:${selected ? '#fff' : color}`,
    `display:block;line-height:1`,
  ].join(';')
}

function clearMarkers() {
  _mMap.forEach(({ overlay }) => overlay.setMap(null))
  _mMap.clear()
}

function addMarker(place) {
  const div  = document.createElement('div')
  const span = document.createElement('span')
  span.textContent = CATS[place.cat]?.emoji ?? '●'
  div.appendChild(span)
  applyMarkerStyle(div, span, place, false)

  div.addEventListener('click', (e) => {
    e.stopPropagation()
    sel.value = place
  })

  const overlay = new kakao.maps.CustomOverlay({
    position: new kakao.maps.LatLng(place.lat, place.lng),
    content: div, yAnchor: 1.3, zIndex: 1,
  })
  overlay.setMap(_map)
  _mMap.set(place.id, { overlay, div, span, place })
}

function refreshMarkerStyles() {
  _mMap.forEach(({ div, span, place, overlay }, id) => {
    const isSelected = sel.value?.id === id
    applyMarkerStyle(div, span, place, isSelected)
    overlay.setZIndex(isSelected ? 5 : 1)
  })
}

// 필터에 따라 해당 카테고리 마커만 지도에 표시
function updateMarkersVisibility() {
  _mMap.forEach(({ overlay, place }) => {
    const visible = filter.value === 'all' || place.cat === filter.value
    overlay.setMap(visible ? _map : null)
  })
}

/* ── 백엔드 스코어링 후보 로드 (지도 최초 로드 시 1회) ─────
   카카오 직접 검색 대신 /api/places/suggest 사용 —
   프랜차이즈 제외 + 블로그 인기순 + 노이즈 컷 + 서브지역/hub 커버된 후보만 띄움
   (코스 추천과 동일한 장소 풀) */
const CAT_CODE_TO_KEY = { AT4: 'sight', CT1: 'culture', FD6: 'food', CE7: 'cafe', MT1: 'shop' }

// 첫 조회(캐시 미스)는 카카오+네이버 블로그 스코어링이 실시간으로 돌아 수십 초 걸릴 수 있음 —
// 회전 메시지로 "멈춘 게 아니라 진행 중"임을 보여준다 (HomeView와 동일 패턴)
const PLACE_LOADING_STEPS = [
  '주변 장소 찾는 중',
  '카테고리별 후보 수집 중',
  '블로그 인기도 분석 중',
  '노이즈 제거하는 중',
  '거의 다 됐어요',
]
const placeLoadingMsg = ref(PLACE_LOADING_STEPS[0])
let _placeLoadingTimer = null
function startPlaceLoadingMsg() {
  let i = 0
  placeLoadingMsg.value = PLACE_LOADING_STEPS[0]
  _placeLoadingTimer = setInterval(() => {
    i = Math.min(i + 1, PLACE_LOADING_STEPS.length - 1)
    placeLoadingMsg.value = PLACE_LOADING_STEPS[i]
  }, 3000)
}
function stopPlaceLoadingMsg() {
  if (_placeLoadingTimer) { clearInterval(_placeLoadingTimer); _placeLoadingTimer = null }
}

function setTopPercent(p) {
  if (topPercent.value === p) return
  topPercent.value = p
  if (!isGPSMode.value || gpsCoords.value) loadAllPlaces()
}

function toPlaceRow(p) {
  return {
    id:   p.id || `${p.name}:${p.lat}:${p.lng}`,
    name: p.name,
    addr: p.address || '',
    cat:  p.subCategory || CAT_CODE_TO_KEY[p.category] || 'sight',
    fee: 0, feeType: 'free',
    trend: Math.round(p.finalScore ?? 0),   // 백엔드 실제 점수 (랜덤 아님). 스트리밍 중 partial은 아직 0
    dist: '',
    lat:  p.lat,
    lng:  p.lng,
    url:  p.id ? `https://place.map.kakao.com/${p.id}` : '',
    phone: '',
    stay: p.stayMinutes ?? (CATS[CAT_CODE_TO_KEY[p.category]]?.stay ?? 60),
    pending: false,
  }
}

let _stopStream = null   // 진행 중인 SSE 구독 해제 함수

function finishLoad(merged) {
  loading.value = false
  stopPlaceLoadingMsg()
  if (merged.length) {
    const bounds = new kakao.maps.LatLngBounds()
    merged.forEach(p => bounds.extend(new kakao.maps.LatLng(p.lat, p.lng)))
    _map.setBounds(bounds)
    sel.value = list.value[0] ?? merged[0]
  }
  focusQueriedPlace()
}

async function loadAllPlaces() {
  if (!_map) return
  if (_stopStream) { _stopStream(); _stopStream = null }
  loading.value   = true
  startPlaceLoadingMsg()
  allPlaces.value = []
  sel.value       = null
  clearMarkers()

  // 선택했던 지역+인기도 기준이면 캐시에서 즉시 (백엔드 재호출 없이)
  // MAP_CACHE_VERSION: 백엔드 스코어링/구역 로직이 바뀌면 올린다 — 안 올리면 브라우저
  // sessionStorage에 옛 알고리즘 결과가 탭이 살아있는 동안 무한정 박제된다(실제 발견된 버그).
  // GPS 모드에서도 역지오코딩된 지역명이 있으면 region 기반 요청 → 홈/코스 탭과 캐시 공유
  const effectiveRegion = regionName.value || gpsRegionName.value || undefined
  const cacheKey = `gadang_map_v${MAP_CACHE_VERSION}_${effectiveRegion || 'unknown'}_${topPercent.value}`
  if (effectiveRegion) {
    const cached = sessionStorage.getItem(cacheKey)
    if (cached) {
      try {
        const merged = JSON.parse(cached)
        allPlaces.value = merged
        merged.forEach(addMarker)
        finishLoad(merged)
        return
      } catch {}
    }
  }

  // SSE 스트리밍 — 구역(zone)이 끝날 때마다 마커를 바로 찍고(점수는 집계 전), 전체 채점이
  // 끝나면 최종 결과로 교체. 캐시 미스인 큰 지역은 1~2분 걸릴 수 있어 "빈 화면 대기"를 없앤다.
  const [lat, lng] = searchCenter.value
  const seenIds = new Set()
  let foundCount = 0

  _stopStream = streamPlaces(
    { lat, lng, region: effectiveRegion, radius: MAP_PLACE_RADIUS, topPercent: topPercent.value },
    {
      onPartial: (rows) => {
        const fresh = (rows || [])
          .map(toPlaceRow)
          .filter(p => !seenIds.has(p.id))
        if (!fresh.length) return
        fresh.forEach(p => { seenIds.add(p.id); p.pending = true })
        allPlaces.value = [...allPlaces.value, ...fresh]
        fresh.forEach(addMarker)
        foundCount += fresh.length
        stopPlaceLoadingMsg()
        placeLoadingMsg.value = `${foundCount}곳 찾음 · 인기도 집계 중…`
      },
      onComplete: (rows) => {
        const merged = (rows || []).map(toPlaceRow)
        clearMarkers()
        allPlaces.value = merged
        merged.forEach(addMarker)
        if (effectiveRegion && merged.length) {
          try { sessionStorage.setItem(cacheKey, JSON.stringify(merged)) } catch {}
        }
        _stopStream = null
        finishLoad(merged)
      },
      onError: () => {
        _stopStream = null
        loading.value = false
        stopPlaceLoadingMsg()
        // 스트림 중 끊겨도 지금까지 찾은 건 그대로 둔다 (빈 화면보다 낫다)
        if (allPlaces.value.length) finishLoad(allPlaces.value)
      },
    },
  )
}

/* ── mock 모드 ───────────────────────────────────────────── */
function loadMock() {
  allPlaces.value = MOCK_PLACES.map(p => ({
    ...p, url: '', phone: '', stay: CATS[p.cat]?.stay ?? 60,
  }))
  sel.value = list.value[0] ?? allPlaces.value[0] ?? null
  focusQueriedPlace()
}

/* ── AI 챗봇 '장소 상세' 버튼으로 들어온 경우: 해당 장소 선택·포커스 ──
   query: place(이름) / lat / lng / cat(카카오 코드). 후보에 없으면 합성 마커 생성 */
function focusQueriedPlace() {
  const name = route.query.place
  if (!name) return
  const qlat = Number(route.query.lat)
  const qlng = Number(route.query.lng)

  let match = allPlaces.value.find(p => p.name === name)
  if (!match && Number.isFinite(qlat) && Number.isFinite(qlng)) {
    // 이름이 안 맞으면 좌표상 가장 가까운(≈300m 이내) 후보로
    let best = null, bestD = Infinity
    for (const p of allPlaces.value) {
      const d = (p.lat - qlat) ** 2 + (p.lng - qlng) ** 2
      if (d < bestD) { bestD = d; best = p }
    }
    if (best && bestD < 1e-5) match = best
  }
  if (!match && Number.isFinite(qlat) && Number.isFinite(qlng)) {
    // 후보 풀에 없으면 합성 장소를 만들어 표시
    match = {
      id: `ai:${name}`, name, addr: '',
      cat: CODE_TO_CAT[route.query.cat] || 'sight',
      fee: 0, feeType: 'free', trend: 0, dist: '',
      lat: qlat, lng: qlng, url: '', phone: '',
      stay: CATS[CODE_TO_CAT[route.query.cat] || 'sight']?.stay ?? 60,
    }
    allPlaces.value = [match, ...allPlaces.value]
    if (!mockMode.value && _map) addMarker(match)
  }
  if (match) {
    sel.value = match
    if (!mockMode.value && _map) {
      _map.panTo(new kakao.maps.LatLng(match.lat, match.lng))
    }
  }
}

/* ── 지역 정보 (날씨 + 행사) ─────────────────────────────── */
const regionInfo = ref(null)
const festScroll = ref(null)

function scrollFest(dir) {
  festScroll.value?.scrollBy({ left: dir * 240, behavior: 'smooth' })
}
// 행사 클릭 → 네이버 검색으로 상세 정보 (일정·요금·프로그램 등)
function openFestival(f) {
  const q = encodeURIComponent(f.title + ' ' + (f.addr || '').split(' ')[0])
  window.open(`https://search.naver.com/search.naver?query=${q}`, '_blank', 'noopener')
}

async function fetchRegionInfo() {
  if (regionName.value) {
    regionInfo.value = await getRegionInfo(regionName.value, null, null)
  } else if (gpsRegionName.value) {
    // GPS 모드도 해석된 지역명(예: 부산)으로 조회 — 좌표(구 단위)면 축제가 비어버림
    regionInfo.value = await getRegionInfo(gpsRegionName.value, gpsCoords.value?.[0], gpsCoords.value?.[1])
  } else if (gpsCoords.value) {
    regionInfo.value = await getRegionInfo(null, gpsCoords.value[0], gpsCoords.value[1])
  }
}

function formatDate(d) {
  if (!d || d.length < 8) return d
  return `${d.slice(0, 4)}.${d.slice(4, 6)}.${d.slice(6, 8)}`
}

/* ── 관광공사 상세 (백엔드 프록시) ──────────────────────── */
const tourDetail  = ref(null)
const tourLoading = ref(false)

async function fetchTourDetail(place) {
  tourDetail.value  = null
  tourLoading.value = true
  tourDetail.value  = await getTourDetail(place.name, place.cat)
  tourLoading.value = false
}

/* ── 로드뷰 폴백 ─────────────────────────────────────────────
   카카오 장소 검색 API는 사진을 안 줌 (TourAPI 매칭된 명소만 tourDetail.img 존재).
   대부분의 카카오 발굴 장소(식당/카페 등)는 사진이 없으므로 그 좌표의 로드뷰를 대신 띄운다. */

/* ── 즐겨찾기 ────────────────────────────────────────────── */
function toggleFav(id) {
  favs.value = favs.value.includes(id)
    ? favs.value.filter(x => x !== id)
    : [...favs.value, id]
}

/* ── 코스 담기 — "코스에 담기"를 눌러도 뭐가 담겼는지 안 보이던 문제 →
   localStorage 기반 카트 + 헤더에서 바로 펼쳐보는 패널 추가 */
const CART_KEY = 'gadang_course_cart'
const courseCart = ref([])
try { courseCart.value = JSON.parse(localStorage.getItem(CART_KEY) || '[]') } catch {}
const cartOpen = ref(false)

watch(courseCart, (v) => {
  try { localStorage.setItem(CART_KEY, JSON.stringify(v)) } catch {}
}, { deep: true })

function isInCart(id) {
  return courseCart.value.some(p => p.id === id)
}
function toggleCart(place) {
  if (!place) return
  courseCart.value = isInCart(place.id)
    ? courseCart.value.filter(p => p.id !== place.id)
    : [...courseCart.value, {
        id: place.id, name: place.name, cat: place.cat,
        lat: place.lat, lng: place.lng, addr: place.addr,
      }]
}
function removeFromCart(id) {
  courseCart.value = courseCart.value.filter(p => p.id !== id)
}
function clearCart() {
  courseCart.value = []
}

function goCourseWithCart() {
  router.push({
    path: '/course',
    query: {
      ...route.query,
      fromMapCart: '1',
      region: regionName.value,
    },
  })
}

function openPlace(url) {
  if (url) window.open(url, '_blank', 'noopener')
}

/* ── 라이프사이클 ────────────────────────────────────────── */
onMounted(async () => {
  try {
    await loadKakao()          // SDK 먼저 (지오코딩에 services 필요)
    await resolveCenter()      // 그 다음 지역명 → 좌표 해결
    if (!mapEl.value) return
    initMap()
    await loadAllPlaces()
    fetchRegionInfo()
    pushRecent(regionName.value)
  } catch {
    mockMode.value = true
    loadMock()
  }
})

// 칩으로 지역 전환 시 (query.region 변경) 좌표·후보 재로딩
watch(() => route.query.region, async () => {
  if (!_map) return
  regionCoord.value = null
  await resolveCenter()
  await loadAllPlaces()
  fetchRegionInfo()
  pushRecent(regionName.value)
})

// 같은 지역에서 다른 장소 버튼 클릭 시 (query.place 변경) 재로딩 없이 포커스만
watch(() => route.query.place, () => focusQueriedPlace())

onUnmounted(() => {
  if (_stopStream) { _stopStream(); _stopStream = null }
  clearMarkers()
  stopPlaceLoadingMsg()
  _map = null
  _ps  = null
})

// 필터 변경 → API 재호출 없음, 마커 가시성 + 목록 첫 항목 자동 선택
watch(filter, () => {
  q.value = ''
  if (!mockMode.value) updateMarkersVisibility()
  const first = list.value[0]
  if (first && first.id !== sel.value?.id) sel.value = first
})

// 선택 변경 → 지도 센터 이동 + 마커 스타일 갱신 + 왼쪽 목록 스크롤 + TourAPI 조회
watch(sel, (newSel) => {
  if (!newSel) return
  if (!mockMode.value && _map) {
    _map.setCenter(new kakao.maps.LatLng(newSel.lat, newSel.lng))
    refreshMarkerStyles()
  }
  fetchTourDetail(newSel)
  // 해당 장소 포함 커뮤니티 게시글 로드
  placePosts.value = []
  placePostsLoading.value = true
  getPostsByPlace(newSel.name).then(posts => {
    placePosts.value = posts
    placePostsLoading.value = false
  })
  // 왼쪽 목록에서 해당 항목으로 스크롤
  nextTick(() => {
    const container = listEl.value
    const item = container?.querySelector(`[data-place-id="${newSel.id}"]`)
    if (!container || !item) return
    const itemTop    = item.offsetTop - container.offsetTop
    const itemBottom = itemTop + item.offsetHeight
    const isAbove    = itemTop < container.scrollTop
    const isBelow    = itemBottom > container.scrollTop + container.clientHeight
    if (isAbove || isBelow) {
      container.scrollTop = itemTop - container.clientHeight / 3
    }
  })
})
</script>

<template>
  <div class="screen-wrap" style="max-width:1240px">

    <!-- Header -->
    <header class="cover" style="margin-bottom:22px">
      <div class="cover-meta">
        <span class="eyebrow">
          {{ isGPSMode ? 'GPS 현재 위치' : '선택 지역' }} ·
          {{ locationLabel.toUpperCase() }}
        </span>
        <span class="coords">
          <span v-if="isGPSMode && gpsCoords">
            N {{ gpsCoords[0].toFixed(2) }}° E {{ gpsCoords[1].toFixed(2) }}°
          </span>
          <span v-else-if="regionName">{{ regionName }}</span>
          <span v-else>위치 확인 중…</span>
        </span>
      </div>
      <div style="display:flex;align-items:flex-end;justify-content:space-between;gap:24px;flex-wrap:wrap">
        <h1 class="disp" style="font-size:clamp(34px,5vw,60px)">
          {{ locationLabel }}
        </h1>
        <div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap">
          <span v-if="!isGPSMode" class="badge badge-confirm" style="border-radius:4px">
            <AppIcon name="pin" style="width:12px;height:12px" />
            {{ regionName }}
          </span>
          <span v-if="isGPSMode" class="badge badge-confirm" style="border-radius:4px">
            <AppIcon name="pin" style="width:12px;height:12px" />
            {{ gpsLoading ? 'GPS 확인 중…' : 'GPS 기반' }}
          </span>
          <span class="badge badge-trend" style="border-radius:4px">
            <AppIcon name="heartFill" style="width:11px;height:11px" />
            즐겨찾기 {{ favs.length }}
          </span>
          <span class="badge" style="border-radius:4px;background:var(--card)">
            총 {{ allPlaces.length }}곳
          </span>

          <!-- 코스 카트 — 담은 장소가 뭔지 바로 펼쳐볼 수 있게 -->
          <div style="position:relative">
            <button class="badge" style="border-radius:4px;cursor:pointer;border:none"
                    :style="courseCart.length ? 'background:var(--accent-wash);color:var(--accent-deep)' : 'background:var(--card)'"
                    @click="cartOpen = !cartOpen">
              <AppIcon name="route" style="width:11px;height:11px" />
              담은 장소 {{ courseCart.length }}
            </button>
            <div v-if="cartOpen" class="card"
                 style="position:absolute;top:calc(100% + 6px);right:0;width:280px;z-index:20;padding:12px;box-shadow:0 8px 24px rgba(0,0,0,.14)">
              <div v-if="!courseCart.length" style="font-size:12.5px;color:var(--ink-faint);text-align:center;padding:14px 0">
                담은 장소가 없어요. 장소를 선택하고 "코스에 담기"를 눌러보세요.
              </div>
              <template v-else>
                <div style="display:flex;flex-direction:column;gap:6px;max-height:280px;overflow-y:auto">
                  <div v-for="p in courseCart" :key="p.id"
                       style="display:flex;align-items:center;gap:8px;padding:6px 4px;border-bottom:1px solid var(--line)">
                    <span style="font-size:15px">{{ CATS[p.cat]?.emoji ?? '●' }}</span>
                    <span style="flex:1;font-size:12.5px;font-weight:600;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ p.name }}</span>
                    <button class="btn btn-ghost btn-sm" style="padding:2px 6px;color:var(--ink-faint)" @click="removeFromCart(p.id)">✕</button>
                  </div>
                </div>
                <div style="display:flex;gap:6px;margin-top:10px">
                  <button class="btn btn-outline btn-sm" style="flex:1" @click="clearCart">비우기</button>
                  <button class="btn btn-primary btn-sm" style="flex:1" @click="goCourseWithCart">코스 짜러 가기</button>
                </div>
              </template>
            </div>
          </div>

          <button class="btn btn-primary btn-sm" style="border-radius:4px" @click="goCourseWithCart">
            <AppIcon name="route" style="width:15px;height:15px" /> 이 장소들로 코스 짜기
          </button>
        </div>
      </div>
    </header>

    <!-- 빠른 지역 전환: 관심(핀) + 최근 본 지역 -->
    <div v-if="!isGPSMode || quickRegions.length"
         style="display:flex;align-items:center;gap:8px;flex-wrap:wrap;margin-bottom:16px">
      <button v-if="!isGPSMode" class="rq-chip rq-pinbtn" @click="togglePin(regionName)">
        <AppIcon :name="isPinned(regionName) ? 'heartFill' : 'heart'" style="width:13px;height:13px" />
        {{ isPinned(regionName) ? '관심 지역' : '관심 등록' }}
      </button>
      <span v-if="quickRegions.length" style="font-size:11px;color:var(--ink-faint);margin:0 2px">최근·관심</span>
      <button v-for="r in quickRegions" :key="r"
              class="rq-chip" :class="{ 'rq-on': r === regionName }"
              @click="switchRegion(r)">
        <AppIcon v-if="isPinned(r)" name="heartFill" style="width:10px;height:10px;color:var(--accent-deep)" />
        {{ r }}
      </button>
    </div>

    <!-- 날씨 바 -->
    <div v-if="regionInfo?.weather"
         style="display:flex;align-items:center;gap:14px;padding:12px 18px;background:var(--card);border-radius:12px;margin-bottom:18px;flex-wrap:wrap;border:1px solid var(--line)">
      <span style="font-size:26px;line-height:1">{{ regionInfo.weather.emoji }}</span>
      <div>
        <span style="font-size:18px;font-weight:700">{{ regionInfo.weather.temperature }}</span>
        <span style="font-size:12px;color:var(--ink-faint);margin-left:6px">{{ regionInfo.weather.desc }}</span>
      </div>
      <div style="display:flex;gap:14px;font-size:12px;color:var(--ink-soft);flex-wrap:wrap">
        <span>체감 <b>{{ regionInfo.weather.feelsLike }}</b></span>
        <span>습도 <b>{{ regionInfo.weather.humidity }}</b></span>
        <span>바람 <b>{{ regionInfo.weather.windSpeed }}</b></span>
        <span v-if="regionInfo.weather.precipProb > 0"
              :style="{ color: regionInfo.weather.precipProb >= 60 ? 'var(--transit)' : 'var(--ink-soft)' }">
          강수 <b>{{ regionInfo.weather.precipProb }}%</b>
        </span>
      </div>
      <span style="margin-left:auto;font-size:11px;color:var(--ink-faint)">현재 날씨</span>
    </div>

    <!-- 일주일 날씨 한눈에 -->
    <div v-if="regionInfo?.weather?.forecast?.length"
         style="display:flex;gap:6px;padding:10px 14px;background:var(--card);border-radius:12px;margin-bottom:18px;overflow-x:auto;border:1px solid var(--line)">
      <div v-for="(d, i) in regionInfo.weather.forecast" :key="d.date"
           style="flex:1;min-width:64px;text-align:center;padding:6px 4px;border-radius:8px"
           :style="i === 0 ? 'background:var(--accent-wash)' : ''">
        <div style="font-size:11px;font-weight:600" :style="{ color: i === 0 ? 'var(--accent-deep)' : 'var(--ink-soft)' }">
          {{ i === 0 ? '오늘' : d.dow }}
        </div>
        <div style="font-size:10px;color:var(--ink-faint);margin-top:1px">{{ d.date }}</div>
        <div style="font-size:20px;line-height:1.4;margin-top:2px">{{ d.emoji }}</div>
        <div style="font-size:12px;margin-top:2px">
          <b>{{ d.tempMax }}</b>
          <span style="color:var(--ink-faint)">/{{ d.tempMin }}</span>
        </div>
        <div v-if="d.precipProb > 0" style="font-size:10px;margin-top:2px"
             :style="{ color: d.precipProb >= 60 ? 'var(--transit)' : 'var(--ink-faint)' }">
          💧{{ d.precipProb }}%
        </div>
      </div>
    </div>

    <div style="display:grid;grid-template-columns:minmax(0,400px) minmax(0,1fr);gap:18px;align-items:start">

      <!-- ── Left panel: 지도 + 목록 ───────────────────────── -->
      <div style="display:flex;flex-direction:column;gap:10px">

        <!-- 실제 Kakao Map -->
        <div v-if="!mockMode" class="card" style="overflow:hidden;position:relative;padding:0;border-radius:14px">
          <div ref="mapEl" style="width:100%;height:300px" />
          <!-- 범례 -->
          <div style="position:absolute;bottom:8px;left:8px;display:flex;gap:3px;flex-wrap:wrap;z-index:10;pointer-events:none">
            <span v-for="f in FILTERS.filter(f => f.k !== 'all')" :key="f.k"
                  :style="{
                    display:'inline-flex', alignItems:'center', gap:'3px',
                    fontSize:'9.5px', padding:'2px 6px', borderRadius:'5px',
                    boxShadow:'0 1px 4px rgba(0,0,0,.18)',
                    background: 'rgba(255,255,255,.94)',
                    borderLeft: `3px solid ${CAT_HUE[f.k] ?? 'var(--accent)'}`,
                  }">
              <span>{{ CATS[f.k]?.emoji }}</span>
              <span style="color:#333;font-weight:600">{{ f.l }}</span>
            </span>
          </div>
        </div>

        <!-- 목업 지도 (폴백) -->
        <div v-else class="card" style="overflow:hidden;position:relative;padding:0;border-radius:14px">
          <div style="width:100%;height:300px;background:linear-gradient(160deg,#e8f0e4 0%,#d4e6d0 40%,#c5dfc0 70%,#b8d4b3 100%);position:relative;overflow:hidden">
            <div style="position:absolute;top:45%;left:0;right:0;height:3px;background:rgba(255,255,255,.7);border-radius:2px"></div>
            <div style="position:absolute;top:0;bottom:0;left:38%;width:3px;background:rgba(255,255,255,.7);border-radius:2px"></div>
            <button v-for="p in allPlaces" :key="p.id"
                    @click="sel = p"
                    style="position:absolute;transform:translate(-50%,-100%);cursor:pointer;background:none;border:none;padding:0"
                    :style="{ ...mockPos(p), zIndex: sel?.id === p.id ? 5 : 1 }">
              <span :style="{
                display:'grid', placeItems:'center',
                width:  sel?.id === p.id ? '34px' : '24px',
                height: sel?.id === p.id ? '34px' : '24px',
                borderRadius:'50% 50% 2px 50%',
                transform:'rotate(45deg)',
                background: sel?.id === p.id ? 'var(--accent)' : 'var(--card)',
                border: '2px solid ' + (CAT_HUE[p.cat] || 'var(--accent)'),
                boxShadow:'0 2px 8px rgba(0,0,0,.22)',
                transition:'all .12s',
              }">
                <span :style="{
                  transform:'rotate(-45deg)',
                  fontSize: sel?.id === p.id ? '13px' : '10px',
                  color:    sel?.id === p.id ? 'white' : (CAT_HUE[p.cat] || 'var(--ink)'),
                  display:'block', lineHeight:'1',
                }">{{ CATS[p.cat]?.emoji }}</span>
              </span>
            </button>
            <div style="position:absolute;bottom:8px;left:8px;font-size:10.5px;color:var(--ink-soft);background:rgba(255,255,255,.85);padding:4px 9px;border-radius:7px;font-family:var(--mono)">
              미리보기 지도
            </div>
          </div>
        </div>

        <!-- 검색 -->
        <div style="position:relative">
          <AppIcon name="search" style="position:absolute;left:12px;top:11px;width:17px;height:17px;color:var(--ink-faint)" />
          <input v-model="q" placeholder="장소 이름으로 필터"
                 style="width:100%;box-sizing:border-box;padding:10px 12px 10px 38px;border-radius:11px;border:1px solid var(--line-strong);background:var(--card);font-size:14px;outline:none" />
        </div>

        <!-- Category filter chips -->
        <div style="display:flex;gap:6px;overflow-x:auto;padding-bottom:2px">
          <button v-for="f in FILTERS" :key="f.k"
                  class="chip"
                  style="flex-shrink:0;display:inline-flex;align-items:center;gap:4px"
                  :style="chipStyle(f)"
                  @click="filter = f.k">
            <span v-if="f.k !== 'all'">{{ CATS[f.k]?.emoji }}</span>
            {{ f.l }}
            <span v-if="f.k !== 'all'" style="opacity:.6;font-size:10px">
              {{ allPlaces.filter(p => p.cat === f.k).length }}
            </span>
          </button>
        </div>

        <!-- 인기도 상위 N% 선택 — 카테고리별 검색량 기준 컷 (15=엄선 ~ 45=다양하게) -->
        <div style="display:flex;align-items:center;gap:6px;font-size:11.5px;color:var(--ink-faint)">
          <span>인기도</span>
          <button v-for="p in [15, 30, 45]" :key="p"
                  class="chip"
                  style="flex-shrink:0;padding:4px 10px;font-size:11.5px"
                  :style="topPercent === p ? {
                    background: 'color-mix(in oklch, var(--accent) 14%, var(--card))',
                    borderColor: 'var(--accent)', color: 'var(--accent)', fontWeight: '600',
                  } : {}"
                  @click="setTopPercent(p)">
            상위 {{ p }}%
          </button>
        </div>

        <!-- Mock mode notice -->
        <div v-if="mockMode" style="padding:8px 12px;border-radius:8px;background:var(--accent-wash);font-size:11.5px;color:var(--ink-soft)">
          미리보기 모드 — 실제 브라우저 + 카카오 도메인 등록 시 실제 장소가 표시됩니다.
        </div>

        <!-- Loading -->
        <div v-if="loading" style="padding:32px;text-align:center;color:var(--ink-faint);font-size:13px">
          {{ placeLoadingMsg }}
          <div style="font-size:11px;margin-top:5px;color:var(--ink-faint)">
            처음 조회하는 지역은 실시간 분석으로 최대 30초까지 걸릴 수 있어요
          </div>
        </div>

        <!-- Empty -->
        <div v-else-if="!list.length" style="padding:24px;text-align:center;color:var(--ink-faint);font-size:13px">
          해당 카테고리 장소가 없습니다.
        </div>

        <!-- Place list -->
        <div v-else ref="listEl" style="display:flex;flex-direction:column;gap:8px;max-height:420px;overflow-y:auto;padding-right:2px">
          <button v-for="p in list" :key="p.id"
                  :data-place-id="p.id"
                  class="card"
                  style="padding:11px;display:flex;gap:11px;align-items:center;text-align:left;cursor:pointer;transition:all .12s;width:100%;box-sizing:border-box"
                  :style="{
                    border: '1.5px solid ' + (sel?.id === p.id ? (CAT_HUE[p.cat] ?? 'var(--accent)') : 'var(--line)'),
                    background: sel?.id === p.id ? `color-mix(in oklch, ${CAT_HUE[p.cat] ?? 'var(--accent)'} 10%, var(--card))` : 'var(--card)',
                  }"
                  @click="sel = p">
            <div style="flex-shrink:0;width:38px;height:38px;display:grid;place-items:center">
              <span :style="{
                display:'grid', placeItems:'center',
                width:  sel?.id === p.id ? '32px' : '26px',
                height: sel?.id === p.id ? '32px' : '26px',
                borderRadius: '50% 50% 2px 50%',
                transform: 'rotate(45deg)',
                background: sel?.id === p.id ? (CAT_HUE[p.cat] ?? 'var(--accent)') : `color-mix(in oklch, ${CAT_HUE[p.cat] ?? 'var(--accent)'} 15%, var(--card))`,
                border: `2px solid ${CAT_HUE[p.cat] ?? 'var(--accent)'}`,
                boxShadow: '0 1px 4px rgba(0,0,0,.15)',
                transition: 'all .12s',
              }">
                <span :style="{
                  transform: 'rotate(-45deg)',
                  fontSize: sel?.id === p.id ? '13px' : '10px',
                  color: sel?.id === p.id ? '#fff' : (CAT_HUE[p.cat] ?? 'var(--accent)'),
                  display: 'block', lineHeight: '1',
                }">{{ CATS[p.cat]?.emoji }}</span>
              </span>
            </div>
            <div style="flex:1;min-width:0">
              <div style="font-weight:600;font-size:14px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">
                {{ p.name }}
              </div>
              <div style="display:flex;align-items:center;gap:8px;margin-top:3px;font-size:11.5px;color:var(--ink-faint)">
                <span>{{ CATS[p.cat]?.ko }}</span>
                <span>·</span>
                <TrendMeter :score="p.trend" noun="장소" />
              </div>
            </div>
            <span @click.stop="toggleFav(p.id)"
                  style="padding:3px;cursor:pointer;flex-shrink:0"
                  :style="{ color: favs.includes(p.id) ? 'var(--trend)' : 'var(--ink-faint)' }">
              <AppIcon :name="favs.includes(p.id) ? 'heartFill' : 'heart'" style="width:17px;height:17px" />
            </span>
          </button>
        </div>
      </div>

      <!-- ── Right panel: 상세 정보 전용 ───────────────────── -->
      <div style="position:sticky;top:18px">

        <!-- 장소 미선택 -->
        <div v-if="!sel && !loading" class="card"
             style="padding:48px 32px;text-align:center;color:var(--ink-faint);font-size:13px">
          지도 마커 또는 왼쪽 목록에서 장소를 선택하세요.
        </div>

        <!-- 상세 패널 -->
        <div v-if="sel" class="card" :key="sel.id" style="overflow:hidden">
          <!-- 사진 (TourAPI) 또는 기본 그라디언트 -->
          <div style="aspect-ratio:16/9;position:relative;overflow:hidden;background:linear-gradient(135deg,var(--accent-wash) 0%,var(--transit-wash) 100%);display:flex;align-items:center;justify-content:center">
            <img v-if="tourDetail?.img" :src="tourDetail.img" alt=""
                 style="position:absolute;inset:0;width:100%;height:100%;object-fit:cover" />
            <span v-if="!tourDetail?.img"
                  style="font-size:54px;line-height:1;position:relative;z-index:1;filter:drop-shadow(0 2px 8px rgba(0,0,0,.18))">
              {{ CATS[sel.cat]?.emoji }}
            </span>
          </div>
          <div style="padding:16px 18px">
            <div style="display:flex;align-items:flex-start;justify-content:space-between;gap:10px">
              <div style="min-width:0">
                <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap">
                  <span style="font-size:18px;font-weight:700">{{ sel.name }}</span>
                  <span class="badge"
                        :style="{ background: `color-mix(in oklch,${CAT_HUE[sel.cat]} 12%, var(--card))`, color: CAT_HUE[sel.cat] }">
                    {{ CATS[sel.cat]?.emoji }} {{ CATS[sel.cat]?.ko }}
                  </span>
                </div>
                <div style="display:flex;align-items:center;gap:5px;font-size:13px;color:var(--ink-soft);margin-top:6px">
                  <AppIcon name="pin" style="width:14px;height:14px;color:var(--ink-faint);flex-shrink:0" />
                  <span style="word-break:keep-all">{{ sel.addr }}</span>
                </div>
                <div v-if="sel.phone" style="font-size:12px;color:var(--ink-faint);margin-top:3px;padding-left:19px">
                  {{ sel.phone }}
                </div>
              </div>
              <button class="btn btn-outline btn-sm" style="flex-shrink:0"
                      :style="{ color: favs.includes(sel.id) ? 'var(--trend)' : 'var(--ink-soft)' }"
                      @click="toggleFav(sel.id)">
                <AppIcon :name="favs.includes(sel.id) ? 'heartFill' : 'heart'" style="width:15px;height:15px" />
                {{ favs.includes(sel.id) ? '저장됨' : '즐겨찾기' }}
              </button>
            </div>

            <div style="display:grid;grid-template-columns:1fr 1fr 1fr;gap:10px;margin-top:14px;padding:13px 0;border-top:1px solid var(--line);border-bottom:1px solid var(--line)">
              <div>
                <div style="font-size:11px;color:var(--ink-faint);font-weight:600;margin-bottom:6px">예상 체류</div>
                <span class="mono" style="font-weight:700;font-size:14px">{{ sel.stay }}분</span>
              </div>
              <div>
                <div style="font-size:11px;color:var(--ink-faint);font-weight:600;margin-bottom:6px">트렌드</div>
                <span class="mono" style="font-weight:700;font-size:14px;color:var(--trend)">{{ Math.round(sel.trend || 0) }}점</span>
              </div>
              <div>
                <div style="font-size:11px;color:var(--ink-faint);font-weight:600;margin-bottom:6px">거리</div>
                <span class="mono" style="font-weight:700;font-size:14px">{{ sel.dist || '-' }}</span>
              </div>
            </div>

            <!-- 관광공사 상세 -->
            <div v-if="tourLoading" style="margin-top:12px;font-size:12px;color:var(--ink-faint);text-align:center;padding:8px 0">
              상세 정보 불러오는 중…
            </div>
            <div v-else-if="tourDetail" style="margin-top:12px;display:flex;flex-direction:column;gap:10px">

              <!-- 개요 -->
              <p v-if="tourDetail.overview"
                 style="font-size:12.5px;color:var(--ink-soft);line-height:1.65;margin:0">
                {{ tourDetail.overview.length > 200 ? tourDetail.overview.slice(0, 200) + '…' : tourDetail.overview }}
              </p>

              <!-- 운영 정보 테이블 -->
              <div v-if="tourDetail.usetime||tourDetail.usefee||tourDetail.restdate||tourDetail.parking||tourDetail.useseason"
                   style="display:flex;flex-direction:column;gap:4px;padding:10px 12px;background:var(--bg);border-radius:10px;font-size:12px">
                <div v-if="tourDetail.usetime" style="display:flex;gap:8px;align-items:flex-start">
                  <span style="flex-shrink:0;width:52px;color:var(--ink-faint);font-weight:600">운영시간</span>
                  <span style="color:var(--ink);line-height:1.5">{{ tourDetail.usetime }}</span>
                </div>
                <div v-if="tourDetail.restdate" style="display:flex;gap:8px;align-items:flex-start">
                  <span style="flex-shrink:0;width:52px;color:var(--ink-faint);font-weight:600">휴무일</span>
                  <span style="color:var(--ink);line-height:1.5">{{ tourDetail.restdate }}</span>
                </div>
                <div v-if="tourDetail.usefee" style="display:flex;gap:8px;align-items:flex-start">
                  <span style="flex-shrink:0;width:52px;color:var(--ink-faint);font-weight:600">입장료</span>
                  <span style="color:var(--ink);line-height:1.5">{{ tourDetail.usefee }}</span>
                </div>
                <div v-if="tourDetail.parking" style="display:flex;gap:8px;align-items:flex-start">
                  <span style="flex-shrink:0;width:52px;color:var(--ink-faint);font-weight:600">주차</span>
                  <span style="color:var(--ink);line-height:1.5">{{ tourDetail.parking }}</span>
                </div>
                <div v-if="tourDetail.useseason" style="display:flex;gap:8px;align-items:flex-start">
                  <span style="flex-shrink:0;width:52px;color:var(--ink-faint);font-weight:600">이용시기</span>
                  <span style="color:var(--ink);line-height:1.5">{{ tourDetail.useseason }}</span>
                </div>
              </div>

              <!-- 카테고리 특화: 음식점 메뉴 -->
              <div v-if="tourDetail.menu||tourDetail.seat"
                   style="padding:10px 12px;background:var(--bg);border-radius:10px;font-size:12px;display:flex;flex-direction:column;gap:4px">
                <div v-if="tourDetail.menu" style="display:flex;gap:8px;align-items:flex-start">
                  <span style="flex-shrink:0;width:52px;color:var(--ink-faint);font-weight:600">대표메뉴</span>
                  <span style="color:var(--ink);line-height:1.5">{{ tourDetail.menu }}</span>
                </div>
                <div v-if="tourDetail.seat" style="display:flex;gap:8px">
                  <span style="flex-shrink:0;width:52px;color:var(--ink-faint);font-weight:600">좌석수</span>
                  <span style="color:var(--ink)">{{ tourDetail.seat }}석</span>
                </div>
              </div>

              <!-- 체험 안내 -->
              <div v-if="tourDetail.expguide"
                   style="padding:10px 12px;background:var(--bg);border-radius:10px;font-size:12px;display:flex;gap:8px;align-items:flex-start">
                <span style="flex-shrink:0;width:52px;color:var(--ink-faint);font-weight:600">체험안내</span>
                <span style="color:var(--ink);line-height:1.5">{{ tourDetail.expguide }}</span>
              </div>

              <!-- 편의시설 칩 -->
              <div v-if="tourDetail.pet||tourDetail.babycarriage||tourDetail.creditcard||tourDetail.infocenter"
                   style="display:flex;flex-wrap:wrap;gap:6px">
                <span v-if="tourDetail.pet" class="badge"
                      :style="{ background: tourDetail.pet.includes('가능') ? 'var(--free-wash,#e8f5e9)' : 'var(--card)', color: tourDetail.pet.includes('가능') ? '#388e3c' : 'var(--ink-faint)' }">
                  🐾 반려동물 {{ tourDetail.pet.includes('가능') ? '가능' : '불가' }}
                </span>
                <span v-if="tourDetail.babycarriage" class="badge"
                      :style="{ background: tourDetail.babycarriage.includes('가능')||tourDetail.babycarriage.includes('대여') ? 'var(--free-wash,#e8f5e9)' : 'var(--card)', color: tourDetail.babycarriage.includes('가능')||tourDetail.babycarriage.includes('대여') ? '#388e3c' : 'var(--ink-faint)' }">
                  🛒 유모차 {{ tourDetail.babycarriage.includes('가능')||tourDetail.babycarriage.includes('대여') ? '대여가능' : tourDetail.babycarriage }}
                </span>
                <span v-if="tourDetail.creditcard" class="badge"
                      :style="{ background: tourDetail.creditcard.includes('가능') ? 'var(--free-wash,#e8f5e9)' : 'var(--card)', color: tourDetail.creditcard.includes('가능') ? '#388e3c' : 'var(--ink-faint)' }">
                  💳 카드결제 {{ tourDetail.creditcard.includes('가능') ? '가능' : '불가' }}
                </span>
                <span v-if="tourDetail.infocenter" class="badge" style="background:var(--card);color:var(--ink-soft)">
                  📞 {{ tourDetail.infocenter }}
                </span>
              </div>

              <!-- 홈페이지 링크 -->
              <a v-if="tourDetail.homepage" :href="tourDetail.homepage" target="_blank" rel="noopener"
                 style="font-size:12px;color:var(--accent);text-decoration:none;display:inline-flex;align-items:center;gap:4px">
                🔗 공식 홈페이지
              </a>
            </div>

            <div style="display:flex;gap:8px;margin-top:14px">
              <button class="btn btn-ghost btn-sm" style="flex:1" @click="openPlace(sel.url)">
                카카오맵에서 보기
              </button>
              <button class="btn btn-sm" style="flex:1"
                      :class="isInCart(sel.id) ? 'btn-outline' : 'btn-primary'"
                      :style="isInCart(sel.id) ? 'color:var(--accent-deep);border-color:var(--accent-deep)' : ''"
                      @click="toggleCart(sel)">
                <AppIcon :name="isInCart(sel.id) ? 'check' : 'plus'" style="width:15px;height:15px" />
                {{ isInCart(sel.id) ? '담음' : '코스에 담기' }}
              </button>
            </div>
          </div>
        </div>

        <!-- 이 장소가 포함된 커뮤니티 코스 -->
        <div v-if="sel" style="margin-top:14px">
          <div style="font-size:13px;font-weight:700;color:var(--ink);margin-bottom:8px;display:flex;align-items:center;gap:6px">
            <span>📍 이 장소가 담긴 코스</span>
            <span v-if="placePosts.length" class="badge" style="background:var(--accent-wash);color:var(--accent)">{{ placePosts.length }}</span>
          </div>
          <div v-if="placePostsLoading" style="font-size:12px;color:var(--ink-faint);text-align:center;padding:10px 0">불러오는 중…</div>
          <div v-else-if="!placePosts.length" style="font-size:12px;color:var(--ink-faint);padding:10px 0">
            이 장소가 담긴 공유 코스가 아직 없어요.
          </div>
          <div v-else style="display:flex;flex-direction:column;gap:7px">
            <button v-for="post in placePosts" :key="post.id"
                    class="card"
                    style="padding:10px 13px;text-align:left;cursor:pointer;width:100%;box-sizing:border-box"
                    @click="$router.push(`/community/${post.postId ?? post.id}`)">
              <div style="font-weight:600;font-size:13px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">
                {{ post.title }}
              </div>
              <div style="display:flex;gap:10px;margin-top:4px;font-size:11.5px;color:var(--ink-faint)">
                <span>{{ post.author }}</span>
                <span>💰 {{ (post.cost || 0).toLocaleString() }}원</span>
                <span>⏱ {{ post.min }}분</span>
                <span>💬 {{ post.comments }}</span>
              </div>
            </button>
          </div>
        </div>

      </div>
    </div>

    <!-- 행사 · 축제 섹션 -->
    <div v-if="regionInfo?.festivals?.length" style="margin-top:28px">
      <div style="display:flex;align-items:center;gap:8px;margin-bottom:14px">
        <span style="font-size:18px">🎪</span>
        <h2 style="margin:0;font-size:16px;font-weight:700">{{ locationLabel }} 행사 · 축제</h2>
        <span class="badge" style="background:var(--accent-wash);color:var(--accent)">
          {{ regionInfo.festivals.length }}건
        </span>
        <div style="margin-left:auto;display:flex;gap:6px">
          <button class="fest-arrow" @click="scrollFest(-1)" aria-label="이전">‹</button>
          <button class="fest-arrow" @click="scrollFest(1)" aria-label="다음">›</button>
        </div>
      </div>
      <div ref="festScroll" style="display:flex;gap:12px;overflow-x:auto;padding-bottom:8px;scroll-behavior:smooth">
        <div v-for="f in regionInfo.festivals" :key="f.title"
             class="card fest-card" style="flex-shrink:0;width:210px;overflow:hidden;padding:0;cursor:pointer"
             @click="openFestival(f)">
          <div style="height:110px;background:var(--accent-wash);position:relative;overflow:hidden;display:flex;align-items:center;justify-content:center">
            <img v-if="f.img" :src="f.img" alt=""
                 style="position:absolute;inset:0;width:100%;height:100%;object-fit:cover" />
            <span v-else style="font-size:32px">🎪</span>
          </div>
          <div style="padding:10px 12px">
            <div style="font-size:13px;font-weight:600;line-height:1.4;overflow:hidden;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical">
              {{ f.title }}
            </div>
            <div style="font-size:11px;color:var(--ink-faint);margin-top:5px">
              {{ formatDate(f.startDate) }} ~ {{ formatDate(f.endDate) }}
            </div>
            <div v-if="f.addr"
                 style="font-size:11px;color:var(--ink-faint);margin-top:2px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">
              📍 {{ f.addr }}
            </div>
            <div style="font-size:10.5px;color:var(--accent);margin-top:6px;font-weight:600">자세히 보기 →</div>
          </div>
        </div>
      </div>
    </div>

  </div>
</template>

<style scoped>
.rq-chip {
  display: inline-flex; align-items: center; gap: 5px;
  font-size: 12px; font-weight: 600;
  padding: 5px 11px; border-radius: 16px;
  border: 1px solid var(--line); background: var(--card);
  color: var(--ink-soft); cursor: pointer; transition: all 0.12s;
  white-space: nowrap;
}
.rq-chip:hover { border-color: var(--accent); color: var(--accent-deep); }
.rq-on {
  background: var(--accent-wash); border-color: var(--accent-deep);
  color: var(--accent-deep); font-weight: 700;
}
.rq-pinbtn { color: var(--accent-deep); border-color: var(--accent); }
.fest-arrow {
  width: 28px; height: 28px; border-radius: 50%;
  border: 1px solid var(--line); background: var(--card);
  color: var(--ink-soft); font-size: 16px; line-height: 1; cursor: pointer;
  display: grid; place-items: center; transition: all 0.12s;
}
.fest-arrow:hover { border-color: var(--accent); color: var(--accent-deep); }
.fest-card { transition: transform 0.12s, box-shadow 0.12s; }
.fest-card:hover { transform: translateY(-2px); box-shadow: 0 4px 14px rgba(0,0,0,.12); }
</style>
