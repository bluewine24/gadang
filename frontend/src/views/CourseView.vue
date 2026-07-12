<script setup>
import { ref, computed, nextTick, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import AppIcon from '../components/ui/AppIcon.vue'
import { geocodeCoord, getCourse, regenerateCourse, saveTrip, searchRegions } from '../api/gadang.js'
import CourseTimeline from '../components/trip/CourseTimeline.vue'
import { useAuth } from '../composables/useAuth.js'
import { won } from '../data/mock.js'

const router = useRouter()
const route  = useRoute()
const { isLoggedIn } = useAuth()

// 로그인 후 복귀 — sessionStorage에 저장한 코스를 복원
onMounted(() => {
  const saved = sessionStorage.getItem('pendingCourse')
  if (saved && isLoggedIn.value) {
    try {
      result.value = JSON.parse(saved)
    } catch { /* 무시 */ }
    sessionStorage.removeItem('pendingCourse')
  }
})

const from   = ref('현재 위치 (GPS)')
const destination = ref('')
const dep    = ref('08:00')
const arr    = ref('21:00')
const DEFAULT_CAFE_KIND = '루프탑'
const lunch  = ref({ kind: '한식', grade: '일반' })
const dinner = ref({ kind: '상관없음', grade: '일반' })
const budget = ref(50000)
const anchors = ref([])
const gpsCoord = ref(null)
const gpsName = ref('')
const gpsLocating = ref(false)
const selectedTransport = ref(null)

const GEO_OPTS = { enableHighAccuracy: false, timeout: 8000, maximumAge: 600000 }

function fetchPosition() {
  return new Promise(resolve => {
    if (!navigator.geolocation) { resolve(null); return }
    navigator.geolocation.getCurrentPosition(
      pos => resolve(pos),
      () => resolve(null),
      GEO_OPTS,
    )
  })
}

async function useGps() {
  if (gpsLocating.value) return
  if (gpsCoord.value && gpsName.value) {
    from.value = gpsName.value
    return
  }

  gpsLocating.value = true
  from.value = '위치 확인 중...'
  const pos = await fetchPosition()
  if (pos) {
    gpsCoord.value = { lat: pos.coords.latitude, lng: pos.coords.longitude }
    const geo = await geocodeCoord(pos.coords.latitude, pos.coords.longitude)
    gpsName.value = geo?.name || '현재 위치'
    from.value = gpsName.value
  } else {
    gpsCoord.value = null
    gpsName.value = ''
    from.value = '현재 위치 (GPS)'
    alert('현재 위치를 가져오지 못했습니다. 브라우저 위치 권한을 확인해 주세요.')
  }
  gpsLocating.value = false
}

// AI 챗봇 '코스 자세히 보기'로 넘어온 조건 프리필 (생성은 사용자가 직접)
if (route.query.region) destination.value = route.query.region
if (route.query.from)   from.value = route.query.from
if (route.query.dep)    dep.value  = route.query.dep
if (route.query.arr)    arr.value  = route.query.arr
if (route.query.budget) budget.value = Number(route.query.budget) || budget.value
if (route.query.startLat && route.query.startLng) {
  gpsCoord.value = { lat: Number(route.query.startLat), lng: Number(route.query.startLng) }
}
if (route.query.transportOneWayMin) {
  selectedTransport.value = {
    mode: route.query.transportMode || '',
    fromHub: route.query.transportFromHub || '',
    toHub: route.query.transportToHub || route.query.hub || '',
    oneWayMin: Number(route.query.transportOneWayMin) || 0,
    fare: Number(route.query.transportFare) || 0,
    originToHubMin: Number(route.query.transportOriginToHubMin) || 0,
    originToHubFare: Number(route.query.transportOriginToHubFare) || 0,
  }
}

/* ── 코스 추천 받기 → 결과 인라인 표시 → 확정 시 일정 탭 ───── */
const generating = ref(false)
const saving     = ref(false)
const result     = ref(null)   // null = 조건 입력, 객체 = 추천 결과 타임라인
const showTitleModal = ref(false)
const tripTitleInput = ref('')
const regenerating = ref(false)
const editDirty = ref(false)
const editEntries = ref([])
let editSeq = 0

const ACT_MAP  = { sight: 'TOURIST_SPOT', park: 'PARK', photo: 'PHOTO_SPOT', culture: 'CULTURAL', shop: 'SHOPPING' }
const CAFE_MAP = { 일반: 'GENERAL', 루프탑: 'ROOFTOP', 베이커리: 'BAKERY', '테마·이색': 'THEME' }
const FOOD_MAP = {
  한식: 'KOREAN',
  일식: 'JAPANESE',
  중식: 'CHINESE',
  양식: 'WESTERN',
  분식: 'BUNSIK',
  '고기/구이': 'MEAT_GRILL',
  해산물: 'SEAFOOD',
  패스트푸드: 'FASTFOOD',
  지역특색: 'LOCAL_SPECIALTY',
  상관없음: 'ANY',
}

const ACTIVITIES = [
  { k: 'sight', l: '관광명소', e: '◎' }, { k: 'park', l: '공원·산책', e: '⬡' },
  { k: 'photo', l: '포토스팟', e: '◇' }, { k: 'culture', l: '문화·전시', e: '▤' },
  { k: 'shop',  l: '쇼핑',    e: '▢' },
]
const CAFE_KINDS  = ['일반', '루프탑', '베이커리', '테마·이색']
const FOOD_KINDS  = ['상관없음', '한식', '일식', '중식', '양식', '분식', '고기/구이', '해산물', '패스트푸드', '지역특색']
const PERIODS = [
  { k: 'morning', l: '오전', hint: '가볍게 시작' },
  { k: 'afternoon', l: '오후', hint: '메인 동선' },
  { k: 'evening', l: '저녁', hint: '마무리 취향' },
]
const SPECIAL_PREFS = [
  { k: 'cafe', l: '카페', e: 'CO', kind: 'slot' },
  { k: 'lunch', l: '점심', e: 'LU', kind: 'meal' },
  { k: 'dinner', l: '저녁', e: 'DN', kind: 'meal' },
]
const PREF_DEFS = Object.fromEntries([
  ...ACTIVITIES.map((a) => [a.k, { ...a, kind: 'activity' }]),
  ...SPECIAL_PREFS.map((p) => [p.k, p]),
  ['fixed', { k: 'fixed', l: '방문 장소', e: 'PIN', kind: 'fixed' }],
])
const FIXED_CATEGORY_LABELS = {
  sight: '관광명소',
  tourist_spot: '관광명소',
  attraction: '관광명소',
  park: '공원·산책',
  photo: '포토스팟',
  culture: '문화·전시',
  cafe: '카페',
  food: '음식점',
  restaurant: '음식점',
  shop: '쇼핑',
  shopping: '쇼핑',
}
const ESTIMATE_MINUTES = {
  sight: 60,
  park: 60,
  photo: 30,
  culture: 90,
  shop: 60,
  cafe: 45,
  lunch: 60,
  dinner: 60,
  fixed: 60,
}
const LOCAL_MOVE_MINUTES = 15
const SAFETY_BUFFER_MINUTES = 30
const COURSE_CART_KEY = 'gadang_course_cart'

let prefSeq = 0
let anchorSeq = 0
const makePref = (type, extra = {}) => ({
  id: `pref-${++prefSeq}`,
  type,
  ...(type === 'cafe' ? { cafeKind: DEFAULT_CAFE_KIND } : {}),
  ...extra,
})
const preferenceGroups = ref([
  { key: 'morning', label: '오전', items: [] },
  { key: 'afternoon', label: '오후', items: [makePref('lunch')] },
  { key: 'evening', label: '저녁', items: [makePref('dinner')] },
])
const draggingPref = ref(null)
const dragOverPref = ref(null)

function importMapCartToAfternoon() {
  if (route.query.fromMapCart !== '1') return
  let cart = []
  try {
    cart = JSON.parse(localStorage.getItem(COURSE_CART_KEY) || '[]')
  } catch {
    cart = []
  }
  if (!Array.isArray(cart) || cart.length === 0) return

  cart.slice(0, 3).forEach((place) => {
    if (place?.lat == null || place?.lng == null) return
    const anchorId = `anchor-${++anchorSeq}`
    anchors.value.push({
      id: anchorId,
      name: place.name,
      place: {
        id: place.id,
        name: place.name,
        address: place.addr || '',
        lat: Number(place.lat),
        lng: Number(place.lng),
        category: place.cat || '',
      },
    })
  })
}

importMapCartToAfternoon()

const preferenceEntries = computed(() => preferenceGroups.value.flatMap((group) =>
  group.items.map((item, index) => ({ group, item, index })),
))
const placedAnchorIds = computed(() => new Set(preferenceEntries.value
  .filter(({ item }) => item.type === 'fixed' && item.anchorId)
  .map(({ item }) => item.anchorId)))
const unplacedAnchors = computed(() => anchors.value.filter((anchor) => !placedAnchorIds.value.has(anchor.id)))
const autoOrderedUnplacedAnchors = computed(() => orderAnchorsByTravel(unplacedAnchors.value))
const autoAnchorBuckets = computed(() => distributeAutoAnchorsByPeriod())
const activityPreferenceKeys = computed(() => {
  return preferenceEntries.value
    .map(({ item }) => item.type)
    .filter((type) => PREF_DEFS[type]?.kind === 'activity')
})
const activePreferenceTypes = computed(() => new Set(preferenceEntries.value.map(({ item }) => item.type)))

function prefDef(type) {
  return PREF_DEFS[type] || { k: type, l: type, e: '·', kind: 'activity' }
}

function hasPreferenceType(type) {
  return activePreferenceTypes.value.has(type)
}

function preferenceLabel(item) {
  if (item.type !== 'fixed') return prefDef(item.type).l
  return anchors.value.find((a) => a.id === item.anchorId)?.name || '장소 선택'
}

function fixedCategoryLabel(anchor) {
  const raw = anchor?.place?.category || anchor?.place?.cat || ''
  const normalized = String(raw).trim()
  if (!normalized) return '방문 장소'
  const mapped = FIXED_CATEGORY_LABELS[normalized.toLowerCase()]
  if (mapped) return mapped
  if (normalized.includes('카페')) return '카페'
  if (normalized.includes('음식') || normalized.includes('식당')) return '음식점'
  if (normalized.includes('쇼핑') || normalized.includes('상점')) return '쇼핑'
  if (normalized.includes('문화') || normalized.includes('전시')) return '문화·전시'
  if (normalized.includes('공원')) return '공원·산책'
  if (normalized.includes('관광') || normalized.includes('명소')) return '관광명소'
  return normalized
}

function fixedCategoryKey(anchor) {
  const raw = String(anchor?.place?.cat || anchor?.place?.category || '').trim().toLowerCase()
  if (['sight', 'park', 'photo', 'culture', 'cafe', 'food', 'shop'].includes(raw)) return raw
  if (raw.includes('카페')) return 'cafe'
  if (raw.includes('음식') || raw.includes('식당')) return 'food'
  if (raw.includes('쇼핑') || raw.includes('상점')) return 'shop'
  if (raw.includes('문화') || raw.includes('전시')) return 'culture'
  if (raw.includes('공원')) return 'park'
  return 'sight'
}

function isAnchorPlaced(anchorId) {
  return placedAnchorIds.value.has(anchorId)
}

function anchorToPreferenceEntry(anchor) {
  if (!anchor?.place || anchor.place.lat == null || anchor.place.lng == null) return null
  return {
    clientId: `auto-${anchor.id}`,
    type: 'SPECIFIC_PLACE',
    pid: anchor.place.id || null,
    placeName: anchor.name || anchor.place.name,
    lat: Number(anchor.place.lat),
    lng: Number(anchor.place.lng),
    cat: fixedCategoryKey(anchor),
    role: 'ANCHOR',
    stayMinutes: 60,
    fee: 0,
    feeType: 'free',
  }
}

function preferenceEntriesForPayload() {
  const hasManualVisitOrder = preferenceEntries.value.some(({ item }) => item.type === 'fixed')
  const hasAutoVisitOrder = Object.values(autoAnchorBuckets.value).some((bucket) => bucket.length > 0)
  if (!hasManualVisitOrder && !hasAutoVisitOrder) return null

  const manualEntries = preferenceGroups.value.flatMap((group) => group.items.map((item) => {
    if (item.type === 'fixed') {
      const anchor = anchors.value.find((a) => a.id === item.anchorId)
      const entry = anchorToPreferenceEntry(anchor)
      return entry ? { ...entry, clientId: item.id } : null
    }
    if (item.type === 'cafe') {
      return {
        clientId: item.id,
        type: 'CAFE_SLOT',
        cafeType: CAFE_MAP[cafeKind(item)] || 'GENERAL',
      }
    }
    if (item.type === 'lunch' || item.type === 'dinner') {
      const state = mealState(item.type)
      return {
        clientId: item.id,
        type: item.type === 'lunch' ? 'LUNCH' : 'DINNER',
        foodType: FOOD_MAP[state.kind] || 'ANY',
        priceLevel: 'MID',
      }
    }
    return {
      clientId: item.id,
      type: 'ACTIVITY_SLOT',
      activityType: ACT_MAP[item.type] || 'TOURIST_SPOT',
    }
  }).filter(Boolean).concat(
    (autoAnchorBuckets.value[group.key] || [])
      .map(anchorToPreferenceEntry)
      .filter(Boolean),
  ))
  return manualEntries
}

function preferenceMeta(item) {
  if (item.type === 'cafe') return item.cafeKind || DEFAULT_CAFE_KIND
  if (item.type === 'lunch') return lunch.value.kind
  if (item.type === 'dinner') return dinner.value.kind
  if (item.type === 'fixed') {
    const anchor = anchors.value.find((a) => a.id === item.anchorId)
    const category = fixedCategoryLabel(anchor)
    const address = anchor?.place?.address || '실제 장소 고정'
    return `${category} · ${address}`
  }
  return '활동 유형'
}

function mealState(type) {
  return type === 'lunch' ? lunch.value : dinner.value
}

function setMealKind(type, value) {
  mealState(type).kind = value
}

function isAddDisabled(type) {
  return PREF_DEFS[type]?.kind === 'meal' && hasPreferenceType(type)
}

function groupByKey(periodKey) {
  return preferenceGroups.value.find((group) => group.key === periodKey)
}

function removePreference(periodKey, index) {
  const group = groupByKey(periodKey)
  if (!group) return
  group.items.splice(index, 1)
}

function removeAnchor(anchorId) {
  anchors.value = anchors.value.filter((anchor) => anchor.id !== anchorId)
  preferenceGroups.value.forEach((group) => {
    group.items = group.items.filter((item) => item.type !== 'fixed' || item.anchorId !== anchorId)
  })
}

function movePreference(fromPeriod, fromIndex, toPeriod, toIndex) {
  const fromGroup = groupByKey(fromPeriod)
  const toGroup = groupByKey(toPeriod)
  if (!fromGroup || !toGroup) return
  const [item] = fromGroup.items.splice(fromIndex, 1)
  if (!item) return
  let insertAt = toIndex
  if (fromGroup === toGroup && fromIndex < toIndex) insertAt -= 1
  toGroup.items.splice(Math.max(0, insertAt), 0, item)
}

function insertPreference(type, periodKey, index) {
  if (isAddDisabled(type)) return
  const group = groupByKey(periodKey)
  if (!group) return
  group.items.splice(Math.max(0, index), 0, makePref(type))
}

function insertFixedPreference(anchorId, periodKey, index) {
  if (!anchorId || isAnchorPlaced(anchorId)) return
  const group = groupByKey(periodKey)
  if (!group) return
  group.items.splice(Math.max(0, index), 0, makePref('fixed', { anchorId }))
}

function onPaletteDragStart(event, type) {
  if (isAddDisabled(type)) return
  draggingPref.value = { source: 'palette', type }
  dragOverPref.value = null
  event.dataTransfer.effectAllowed = 'copy'
  event.dataTransfer.setData('text/plain', `palette:${type}`)
}

function onAnchorDragStart(event, anchorId) {
  if (isAnchorPlaced(anchorId)) return
  draggingPref.value = { source: 'anchor', anchorId }
  dragOverPref.value = null
  event.dataTransfer.effectAllowed = 'copy'
  event.dataTransfer.setData('text/plain', `anchor:${anchorId}`)
}

function onPrefDragStart(event, periodKey, index) {
  draggingPref.value = { source: 'lane', periodKey, index }
  dragOverPref.value = null
  event.dataTransfer.effectAllowed = 'move'
  event.dataTransfer.setData('text/plain', `${periodKey}:${index}`)
}

function isNoopDrop(periodKey, index) {
  const from = draggingPref.value
  return from?.source === 'lane'
    && from.periodKey === periodKey
    && (index === from.index || index === from.index + 1)
}

function setDropIndicator(event, periodKey, index) {
  if (!draggingPref.value || estimateForPeriod(periodKey)?.inactive) return
  const rect = event.currentTarget.getBoundingClientRect()
  const after = event.clientY > rect.top + rect.height / 2
  const insertIndex = index + (after ? 1 : 0)
  dragOverPref.value = isNoopDrop(periodKey, insertIndex)
    ? null
    : { periodKey, index: insertIndex, end: false }
}

function dropIndexFor(periodKey, fallbackIndex) {
  return dragOverPref.value?.periodKey === periodKey
    ? dragOverPref.value.index
    : fallbackIndex
}

function dropClass(periodKey, index) {
  const target = dragOverPref.value
  if (target?.periodKey !== periodKey) return null
  if (target.index === index) return 'drop-before'
  if (target.index === index + 1) return 'drop-after'
  return null
}

function onPrefDrop(event, periodKey, index) {
  event.preventDefault()
  if (estimateForPeriod(periodKey)?.inactive) {
    draggingPref.value = null
    dragOverPref.value = null
    return
  }
  const from = draggingPref.value
  if (!from) return
  const insertIndex = dropIndexFor(periodKey, index)
  if (from.source === 'palette') {
    insertPreference(from.type, periodKey, insertIndex)
  } else if (from.source === 'anchor') {
    insertFixedPreference(from.anchorId, periodKey, insertIndex)
  } else {
    movePreference(from.periodKey, from.index, periodKey, insertIndex)
  }
  draggingPref.value = null
  dragOverPref.value = null
}

function onPrefDropEnd(event, periodKey) {
  event.preventDefault()
  const group = groupByKey(periodKey)
  onPrefDrop(event, periodKey, group?.items.length ?? 0)
}

function onPrefDragEnd() {
  draggingPref.value = null
  dragOverPref.value = null
}

const cafePreferenceEntries = computed(() => preferenceEntries.value
  .filter(({ item }) => item.type === 'cafe'))

function cafeKind(item) {
  return item.cafeKind || DEFAULT_CAFE_KIND
}

function setCafeKind(item, value) {
  item.cafeKind = value
}

function cafeWindowForPeriod(periodKey) {
  if (periodKey === 'morning') return { startTime: '10:00', endTime: '12:30' }
  if (periodKey === 'evening') return { startTime: '17:00', endTime: '20:00' }
  return { startTime: '14:00', endTime: '17:00' }
}

function cafeSlotsForPayload() {
  return cafePreferenceEntries.value.map(({ group, item }) => ({
    cafeType: CAFE_MAP[cafeKind(item)] || 'GENERAL',
    ...cafeWindowForPeriod(group.key),
  }))
}

function buildPayload() {
  const meal = (m, type) => hasPreferenceType(type)
    ? {
        enabled: true,
        foodType: FOOD_MAP[m.kind] || 'ANY',
        priceLevel: 'MID',
      }
    : { enabled: false }
  const cafeSlots = cafeSlotsForPayload()
  const firstCafe = cafeSlots[0] || null
  return {
    region: destination.value,
    startAddress: gpsName.value || from.value,
    startLat: gpsCoord.value?.lat ?? null,
    startLng: gpsCoord.value?.lng ?? null,
    departureTime: dep.value,
    returnTime: arr.value,
    transportMode: selectedTransport.value?.mode || null,
    transportFromHub: selectedTransport.value?.fromHub || null,
    transportToHub: selectedTransport.value?.toHub || null,
    transportOneWayMin: selectedTransport.value?.oneWayMin || null,
    transportFare: selectedTransport.value?.fare ?? null,
    transportOriginToHubMin: selectedTransport.value?.originToHubMin ?? null,
    transportOriginToHubFare: selectedTransport.value?.originToHubFare ?? null,
    budgetGuide: Number(budget.value) || null,
    activityTypes: activityPreferenceKeys.value.map((k) => ACT_MAP[k]).filter(Boolean),
    cafeEnabled: cafeSlots.length > 0,
    cafeType: firstCafe?.cafeType || null,
    cafeStartTime: firstCafe?.startTime || null,
    cafeEndTime: firstCafe?.endTime || null,
    cafeSlots,
    lunch: meal(lunch.value, 'lunch'),
    dinner: meal(dinner.value, 'dinner'),
    trendEnabled: true,
    fixedPlaces: fixedPlacesForPayload(),
    preferenceEntries: preferenceEntriesForPayload(),
  }
}

function initEditEntries(course) {
  editEntries.value = (course?.items || [])
    .filter((item) => item.type === 'place')
    .map((item) => ({
      clientId: `edit-${++editSeq}`,
      type: 'LOCKED_PLACE',
      pid: item.pid || null,
      placeName: item.name,
      lat: item.lat,
      lng: item.lng,
      cat: item.cat,
      role: item.role,
      meal: item.meal,
      stayMinutes: item.stay || 60,
      fee: item.fee || 0,
      feeType: item.feeType || 'estimate',
    }))
  editDirty.value = false
}

function editSlotPayload(entry) {
  if (entry.type === 'LOCKED_PLACE' || entry.type === 'SPECIFIC_PLACE') {
    return {
      clientId: entry.clientId,
      type: entry.type,
      pid: entry.pid || null,
      placeName: entry.placeName,
      lat: entry.lat,
      lng: entry.lng,
      cat: entry.cat,
      role: entry.role,
      meal: entry.meal || null,
      stayMinutes: entry.stayMinutes || 60,
      fee: entry.fee || 0,
      feeType: entry.feeType || 'estimate',
    }
  }
  if (entry.type === 'ACTIVITY_SLOT') {
    return {
      clientId: entry.clientId,
      type: entry.type,
      pid: entry.pid || null,
      placeName: entry.placeName || null,
      lat: entry.lat ?? null,
      lng: entry.lng ?? null,
      activityType: ACT_MAP[entry.activityKey] || 'TOURIST_SPOT',
    }
  }
  if (entry.type === 'CAFE_SLOT') {
    return {
      clientId: entry.clientId,
      type: entry.type,
      pid: entry.pid || null,
      placeName: entry.placeName || null,
      lat: entry.lat ?? null,
      lng: entry.lng ?? null,
      cafeType: CAFE_MAP[entry.cafeKind || DEFAULT_CAFE_KIND] || 'GENERAL',
    }
  }
  if (entry.type === 'LUNCH' || entry.type === 'DINNER') {
    const state = entry.type === 'LUNCH' ? lunch.value : dinner.value
    return {
      clientId: entry.clientId,
      type: entry.type,
      pid: entry.pid || null,
      placeName: entry.placeName || null,
      lat: entry.lat ?? null,
      lng: entry.lng ?? null,
      foodType: FOOD_MAP[entry.foodKind || state.kind] || 'ANY',
      priceLevel: 'MID',
    }
  }
  return entry
}

function buildRegeneratePayload() {
  return {
    base: buildPayload(),
    entries: editEntries.value.map(editSlotPayload),
  }
}

function removeEditEntry(payload) {
  const index = typeof payload === 'number' ? payload : payload?.index
  if (!Number.isInteger(index) || index < 0) return
  editEntries.value.splice(index, 1)
  editDirty.value = true
}

function insertEditEntry(index, entry) {
  editEntries.value.splice(index, 0, { clientId: `edit-${++editSeq}`, ...entry })
  editDirty.value = true
}

function updateEditEntry({ index, patch }) {
  if (!Number.isInteger(index) || index < 0 || index >= editEntries.value.length) return
  editEntries.value[index] = { ...editEntries.value[index], ...patch }
  editDirty.value = true
}

function addEditActivity(index, activityKey) {
  if (typeof index === 'object') {
    activityKey = index.activityKey
    index = index.index
  }
  insertEditEntry(index, { type: 'ACTIVITY_SLOT', activityKey })
}

function addEditCafe(index) {
  if (typeof index === 'object') index = index.index
  insertEditEntry(index, { type: 'CAFE_SLOT', cafeKind: DEFAULT_CAFE_KIND })
}

function addEditMeal(index, type) {
  if (typeof index === 'object') {
    type = index.type
    index = index.index
  }
  const state = type === 'LUNCH' ? lunch.value : dinner.value
  insertEditEntry(index, {
    type,
    foodKind: state.kind,
  })
}

async function runRegenerate() {
  if (regenerating.value) return
  regenerating.value = true
  try {
    result.value = await regenerateCourse(buildRegeneratePayload())
    initEditEntries(result.value)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  } catch (e) {
    alert(e?.message || '코스 재생성에 실패했습니다.')
  } finally {
    regenerating.value = false
  }
}

async function runCourse() {
  if (generating.value) return
  const blockedEstimate = overfullEstimate.value
  if (blockedEstimate) {
    alert(estimateMessage.value)
    return
  }
  if (!destination.value.trim()) {
    alert('목적지를 입력하거나 지도에서 선택해 주세요.')
    return
  }
  generating.value = true
  if (from.value.includes('GPS')) {
    await useGps()
    if (!gpsCoord.value) {
      generating.value = false
      return
    }
  }
  try {
    result.value = await getCourse(buildPayload())
    initEditEntries(result.value)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  } catch (e) {
    console.error(e)
    alert(e?.message || '코스 생성에 실패했습니다. 조건을 확인하고 다시 시도해 주세요.')
  } finally {
    generating.value = false
  }
}

function backToForm() {
  result.value = null
}

// 추천 결과 확정 → 제목 입력 모달 → 백엔드 저장 → 일정 탭
function confirmTrip() {
  if (!result.value || saving.value) return
  if (!isLoggedIn.value) {
    sessionStorage.setItem('pendingCourse', JSON.stringify(result.value))
    router.push('/login?redirect=/course')
    return
  }
  tripTitleInput.value = `${result.value.region || from.value} 당일치기`
  showTitleModal.value = true
}

async function doSaveTrip() {
  if (saving.value) return
  const title = tripTitleInput.value.trim() || `${result.value.region || from.value} 당일치기`
  saving.value = true
  showTitleModal.value = false
  try {
    await saveTrip(title, result.value)
    router.push('/trip')
  } catch (e) {
    alert('일정 저장에 실패했어요. 잠시 후 다시 시도해 주세요.')
  } finally {
    saving.value = false
  }
}

/* ─── 지도 피커 (고정 장소 선택) ────────────────────────── */
const pickerOpen   = ref(false)
const pickerMode   = ref('anchor')
const pickerIdx    = ref(-1)     // 어떤 anchor 슬롯인지 (-1 = 신규)
const pickerPeriod = ref('afternoon')
const pickerInsertIndex = ref(-1)
const pickerQ      = ref('')
const pickerResults = ref([])
const pickerSel    = ref(null)   // { name, address, lat, lng }
const pickerMapEl  = ref(null)
const pickerSearching = ref(false)
const regions = ref([])
const regionsLoading = ref(false)
const regionsSearched = ref(false)
const regionsError = ref('')
const regionSort = ref('rec')
const regionSelectedOpts = ref({})

const SORTS = [['rec','추천순'],['trend','트렌드순'],['time','이동 짧은순'],['fare','저렴한순']]

let _pickerMap = null
let _pickerPS  = null
let _pickerMarker = null

const filteredRegions = computed(() => {
  const q = pickerQ.value.trim()
  const list = Array.isArray(sortedRegions.value) ? sortedRegions.value : []
  return q ? list.filter((r) => r.name?.includes(q) || r.sido?.includes(q)) : list
})

const regionRecScores = computed(() => {
  const dests = regions.value.filter((r) => !r.self)
  const trends = dests.map((r) => r.trend || 0)
  const tMin = Math.min(...trends), tMax = Math.max(...trends)
  const span = tMax - tMin
  const map = {}
  for (const r of dests) {
    const normTrend = span > 0 ? 0.05 + 0.95 * ((r.trend || 0) - tMin) / span : 1
    const total = (r.stay || 0) + (r.roundTrip || 0)
    const stayRatio = total > 0 ? r.stay / total : 0
    map[rKey(r)] = Math.pow(normTrend, 1.0) * Math.pow(stayRatio, 2.5) * 100
  }
  return map
})

const rKey = (r) => r.id || r.name
const getSelIdx = (r) => regionSelectedOpts.value[rKey(r)] ?? 0
const getSelOpt = (r) => r.options?.[getSelIdx(r)] ?? null
const recScore = (r) => regionRecScores.value[rKey(r)] || 0
const effRoundTrip = (r) => { const o = getSelOpt(r); return o ? o.oneWayMin * 2 : r.roundTrip }
const effFare = (r) => { const o = getSelOpt(r); return o ? o.fare * 2 : r.fare }

const sortedRegions = computed(() => {
  const list = [...regions.value]
  const selfFirst = (a, b) => (a.self === b.self ? 0 : a.self ? -1 : 1)
  if (regionSort.value === 'rec') return list.sort((a, b) => selfFirst(a, b) || recScore(b) - recScore(a))
  if (regionSort.value === 'trend') return list.sort((a, b) => selfFirst(a, b) || (b.trend || 0) - (a.trend || 0))
  if (regionSort.value === 'time') return list.sort((a, b) => selfFirst(a, b) || effRoundTrip(a) - effRoundTrip(b))
  return list.sort((a, b) => selfFirst(a, b) || effFare(a) - effFare(b))
})

function fmtMin(m) {
  if (!m) return '0분'
  const h = Math.floor(m / 60), x = m % 60
  return (h ? h + '시간' : '') + (x ? ' ' + x + '분' : '')
}

function gapMin(a, b) {
  const [ah, am] = String(a || '00:00').split(':').map(Number)
  const [bh, bm] = String(b || '00:00').split(':').map(Number)
  let m = (bh * 60 + bm) - (ah * 60 + am)
  if (m < 0) m += 1440
  return m
}

const effStay = (r) => r.self ? r.stay : Math.max(0, gapMin(dep.value, arr.value) - effRoundTrip(r))

function selectRegionOpt(region, idx) {
  regionSelectedOpts.value = { ...regionSelectedOpts.value, [rKey(region)]: idx }
  pickerSel.value = { name: region.name, region, option: region.options?.[idx] ?? null }
}

async function loadRegions() {
  if (regionsLoading.value) return
  regionsLoading.value = true
  regionsError.value = ''
  try {
    if (from.value.includes('GPS')) await useGps()
    const params = { from: from.value, dep: dep.value, arr: arr.value }
    if (gpsCoord.value) {
      params.lat = gpsCoord.value.lat
      params.lng = gpsCoord.value.lng
    }
    regions.value = await searchRegions(params)
    regionsSearched.value = true
    const next = {}
    regions.value.forEach((r) => { next[rKey(r)] = regionSelectedOpts.value[rKey(r)] ?? 0 })
    regionSelectedOpts.value = next
  } catch (e) {
    regionsError.value = '당일치기 가능 지역을 불러오지 못했습니다.'
  } finally {
    regionsLoading.value = false
  }
}

/* 피커 열기 */
function openPicker(idx = -1, periodKey = 'afternoon') {
  pickerMode.value = 'anchor'
  pickerIdx.value  = idx
  pickerPeriod.value = periodKey
  pickerInsertIndex.value = -1
  pickerQ.value    = idx >= 0 ? (anchors.value[idx]?.name || '') : ''
  pickerResults.value = []
  pickerSel.value  = idx >= 0 ? anchors.value[idx]?.place || null : null
  pickerOpen.value = true
  nextTick(initPickerMap)
}

function openEditSpecificPicker(index) {
  if (typeof index === 'object') index = index.index
  pickerMode.value = 'editSpecific'
  pickerIdx.value = -1
  pickerPeriod.value = 'afternoon'
  pickerInsertIndex.value = index
  pickerQ.value = ''
  pickerResults.value = []
  pickerSel.value = null
  pickerOpen.value = true
  nextTick(initPickerMap)
}

async function openDestinationPicker() {
  pickerMode.value = 'destination'
  pickerIdx.value = -1
  pickerInsertIndex.value = -1
  pickerQ.value = destination.value
  pickerResults.value = []
  pickerSel.value = null
  pickerOpen.value = true
  await loadRegions()
  const current = regions.value.find((r) => r.name === destination.value)
  if (current) {
    const idx = Math.max(0, current.options?.findIndex((o) =>
      o.type === selectedTransport.value?.mode
      && o.fromHub === selectedTransport.value?.fromHub
      && o.toHub === selectedTransport.value?.toHub) ?? 0)
    selectRegionOpt(current, idx)
  }
}

/* 피커 닫기 */
function closePicker() {
  pickerOpen.value = false
  pickerQ.value    = ''
  pickerResults.value = []
  pickerInsertIndex.value = -1
}

/* Kakao SDK 로드 (MapView와 같은 방식) */
function loadKakaoSDK() {
  return new Promise((resolve, reject) => {
    const ready = () => window.kakao?.maps?.Map && window.kakao?.maps?.services?.Places
    if (ready()) { resolve(); return }

    const waitUntilReady = () => {
      const id = setInterval(() => {
        if (ready()) {
          clearInterval(id)
          resolve()
        }
      }, 80)
      setTimeout(() => {
        clearInterval(id)
        reject(new Error('Kakao SDK load timed out'))
      }, 10000)
    }

    const existing = document.querySelector('script[src*="dapi.kakao.com"]')
    if (existing) {
      waitUntilReady()
      return
    }

    const s = document.createElement('script')
    s.src = `//dapi.kakao.com/v2/maps/sdk.js?appkey=${import.meta.env.VITE_KAKAO_MAP_KEY}&libraries=services&autoload=false`
    s.onload = () => window.kakao.maps.load(() => {
      if (ready()) resolve()
      else reject(new Error('Kakao Places service is unavailable'))
    })
    s.onerror = () => reject(new Error('Kakao SDK load failed'))
    document.head.appendChild(s)
  })
}
/* 피커 지도 초기화 */
async function initPickerMap() {
  try {
    await loadKakaoSDK()
  } catch (e) {
    console.error(e)
    alert('지도를 불러오지 못했습니다. Kakao JavaScript 키와 도메인 설정을 확인해 주세요.')
    closePicker()
    return
  }
  if (!pickerMapEl.value) return

  const { kakao } = window
  const center = pickerSel.value
    ? new kakao.maps.LatLng(pickerSel.value.lat, pickerSel.value.lng)
    : new kakao.maps.LatLng(37.5665, 126.9780)

  _pickerMap = new kakao.maps.Map(pickerMapEl.value, { center, level: 3 })
  _pickerPS  = new kakao.maps.services.Places()

  if (pickerSel.value) {
    _pickerMarker = new kakao.maps.Marker({ position: center, map: _pickerMap })
  }
}

/* 장소 검색 */
async function searchPlace() {
  if (pickerMode.value === 'destination') return
  if (!pickerQ.value.trim()) return
  if (!_pickerPS) {
    await initPickerMap()
    if (!_pickerPS) return
  }
  pickerSearching.value = true
  _pickerPS.keywordSearch(pickerQ.value, (data, status) => {
    pickerSearching.value = false
    if (status === window.kakao.maps.services.Status.OK) {
      pickerResults.value = data.slice(0, 8).map(p => ({
        id:      p.id,
        name:    p.place_name,
        address: p.road_address_name || p.address_name,
        lat:     parseFloat(p.y),
        lng:     parseFloat(p.x),
        category: p.category_name?.split('>').pop()?.trim() || '',
      }))
    } else {
      pickerResults.value = []
    }
  })
}

/* 결과 클릭 → 지도 이동 + 마커 */
function selectResult(place) {
  pickerSel.value = place
  if (pickerMode.value === 'destination') return
  if (_pickerMap) {
    const { kakao } = window
    const latlng = new kakao.maps.LatLng(place.lat, place.lng)
    _pickerMap.setCenter(latlng)
    _pickerMap.setLevel(3)
    if (_pickerMarker) _pickerMarker.setMap(null)
    _pickerMarker = new kakao.maps.Marker({ position: latlng, map: _pickerMap })
  }
}

/* 선택 확정 */
function timeToMinutes(time, fallback = '10:00') {
  const [h, m] = String(time || fallback).split(':').map(Number)
  return (Number.isFinite(h) ? h : 10) * 60 + (Number.isFinite(m) ? m : 0)
}

function minutesToTime(minutes) {
  const clamped = Math.max(0, Math.min(23 * 60 + 59, minutes))
  const h = Math.floor(clamped / 60)
  const m = clamped % 60
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`
}

function defaultAnchorTimes(periodKey = 'afternoon', order = 0) {
  const start = timeToMinutes(dep.value)
  const end = timeToMinutes(arr.value, '21:00')
  const originToHub = Number(selectedTransport.value?.originToHubMin || 0)
  const oneWay = Number(selectedTransport.value?.oneWayMin || 0)
  const outbound = Math.max(0, originToHub) + Math.max(0, oneWay)
  const baseByPeriod = { morning: '10:30', afternoon: '14:00', evening: '17:30' }

  let visit = Math.max(timeToMinutes(baseByPeriod[periodKey] || '14:00') + order * 75, start + outbound + 60)
  visit = Math.ceil(visit / 30) * 30
  let depart = visit + 60

  if (depart >= end) {
    depart = Math.max(start + 60, end - 30)
    visit = Math.max(start + 15, depart - 60)
  }

  return {
    arrTime: minutesToTime(visit),
    depTime: minutesToTime(depart),
  }
}

function distanceKm(a, b) {
  if (!a || !b || a.lat == null || a.lng == null || b.lat == null || b.lng == null) return Number.POSITIVE_INFINITY
  const toRad = (v) => Number(v) * Math.PI / 180
  const dLat = toRad(b.lat - a.lat)
  const dLng = toRad(b.lng - a.lng)
  const lat1 = toRad(a.lat)
  const lat2 = toRad(b.lat)
  const h = Math.sin(dLat / 2) ** 2
    + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) ** 2
  return 6371 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h))
}

function anchorCoord(anchor) {
  return anchor?.place?.lat == null || anchor?.place?.lng == null
    ? null
    : { lat: Number(anchor.place.lat), lng: Number(anchor.place.lng) }
}

function orderAnchorsByTravel(list) {
  const remaining = [...list]
  if (remaining.length <= 1) return remaining

  const ordered = []
  let current = gpsCoord.value || anchorCoord(remaining[0])
  while (remaining.length) {
    let bestIndex = 0
    let bestDistance = Number.POSITIVE_INFINITY
    remaining.forEach((anchor, index) => {
      const dist = distanceKm(current, anchorCoord(anchor))
      if (dist < bestDistance) {
        bestDistance = dist
        bestIndex = index
      }
    })
    const [next] = remaining.splice(bestIndex, 1)
    ordered.push(next)
    current = anchorCoord(next) || current
  }
  return ordered
}

function distributeAutoAnchorsByPeriod() {
  const buckets = Object.fromEntries(PERIODS.map((period) => [period.k, []]))
  const remaining = Object.fromEntries(PERIODS.map((period) => {
    const estimate = estimateForPeriod(period.k)
    const room = estimate && !estimate.inactive ? estimate.usable - estimate.estimated : Number.NEGATIVE_INFINITY
    return [period.k, room]
  }))

  autoOrderedUnplacedAnchors.value.forEach((anchor) => {
    const target = PERIODS
      .map((period) => period.k)
      .sort((a, b) => remaining[b] - remaining[a])[0]
    if (!target || !Number.isFinite(remaining[target])) return

    const moveCost = buckets[target].length > 0 ? LOCAL_MOVE_MINUTES : 0
    buckets[target].push(anchor)
    remaining[target] -= ESTIMATE_MINUTES.fixed + moveCost
  })

  Object.keys(buckets).forEach((key) => {
    buckets[key] = orderAnchorsByTravel(buckets[key])
  })
  return buckets
}

function orderedAnchorsForPayload() {
  const out = []
  const seen = new Set()
  autoOrderedUnplacedAnchors.value.forEach((anchor) => {
    if (!anchor || seen.has(anchor.id)) return
    seen.add(anchor.id)
    out.push(anchor)
  })
  preferenceGroups.value.forEach((group) => {
    group.items.forEach((item) => {
      if (item.type !== 'fixed' || seen.has(item.anchorId)) return
      const anchor = anchors.value.find((a) => a.id === item.anchorId)
      if (!anchor) return
      seen.add(anchor.id)
      out.push(anchor)
    })
  })
  anchors.value.forEach((anchor) => {
    if (seen.has(anchor.id)) return
    seen.add(anchor.id)
    out.push(anchor)
  })
  return out
}

function fixedPlacesForPayload() {
  return orderedAnchorsForPayload()
    .filter((anchor) => anchor?.place && anchor.place.lat != null && anchor.place.lng != null)
    .map((anchor) => ({
      placeName: anchor.name || anchor.place.name,
      lat: Number(anchor.place.lat),
      lng: Number(anchor.place.lng),
    }))
}

function selectedOneWayTravelMinutes() {
  return Math.max(0, Number(selectedTransport.value?.oneWayMin || 0))
}

function destinationAvailableWindow() {
  const start = timeToMinutes(dep.value)
  const end = timeToMinutes(arr.value, '21:00')
  if (!end || end <= start) return { start, end: start }
  const travel = selectedOneWayTravelMinutes()
  return {
    start: Math.min(end, start + travel),
    end: Math.max(start, end - travel),
  }
}

function periodUsableMinutes(periodKey) {
  const available = destinationAvailableWindow()
  if (available.end <= available.start) return 0
  const noon = 12 * 60
  const evening = 18 * 60
  if (periodKey === 'morning') return Math.max(0, Math.min(available.end, noon) - available.start)
  if (periodKey === 'afternoon') return Math.max(0, Math.min(available.end, evening) - Math.max(available.start, noon))
  return Math.max(0, available.end - Math.max(available.start, evening))
}

function overlapMinutes(startA, endA, startB, endB) {
  return Math.max(0, Math.min(endA, endB) - Math.max(startA, startB))
}

function windowMinutes(startTime, endTime) {
  return {
    start: timeToMinutes(startTime),
    end: timeToMinutes(endTime),
  }
}

function requiredItemWindow(item, periodKey) {
  if (item.type === 'lunch') return windowMinutes('11:00', '14:00')
  if (item.type === 'dinner') return windowMinutes('17:00', '20:00')
  if (item.type === 'cafe') {
    const window = cafeWindowForPeriod(periodKey)
    return windowMinutes(window.startTime, window.endTime)
  }
  return null
}

function itemUsableMinutes(item, periodKey) {
  const available = destinationAvailableWindow()
  const window = requiredItemWindow(item, periodKey)
  if (!window) return periodUsableMinutes(periodKey)
  return overlapMinutes(available.start, available.end, window.start, window.end)
}

function groupUsableMinutes(group) {
  return group.items.reduce(
    (usable, item) => Math.max(usable, itemUsableMinutes(item, group.key)),
    periodUsableMinutes(group.key),
  )
}

function fixedPreferenceMinutes(item) {
  const anchor = anchors.value.find((a) => a.id === item.anchorId)
  if (!anchor?.arrTime || !anchor?.depTime) return ESTIMATE_MINUTES.fixed
  return Math.max(20, gapMin(anchor.arrTime, anchor.depTime))
}

function preferenceEstimateMinutes(item) {
  if (item.type === 'fixed') return fixedPreferenceMinutes(item)
  return ESTIMATE_MINUTES[item.type] ?? 60
}

function estimateStatus(estimated, usable, inactive = false) {
  if (inactive && estimated <= 0) return 'OFF'
  if (estimated <= usable) return 'OK'
  if (estimated <= usable + 60) return 'TIGHT'
  return 'OVERFULL'
}

const periodEstimates = computed(() => {
  return preferenceGroups.value.map((group) => {
    const slotMinutes = group.items.reduce((sum, item) => sum + preferenceEstimateMinutes(item), 0)
    const moveMinutes = Math.max(0, group.items.length - 1) * LOCAL_MOVE_MINUTES
    const safetyMinutes = group.items.length > 0 ? SAFETY_BUFFER_MINUTES : 0
    const estimated = slotMinutes + moveMinutes + safetyMinutes
    const usable = groupUsableMinutes(group)
    const inactive = usable <= 0
    return {
      key: group.key,
      label: group.label,
      estimated,
      usable,
      inactive,
      status: estimateStatus(estimated, usable, inactive),
    }
  })
})

function estimateForPeriod(periodKey) {
  return periodEstimates.value.find((estimate) => estimate.key === periodKey)
}

const overfullEstimate = computed(() => periodEstimates.value.find((estimate) => estimate.status === 'OVERFULL') || null)
const estimateMessage = computed(() => {
  const over = overfullEstimate.value
  if (!over) return ''
  if (over.inactive) {
    return `${over.label}은 선택한 출발/귀가 시간과 이동 시간을 고려하면 사용할 수 없는 시간대예요. 이 탭의 선호 카드를 다른 시간대로 옮겨 주세요.`
  }
  return `${over.label} 선호 순서가 너무 많아요. 예상 ${fmtMin(over.estimated)}, 사용 가능 ${fmtMin(over.usable)}입니다.`
})

function confirmPicker() {
  if (!pickerSel.value) return
  const place = pickerSel.value
  if (pickerMode.value === 'destination') {
    const region = place.region || place
    const option = place.option || null
    destination.value = region.name
    selectedTransport.value = option ? {
      mode: option.type || '',
      fromHub: option.fromHub || '',
      toHub: option.toHub || region.name,
      oneWayMin: Number(option.oneWayMin) || 0,
      fare: Number(option.fare) || 0,
      originToHubMin: Number(option.originToHubMin) || 0,
      originToHubFare: Number(option.originToHubFare) || 0,
    } : null
    closePicker()
    return
  }
  if (pickerMode.value === 'editSpecific') {
    insertEditEntry(Math.max(0, pickerInsertIndex.value), {
      type: 'SPECIFIC_PLACE',
      pid: place.id || null,
      placeName: place.name,
      address: place.address || '',
      lat: Number(place.lat),
      lng: Number(place.lng),
      cat: 'sight',
      role: 'ANCHOR',
      stayMinutes: 60,
      fee: 0,
      feeType: 'free',
    })
    closePicker()
    return
  }
  if (pickerIdx.value >= 0) {
    anchors.value[pickerIdx.value].name  = place.name
    anchors.value[pickerIdx.value].place = place
  } else {
    if (anchors.value.length < 3) {
      const times = defaultAnchorTimes(pickerPeriod.value)
      const anchorId = `anchor-${++anchorSeq}`
      anchors.value.push({
        id:      anchorId,
        name:    place.name,
        place,
        arrTime: times.arrTime,
        depTime: times.depTime,
      })
    }
  }
  closePicker()
}

function addFixedPreference(periodKey) {
  if (anchors.value.length >= 3) return
  openPicker(-1, periodKey)
}

function addPinnedLocation() {
  if (anchors.value.length >= 3) return
  openPicker(-1, 'pins')
}

/* 피커 ESC 닫기 */
function onPickerKey(e) { if (e.key === 'Escape') closePicker() }

const summary = computed(() => [
  from.value.includes('GPS') ? '현재위치' : from.value,
  destination.value ? `목적지 ${destination.value}` : null,
  `${dep.value}–${arr.value}`,
  selectedTransport.value?.mode ? `${selectedTransport.value.mode} ${selectedTransport.value.fromHub}→${selectedTransport.value.toHub}` : null,
  activityPreferenceKeys.value.length ? activityPreferenceKeys.value.map(a => ACTIVITIES.find(x => x.k === a)?.l).filter(Boolean).join('·') : null,
  cafePreferenceEntries.value.length ? `카페 ${cafePreferenceEntries.value.length}곳` : null,
  hasPreferenceType('lunch') ? `점심 ${lunch.value.kind}` : null,
  hasPreferenceType('dinner') ? `저녁 ${dinner.value.kind}` : null,
  anchors.value.length ? `방문 ${anchors.value.length}곳` : null,
].filter(Boolean))

const budgetLabel = computed(() => budget.value === 0 ? '제한 없음' : budget.value.toLocaleString('ko-KR') + '원')
</script>

<template>
  <div class="screen-wrap" style="max-width:1240px">
    <header v-if="!result" class="cover" style="margin-bottom:22px">
      <div class="cover-meta">
        <span class="eyebrow">코스 조건 입력 · BRIEF</span>
        <span class="coords">Top-K 스코어링 + Greedy 동선 최적화</span>
      </div>
      <h1 class="disp" style="font-size:clamp(34px,5vw,60px)">
        오늘의 조건을 <span class="disp-i">적어주세요.</span>
      </h1>
      <p class="lede">조건은 다 선택 안 해도 돼요. 비워두면 균형 잡힌 코스를 자동으로 짜드려요.</p>
    </header>

    <!-- ── 추천 결과 (확정 전 미리보기) ───────────────────── -->
    <div v-if="result">
      <header class="cover" style="margin-bottom:22px">
        <div class="cover-meta">
          <span class="eyebrow">추천 코스 · 미리보기</span>
          <span class="coords">확정하면 일정 탭에 저장돼요</span>
        </div>
        <div style="display:flex;align-items:flex-end;justify-content:space-between;gap:24px;flex-wrap:wrap">
          <h1 class="disp" style="font-size:clamp(30px,4.5vw,52px)">
            {{ result.region }}, <span class="disp-i">하루의 윤곽.</span>
          </h1>
          <div style="display:flex;gap:8px">
            <button class="btn btn-outline btn-sm" style="border-radius:6px" @click="backToForm" :disabled="saving">
              <AppIcon name="swap" style="width:15px;height:15px" /> 조건 다시
            </button>
            <button class="btn btn-primary btn-sm" style="border-radius:6px" @click="confirmTrip" :disabled="saving">
              <AppIcon name="bookmark" style="width:15px;height:15px" /> {{ saving ? '저장 중…' : '이 일정 확정' }}
            </button>
          </div>
        </div>
        <p class="lede">{{ result.startPoint }} 출발 · {{ result.startTime }}~{{ result.endTime }} · {{ result.placeCount }}장소 코스</p>
      </header>

      <CourseTimeline
        :course="result"
        :return-limit="arr"
        editable
        :edit-entries="editEntries"
        :dirty="editDirty"
        :regenerating="regenerating"
        :activities="ACTIVITIES"
        :food-kinds="FOOD_KINDS"
        @remove-entry="removeEditEntry"
        @update-entry="updateEditEntry"
        @add-activity="addEditActivity"
        @add-cafe="addEditCafe"
        @add-meal="addEditMeal"
        @add-specific="openEditSpecificPicker"
        @regenerate="runRegenerate"
      />

      <div style="display:flex;gap:10px;justify-content:center;margin-top:24px">
        <button class="btn btn-outline" @click="backToForm" :disabled="saving">조건 다시 정하기</button>
        <button class="btn btn-primary btn-lg" @click="confirmTrip" :disabled="saving">
          <AppIcon name="bookmark" style="width:18px;height:18px" /> {{ saving ? '저장 중…' : '이 일정으로 확정하기' }}
        </button>
      </div>
    </div>

    <div v-if="!result" style="display:grid;grid-template-columns:minmax(0,1fr) 300px;gap:22px;align-items:start" class="trip-grid">
      <div>
        <!-- 1. 출발지·시간 -->
        <div class="card section-card">
          <div class="sec-hd">
            <span class="sec-n">1</span>
            <div><div style="font-weight:700;font-size:15.5px">출발지 · 시간</div><div style="font-size:12.5px;color:var(--ink-faint)">필수</div></div>
          </div>
          <div style="display:grid;grid-template-columns:1.2fr 1.2fr .8fr .8fr;gap:14px" class="field-row">
            <label class="flbl">
              <span>출발지</span>
              <div style="display:flex;gap:6px">
                <input v-model="from" class="inp" />
                <button class="badge badge-accent" style="border:none;cursor:pointer;flex-shrink:0" @click="useGps" :disabled="gpsLocating">
                  {{ gpsLocating ? '...' : 'GPS' }}
                </button>
              </div>
            </label>
            <label class="flbl">
              <span>목적지</span>
              <div style="display:flex;gap:6px">
                <input v-model="destination" class="inp" placeholder="예: 강릉, 부산, 성수동" />
                <button class="badge badge-accent" style="border:none;cursor:pointer;flex-shrink:0" @click="openDestinationPicker">REGION</button>
              </div>
            </label>
            <label class="flbl"><span>출발 시간</span><input type="time" v-model="dep" class="inp" /></label>
            <label class="flbl"><span>귀가 시간</span><input type="time" v-model="arr" class="inp" /></label>
          </div>
        </div>

        <!-- 2. 선호 순서 -->
        <div class="card section-card">
          <div class="sec-hd">
            <span class="sec-n">2</span>
            <div>
              <div style="font-weight:700;font-size:15.5px">선호 순서</div>
              <div style="font-size:12.5px;color:var(--ink-faint)">엄격한 시간표가 아니라 오전·오후·저녁 안에서 우선순위를 잡아요</div>
            </div>
          </div>

          <div class="pref-palette">
            <button
              v-for="a in ACTIVITIES"
              :key="a.k"
              class="pref-add"
              type="button"
              draggable="true"
              :disabled="isAddDisabled(a.k)"
              @dragstart="onPaletteDragStart($event, a.k)"
              @dragend="onPrefDragEnd"
            >
              <span>{{ a.e }}</span>{{ a.l }}
            </button>
            <button
              v-for="p in SPECIAL_PREFS"
              :key="p.k"
              class="pref-add"
              type="button"
              :draggable="!isAddDisabled(p.k)"
              :disabled="isAddDisabled(p.k)"
              @dragstart="onPaletteDragStart($event, p.k)"
              @dragend="onPrefDragEnd"
            >
              <span>{{ p.e }}</span>{{ p.l }}
            </button>
          </div>

          <div class="pref-board">
            <section
              v-for="period in PERIODS"
              :key="period.k"
              class="pref-lane"
              :class="{ 'pref-lane-inactive': estimateForPeriod(period.k)?.inactive }"
              @dragover.prevent
              @drop="onPrefDropEnd($event, period.k)"
            >
              <div class="pref-lane-head">
                <div>
                  <strong>{{ period.l }}</strong>
                  <span>{{ period.hint }}</span>
                </div>
                <div
                  v-if="estimateForPeriod(period.k)"
                  class="estimate-pill"
                  :class="'estimate-' + estimateForPeriod(period.k).status.toLowerCase()"
                >
                  {{ estimateForPeriod(period.k).status }}
                  <small>{{ fmtMin(estimateForPeriod(period.k).estimated) }} / {{ fmtMin(estimateForPeriod(period.k).usable) }}</small>
                </div>
                <button
                  class="pref-fixed-btn"
                  :disabled="anchors.length >= 3 || estimateForPeriod(period.k)?.inactive"
                  @click="addFixedPreference(period.k)"
                >
                  <AppIcon name="plus" style="width:13px;height:13px" /> 장소
                </button>
              </div>

              <div v-if="groupByKey(period.k)?.items.length === 0" class="pref-empty">
                위의 활동을 여기로 드래그하세요.
              </div>

              <article
                v-for="(item, index) in groupByKey(period.k)?.items"
                :key="item.id"
                class="pref-card"
                :class="[
                  { 'pref-card-fixed': item.type === 'fixed' },
                  dropClass(period.k, index),
                ]"
                draggable="true"
                @dragstart="onPrefDragStart($event, period.k, index)"
                @dragend="onPrefDragEnd"
                @dragenter.prevent.stop="setDropIndicator($event, period.k, index)"
                @dragover.prevent.stop="setDropIndicator($event, period.k, index)"
                @drop.stop="onPrefDrop($event, period.k, index)"
              >
                <div class="pref-card-main">
                  <AppIcon name="drag" class="pref-drag" />
                  <div class="pref-mark">{{ prefDef(item.type).e }}</div>
                  <div class="pref-copy">
                    <strong>{{ preferenceLabel(item) }}</strong>
                    <span>{{ preferenceMeta(item) }}</span>
                  </div>
                  <button class="pref-remove" @click="removePreference(period.k, index)">×</button>
                </div>

                <div v-if="item.type === 'cafe'" class="pref-options">
                  <button
                    v-for="c in CAFE_KINDS"
                    :key="c"
                    class="chip"
                    :class="{ on: cafeKind(item) === c }"
                    @click="setCafeKind(item, c)"
                  >{{ c }}</button>
                </div>

                <div v-if="item.type === 'lunch' || item.type === 'dinner'" class="pref-options pref-options-scroll">
                  <button
                    v-for="f in FOOD_KINDS"
                    :key="f"
                    class="chip"
                    :class="{ on: mealState(item.type).kind === f }"
                    @click="setMealKind(item.type, f)"
                  >{{ f }}</button>
                </div>

                <div v-if="item.type === 'fixed'" class="pref-options">
                  <button class="btn btn-ghost btn-sm" @click="openPicker(anchors.findIndex((a) => a.id === item.anchorId), period.k)">
                    <AppIcon name="pin" style="width:14px;height:14px" /> 장소 변경
                  </button>
                </div>
              </article>
            </section>
          </div>
        </div>

        <!-- 3. 예산 -->
        <div class="card section-card">
          <div class="sec-hd">
            <span class="sec-n">3</span>
            <div><div style="font-weight:700;font-size:15.5px">예산 가이드</div></div>
          </div>
          <div style="padding:4px 2px">
            <div style="display:flex;justify-content:space-between;align-items:baseline;margin-bottom:8px">
              <span style="font-size:13.5px;font-weight:600">소프트 예산 가이드</span>
              <span class="mono" style="font-size:17px;font-weight:700;color:var(--accent-deep)">{{ budgetLabel }}</span>
            </div>
            <input type="range" min="0" max="120000" step="5000" v-model.number="budget"
                   style="width:100%;accent-color:var(--accent)" />
            <div style="display:flex;justify-content:space-between;font-size:11px;color:var(--ink-faint);margin-top:2px" class="mono">
              <span>제한없음</span><span>120,000원</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Sticky summary -->
      <div style="position:sticky;top:18px">
        <div class="card" style="overflow:hidden">
          <div style="padding:16px 18px;background:var(--accent-wash)">
            <div class="eyebrow" style="color:var(--accent-deep)">내 조건 요약</div>
            <div style="font-size:15px;font-weight:700;margin-top:6px">이 조건으로 코스를 짜요</div>
          </div>
          <div style="padding:14px 18px;display:flex;flex-wrap:wrap;gap:7px">
            <span v-for="s in summary" :key="s"
                  style="font-size:12px;background:var(--card-sunken);color:var(--ink-soft);padding:5px 10px;border-radius:999px;font-weight:500">{{ s }}</span>
          </div>
          <div style="padding:0 18px 18px">
            <div v-if="overfullEstimate" class="estimate-block-message">
              {{ estimateMessage }}
            </div>
            <button
              class="btn btn-primary btn-block btn-lg"
              :class="{ 'btn-soft-blocked': overfullEstimate }"
              @click="runCourse"
              :disabled="generating"
            >
              <AppIcon name="route" style="width:18px;height:18px" />
              {{ generating ? '코스 짜는 중…' : '코스 추천 받기' }}
            </button>
            <p style="font-size:11px;color:var(--ink-faint);text-align:center;margin:10px 0 0;line-height:1.5">
              Top-K 스코어링 + Greedy 동선 최적화로<br>하루 코스를 만들어요
            </p>
          </div>
        </div>

        <div class="card visit-card">
          <div class="visit-card-head">
            <div class="eyebrow" style="color:var(--accent-deep)">방문 장소</div>
            <div style="font-size:13.5px;font-weight:700;margin-top:5px">꼭 들를 장소를 먼저 담아요</div>
          </div>
          <div class="pinned-panel summary-panel">
            <div class="pinned-note">
              직접 드래그해서 선호 순서에 배치하지 않은 장소는 코스 생성 시 예상 이동 시간을 기준으로 더 짧은 순서로 자동 재정렬됩니다.
            </div>
            <button
              class="pref-fixed-btn pinned-add"
              type="button"
              :disabled="anchors.length >= 3"
              @click="addPinnedLocation"
            >
              <AppIcon name="plus" style="width:13px;height:13px" /> 방문 장소 추가
            </button>

            <div v-if="anchors.length === 0" class="pref-empty">
              아직 방문 장소가 없습니다. 장소를 추가한 뒤 선호 순서로 드래그해 배치할 수 있어요.
            </div>

            <article
              v-for="anchor in anchors"
              :key="anchor.id"
              class="pref-card pref-card-fixed pinned-card"
              :class="{ 'pinned-card-placed': isAnchorPlaced(anchor.id) }"
              :draggable="!isAnchorPlaced(anchor.id)"
              @dragstart="onAnchorDragStart($event, anchor.id)"
              @dragend="onPrefDragEnd"
            >
              <div class="pref-card-main">
                <AppIcon name="drag" class="pref-drag" />
                <div class="pref-mark">PIN</div>
                <div class="pref-copy">
                  <strong>{{ anchor.name }}</strong>
                  <span>{{ fixedCategoryLabel(anchor) }} · {{ anchor.place?.address || '실제 장소 고정' }}</span>
                </div>
                <span v-if="isAnchorPlaced(anchor.id)" class="pinned-state">배치됨</span>
                <button class="pref-remove" @click="removeAnchor(anchor.id)">×</button>
              </div>
            </article>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- ── 지도 피커 모달 ───────────────────────────────── -->
  <!-- Destination uses region picker; fixed places use Kakao place picker -->
  <Teleport to="body">
    <div v-if="pickerOpen" class="picker-backdrop" @keydown="onPickerKey" @click.self="closePicker">
      <div class="picker-modal">
        <div class="picker-head">
          <span style="font-weight:700;font-size:15px">{{ pickerMode === 'destination' ? '지역 선택' : '장소 선택' }}</span>
          <button @click="closePicker" style="color:var(--ink-faint);line-height:1">×</button>
        </div>

        <div class="picker-search">
          <AppIcon name="search" style="width:15px;height:15px;flex-shrink:0;color:var(--ink-faint)" />
          <input
            v-model="pickerQ"
            class="picker-inp"
            :placeholder="pickerMode === 'destination' ? '지역명으로 검색 (예: 부산, 경주)' : '장소명으로 검색 (예: 경복궁, 성수 카페)'"
            @keydown.enter="searchPlace"
            autofocus
          />
          <button v-if="pickerMode !== 'destination'" class="btn btn-primary btn-sm" style="flex-shrink:0" @click="searchPlace" :disabled="pickerSearching">
            {{ pickerSearching ? '검색 중' : '검색' }}
          </button>
        </div>

        <div v-if="pickerMode === 'destination'" class="picker-region-body">
          <div class="region-sort-row">
            <button
              v-for="[k, l] in SORTS"
              :key="k"
              class="region-sort-btn"
              :class="{ 'region-sort-on': regionSort === k }"
              @click="regionSort = k"
            >{{ l }}</button>
          </div>

          <div v-if="regionsLoading" style="padding:20px 16px;font-size:13px;color:var(--ink-faint);text-align:center">
            당일치기 가능 지역을 불러오는 중입니다.
          </div>
          <div v-else-if="regionsError" style="padding:20px 16px;font-size:13px;color:var(--trend);text-align:center">
            {{ regionsError }}
          </div>
          <div v-else-if="regionsSearched && filteredRegions.length === 0" style="padding:20px 16px;font-size:13px;color:var(--ink-faint);text-align:center">
            조건에 맞는 지역이 없습니다. 출발지나 시간을 조정해 주세요.
          </div>

          <article
            v-for="r in filteredRegions"
            :key="r.id || r.name"
            class="region-choice"
            :class="{ 'region-choice-on': pickerSel?.name === r.name }"
            @click="selectRegionOpt(r, getSelIdx(r))"
          >
            <div class="region-choice-main">
              <div style="min-width:0">
                <div class="region-choice-name">{{ r.name }}</div>
                <div class="region-choice-meta">
                  {{ r.sido || '지역' }} · 왕복 {{ r.self ? '—' : fmtMin(effRoundTrip(r)) }} · {{ r.self ? '무료' : won(effFare(r)) + '원' }} · 체류 {{ fmtMin(effStay(r)) }}
                </div>
              </div>
              <span v-if="r.hot" class="region-hot">PICK</span>
            </div>

            <div v-if="r.options && r.options.length" class="region-options">
              <button
                v-for="(opt, oi) in r.options"
                :key="opt.type + '-' + opt.fromHub + '-' + opt.toHub"
                class="region-opt"
                :class="{ 'region-opt-on': getSelIdx(r) === oi }"
                @click.stop="selectRegionOpt(r, oi)"
              >
                <span class="mono region-opt-type">{{ opt.type }}</span>
                <span class="region-opt-route"><b>{{ opt.toHub }}</b><span> · {{ opt.fromHub }} 출발</span></span>
                <span class="region-opt-detail">
                  편도 {{ fmtMin(opt.oneWayMin) }} · {{ won(opt.fare) }}원
                  <template v-if="opt.dailyTrips > 0"> · 하루 {{ opt.dailyTrips }}회</template>
                </span>
              </button>
            </div>
            <button v-else class="region-opt region-opt-on" @click.stop="selectRegionOpt(r, 0)">
              <span class="mono region-opt-type">LOCAL</span>
              <span class="region-opt-route"><b>{{ r.name }}</b></span>
              <span class="region-opt-detail">지역 내 이동</span>
            </button>
          </article>
          <div v-if="regionsSearched && filteredRegions.length" class="region-count">
            {{ filteredRegions.length }}곳 표시
          </div>
        </div>

        <div v-else class="picker-body">
          <div class="picker-list">
            <div v-if="pickerResults.length === 0 && !pickerSearching"
                 style="padding:20px 16px;font-size:13px;color:var(--ink-faint);text-align:center">
              <div style="font-size:24px;margin-bottom:8px">⌕</div>
              장소명을 입력하고 검색해 주세요.
            </div>
            <button
              v-for="p in pickerResults"
              :key="p.name + p.address"
              class="picker-result"
              :class="{ 'picker-result-on': pickerSel?.name === p.name && pickerSel?.address === p.address }"
              @click="selectResult(p)"
            >
              <div style="font-weight:600;font-size:13.5px;text-align:left">{{ p.name }}</div>
              <div style="font-size:11.5px;color:var(--ink-faint);margin-top:2px;text-align:left">{{ p.address }}</div>
              <span v-if="p.category" style="font-size:11px;color:var(--ink-faint);text-align:left">{{ p.category }}</span>
            </button>
          </div>

          <div ref="pickerMapEl" class="picker-map"></div>
        </div>

        <div class="picker-foot">
          <button class="btn btn-ghost" @click="closePicker">취소</button>
          <button class="btn btn-primary" :disabled="!pickerSel" @click="confirmPicker">
            <AppIcon name="pin" style="width:15px;height:15px" />
            {{ pickerSel ? pickerSel.name + ' 선택' : (pickerMode === 'destination' ? '지역을 선택하세요' : '장소를 선택하세요') }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>

  <!-- 일정 제목 입력 모달 -->
  <Teleport to="body">
    <div v-if="showTitleModal"
         style="position:fixed;inset:0;background:rgba(0,0,0,.45);z-index:2000;display:flex;align-items:center;justify-content:center;padding:16px"
         @click.self="showTitleModal = false">
      <div class="card" style="width:100%;max-width:440px;padding:28px 26px">
        <h3 style="margin:0 0 6px;font-size:17px;font-weight:700">일정 이름을 정해 주세요</h3>
        <p style="margin:0 0 18px;font-size:13px;color:var(--ink-faint)">나중에 일정 탭에서 이 이름으로 찾을 수 있어요.</p>
        <input
          v-model="tripTitleInput"
          placeholder="예) 부산 당일치기"
          maxlength="50"
          style="width:100%;box-sizing:border-box;padding:11px 13px;border-radius:10px;border:1.5px solid var(--line-strong);background:var(--card);font-size:14px;outline:none;margin-bottom:16px"
          @keyup.enter="doSaveTrip"
          ref="titleInputEl"
        />
        <div style="display:flex;gap:8px;justify-content:flex-end">
          <button class="btn btn-outline btn-sm" @click="showTitleModal = false">취소</button>
          <button class="btn btn-primary btn-sm" @click="doSaveTrip">
            <AppIcon name="bookmark" style="width:14px;height:14px" /> 이 이름으로 저장
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.section-card { padding: 20px 22px; margin-bottom: 14px; }
.sec-hd { display: flex; align-items: flex-start; gap: 11px; margin-bottom: 15px; }
.sec-n {
  width: 24px; height: 24px; border-radius: 7px;
  background: var(--accent-wash); color: var(--accent-deep);
  display: grid; place-items: center; font-size: 12.5px; font-weight: 700;
  font-family: var(--mono); flex-shrink: 0; margin-top: 1px;
}
.flbl { display: block; }
.flbl > span { font-size: 11.5px; color: var(--ink-faint); font-weight: 600; display: block; margin-bottom: 5px; }
.inp {
  width: 100%; border: 1px solid var(--line-strong); border-radius: 9px;
  background: var(--card); font-size: 14px; font-weight: 600;
  padding: 8px 10px; outline: none;
}
.pref-palette {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;
}
.visit-card {
  margin-top: 14px;
  overflow: hidden;
}
.visit-card-head {
  padding: 14px 18px 0;
}
.pinned-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.summary-panel {
  padding: 14px 18px;
}
.pinned-note {
  border: 1px solid color-mix(in srgb, var(--accent) 36%, var(--line));
  border-radius: 9px;
  background: var(--accent-wash);
  color: var(--accent-deep);
  padding: 10px 12px;
  font-size: 12.5px;
  line-height: 1.45;
  font-weight: 700;
}
.pinned-add {
  align-self: flex-start;
}
.pinned-card {
  margin-bottom: 0;
}
.pinned-card-placed {
  opacity: .62;
}
.pinned-card-placed .pref-drag {
  cursor: default;
}
.pinned-state {
  flex-shrink: 0;
  border-radius: 999px;
  background: var(--card-sunken);
  color: var(--ink-faint);
  padding: 3px 7px;
  font-size: 11px;
  font-weight: 800;
}
.pref-add {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--card);
  padding: 7px 11px;
  font-size: 12.5px;
  font-weight: 700;
  color: var(--ink-soft);
  cursor: grab;
}
.pref-add:active { cursor: grabbing; }
.pref-add span {
  font-family: var(--mono);
  font-size: 10px;
  color: var(--accent-deep);
}
.pref-add:disabled {
  opacity: .42;
  cursor: default;
}
.pref-board {
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px;
}
.pref-lane {
  min-height: 120px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--card-sunken);
  padding: 11px;
}
.pref-lane-inactive {
  opacity: .52;
  background: color-mix(in srgb, var(--card-sunken) 55%, var(--line));
}
.pref-lane-inactive .pref-empty {
  background: transparent;
}
.pref-lane-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}
.pref-lane-head strong {
  display: block;
  font-size: 14px;
}
.pref-lane-head span {
  display: block;
  margin-top: 2px;
  font-size: 11.5px;
  color: var(--ink-faint);
}
.estimate-pill {
  margin-left: auto;
  flex-shrink: 0;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--card);
  padding: 4px 7px;
  color: var(--ink-soft);
  font-family: var(--mono);
  font-size: 10px;
  font-weight: 800;
  text-align: right;
}
.estimate-pill small {
  display: block;
  margin-top: 1px;
  color: var(--ink-faint);
  font-size: 9.5px;
  font-weight: 700;
}
.estimate-ok {
  border-color: color-mix(in srgb, var(--free) 35%, var(--line));
  color: var(--free);
}
.estimate-tight {
  border-color: color-mix(in srgb, var(--accent) 45%, var(--line));
  color: var(--accent-deep);
}
.estimate-overfull {
  border-color: color-mix(in srgb, var(--trend) 45%, var(--line));
  color: var(--trend);
}
.estimate-off {
  color: var(--ink-faint);
  border-color: var(--line);
}
.estimate-block-message {
  border: 1px solid color-mix(in srgb, var(--trend) 36%, var(--line));
  border-radius: 8px;
  background: var(--trend-wash);
  color: var(--trend);
  padding: 9px 10px;
  margin-bottom: 10px;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.45;
}
.btn-soft-blocked {
  opacity: .48;
  filter: grayscale(.2);
  cursor: not-allowed;
}
.pref-fixed-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
  border: 1px solid var(--line);
  border-radius: 7px;
  background: var(--card);
  padding: 5px 8px;
  font-size: 11.5px;
  font-weight: 700;
  color: var(--accent-deep);
}
.pref-fixed-btn:disabled {
  opacity: .45;
  cursor: default;
}
.pref-empty {
  border: 1px dashed var(--line-strong);
  border-radius: 10px;
  padding: 16px 10px;
  color: var(--ink-faint);
  font-size: 12.5px;
  text-align: center;
}
.pref-card {
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--card);
  margin-bottom: 9px;
  overflow: hidden;
  box-shadow: var(--shadow-sm);
  position: relative;
}
.pref-card.drop-before,
.pref-card.drop-after {
  overflow: visible;
}
.pref-card.drop-before::before,
.pref-card.drop-after::after {
  content: "";
  position: absolute;
  left: 8px;
  right: 8px;
  height: 3px;
  border-radius: 999px;
  background: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-wash);
  z-index: 3;
}
.pref-card.drop-before::before {
  top: -8px;
}
.pref-card.drop-after::after {
  bottom: -8px;
}
.pref-card-fixed {
  border-color: color-mix(in srgb, var(--accent) 38%, var(--line));
}
.pref-card-main {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 10px;
}
.pref-drag {
  width: 15px;
  height: 15px;
  color: var(--ink-faint);
  flex-shrink: 0;
  cursor: grab;
}
.pref-mark {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  background: var(--accent-wash);
  color: var(--accent-deep);
  font-family: var(--mono);
  font-size: 10px;
  font-weight: 800;
  flex-shrink: 0;
}
.pref-copy {
  min-width: 0;
  flex: 1;
}
.pref-copy strong,
.pref-copy span {
  display: block;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.pref-copy strong {
  font-size: 13.5px;
}
.pref-copy span {
  margin-top: 2px;
  font-size: 11.5px;
  color: var(--ink-faint);
}
.pref-remove {
  width: 24px;
  height: 24px;
  border-radius: 6px;
  color: var(--ink-faint);
  font-size: 18px;
  line-height: 1;
  flex-shrink: 0;
}
.pref-remove:hover {
  background: var(--card-sunken);
  color: var(--ink);
}
.pref-options {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  border-top: 1px solid var(--line);
  padding: 9px 10px 10px;
}
.pref-options .chip {
  padding: 5px 10px;
  font-size: 12px;
}
.pref-options-scroll {
  flex-wrap: nowrap;
  overflow-x: auto;
  overscroll-behavior-x: contain;
  scrollbar-width: thin;
}
.pref-options-scroll .chip {
  flex: 0 0 auto;
}
.slot-time-row {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.mini-time-lbl {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--ink-faint);
  font-weight: 600;
}
.mini-time-inp {
  width: 94px;
  padding: 5px 7px;
  font-size: 12.5px;
}
.meal-window-note {
  margin-top: 9px;
  font-size: 12.5px;
  color: var(--ink-faint);
  line-height: 1.35;
}
.toggle-btn {
  width: 44px; height: 25px; border-radius: 999px; padding: 3px;
  background: var(--line-strong); transition: background .15s; flex-shrink: 0;
}
.toggle-on { background: var(--accent); }
.toggle-knob {
  display: block; width: 19px; height: 19px; border-radius: 50%;
  background: white; transition: transform .15s; box-shadow: var(--shadow-sm);
}

/* ── 고정 장소 카드 ── */
.anchor-card {
  border: 1.5px solid var(--line);
  border-radius: 14px;
  overflow: hidden;
}
.anchor-place {
  display: flex; align-items: center; gap: 10px;
  padding: 12px 14px;
  cursor: pointer;
  background: var(--card);
  border-bottom: 1px solid var(--line);
  transition: background .1s;
}
.anchor-place:hover { background: var(--accent-wash); }
.anchor-times {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 14px;
  background: var(--card-sunken);
}
.time-lbl {
  display: flex; flex-direction: column; gap: 3px;
}
.time-lbl > span {
  font-size: 10.5px; color: var(--ink-faint); font-weight: 600; letter-spacing: .02em;
}
.time-inp { width: 110px; font-size: 13px; padding: 6px 8px; }

/* ── 피커 모달 ── */
.picker-backdrop {
  position: fixed; inset: 0; z-index: 1000;
  background: rgba(0,0,0,.45);
  display: flex; align-items: center; justify-content: center;
  padding: 24px;
}
.picker-modal {
  background: var(--card);
  border-radius: 18px;
  width: min(860px, 100%);
  max-height: 82vh;
  display: flex; flex-direction: column;
  overflow: hidden;
  box-shadow: 0 24px 64px rgba(0,0,0,.25);
}
.picker-head {
  display: flex; align-items: center; justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--line);
  flex-shrink: 0;
}
.picker-search {
  display: flex; align-items: center; gap: 10px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--line);
  flex-shrink: 0;
}
.picker-inp {
  flex: 1; border: 1px solid var(--line-strong); border-radius: 8px;
  padding: 8px 12px; font-size: 14px; font-weight: 500; outline: none;
  background: var(--card);
}
.picker-body {
  display: grid;
  grid-template-columns: 260px 1fr;
  flex: 1;
  overflow: hidden;
}
.picker-region-body {
  min-height: 360px;
  max-height: min(60vh, 520px);
  overflow-y: auto;
  padding: 8px;
}
.region-sort-row {
  display: flex;
  gap: 10px;
  padding: 8px 8px 12px;
  border-bottom: 1px solid var(--line);
  margin-bottom: 4px;
  flex-wrap: wrap;
}
.region-sort-btn {
  font-family: var(--mono);
  font-size: 11.5px;
  font-weight: 700;
  color: var(--ink-faint);
  border-bottom: 1.5px solid transparent;
  padding-bottom: 2px;
}
.region-sort-on {
  color: var(--accent-deep);
  border-bottom-color: var(--accent);
}
.region-choice {
  border-bottom: 1px solid var(--line);
  padding: 12px 10px;
  cursor: pointer;
}
.region-choice:hover {
  background: var(--card-sunken);
}
.region-choice-on {
  background: var(--accent-wash);
  border-left: 3px solid var(--accent);
}
.region-choice-main {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 8px;
}
.region-choice-name {
  font-weight: 800;
  font-size: 15px;
  line-height: 1.2;
}
.region-choice-meta {
  font-size: 11.5px;
  color: var(--ink-faint);
  margin-top: 3px;
  line-height: 1.35;
}
.region-hot {
  flex-shrink: 0;
  font-size: 10px;
  font-weight: 800;
  color: var(--trend);
  border: 1px solid color-mix(in srgb, var(--trend) 35%, var(--line));
  border-radius: 999px;
  padding: 2px 7px;
}
.region-options {
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.region-opt {
  width: 100%;
  display: grid;
  grid-template-columns: 58px minmax(0, 1fr) auto;
  align-items: center;
  gap: 7px;
  border: 1px solid var(--line);
  border-radius: 5px;
  background: var(--card);
  padding: 7px 9px;
  text-align: left;
}
.region-opt:hover {
  border-color: var(--accent);
}
.region-opt-on {
  border-color: var(--accent-deep);
  background: var(--accent-wash);
}
.region-opt-type {
  font-size: 11px;
  font-weight: 800;
  color: var(--accent-deep);
}
.region-opt-route {
  font-size: 12px;
  color: var(--ink);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.region-opt-route span {
  color: var(--ink-faint);
  font-size: 11px;
}
.region-opt-detail {
  font-size: 11.5px;
  color: var(--ink-soft);
  white-space: nowrap;
}
.region-count {
  padding: 10px;
  text-align: right;
  color: var(--ink-faint);
  font-size: 11.5px;
}
.picker-list {
  overflow-y: auto;
  border-right: 1px solid var(--line);
}
.picker-result {
  width: 100%;
  padding: 12px 14px;
  border-bottom: 1px solid var(--line);
  cursor: pointer;
  background: transparent;
  transition: background .1s;
  text-align: left;
}
.picker-result:hover { background: var(--card-sunken); }
.picker-result-on { background: var(--accent-wash) !important; border-left: 3px solid var(--accent); }
.picker-map {
  height: 100%;
  min-height: 300px;
}
.picker-foot {
  display: flex; justify-content: flex-end; gap: 10px;
  padding: 14px 18px;
  border-top: 1px solid var(--line);
  flex-shrink: 0;
}
@media (max-width: 720px) {
  .region-opt {
    grid-template-columns: 52px minmax(0, 1fr);
  }
  .region-opt-detail {
    grid-column: 2;
    white-space: normal;
  }
}
</style>
