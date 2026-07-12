<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon    from '../components/ui/AppIcon.vue'
import TrendMeter from '../components/ui/TrendMeter.vue'
import ImgPh      from '../components/ui/ImgPh.vue'
import { won } from '../data/mock.js'
import { searchRegions, geocodeCoord } from '../api/gadang.js'
import { requestLocation, setLocationName } from '../utils/geo.js'

const router  = useRouter()
const cond    = ref({ from: '현재 위치 (GPS)', dep: '08:00', arr: '20:00' })
const sort    = ref('rec')
const regions = ref([])
const loading = ref(false)
const error   = ref('')
const searched = ref(false)

const gpsCoord   = ref(null)
const gpsName    = ref('')     // 역지오코딩된 위치명 캐시
const gpsLocating = ref(false) // GPS 위치명 로딩 중

// 페이지 진입 즉시 위치를 요청 — App.vue에서 이미 선요청했다면 캐시를 즉시 재사용해
// 대기 없이 끝난다. 권한 결정 전이라도 바로 요청해 이후 탭 전환 시 지연을 최소화한다.
onMounted(async () => {
  // 이전 검색 결과가 있으면 복원하고 GPS 선조회는 생략 (이미 검색 완료 상태)
  if (restoreState()) return
  try {
    const loc = await requestLocation()
    if (!loc) return
    gpsCoord.value = { lat: loc.c[0], lng: loc.c[1] }
    if (loc.n) {
      gpsName.value = loc.n
      if (cond.value.from.includes('GPS')) cond.value.from = loc.n
      return
    }
    const geo = await geocodeCoord(loc.c[0], loc.c[1])
    if (geo?.name) {
      gpsName.value = geo.name
      setLocationName(geo.name)
      if (cond.value.from.includes('GPS')) cond.value.from = geo.name
    }
  } catch { /* 선조회 실패는 무시 — 버튼 경로가 다시 시도함 */ }
})

async function useGps() {
  if (!navigator.geolocation) { cond.value.from = '서울역'; return }
  // 선조회된 좌표가 있으면 이름 여부 관계없이 즉시 사용 (이름은 onMounted 선조회가 완료되면 채워짐)
  if (gpsCoord.value) { cond.value.from = gpsName.value || '현재 위치'; return }

  gpsLocating.value = true
  cond.value.from = '위치 확인 중...'
  const loc = await requestLocation()
  if (loc) {
    gpsCoord.value = { lat: loc.c[0], lng: loc.c[1] }
    let name = loc.n
    if (!name) {
      const geo = await geocodeCoord(loc.c[0], loc.c[1])
      name = geo?.name || ''
      if (name) setLocationName(name)
    }
    cond.value.from = name || '현재 위치 (GPS)'
    if (name) gpsName.value = name
  } else {
    cond.value.from = '서울역'
    gpsCoord.value = null
  }
  gpsLocating.value = false
}

// 오래 걸리는 첫 조회 동안 진행 단계를 보여주는 회전 메시지
const LOADING_STEPS = [
  '가까운 역·터미널 찾는 중',
  '오늘 직통 교통편 조회 중',
  'KTX·시외버스 시간표 확인 중',
  '검색 트렌드 분석 중',
  '지역 사진 모으는 중',
  '거의 다 됐어요',
]
const loadingMsg = ref(LOADING_STEPS[0])
let _loadingTimer = null

function startLoadingMsg() {
  let i = 0
  loadingMsg.value = LOADING_STEPS[0]
  _loadingTimer = setInterval(() => {
    i = Math.min(i + 1, LOADING_STEPS.length - 1) // 마지막 메시지에서 멈춤
    loadingMsg.value = LOADING_STEPS[i]
  }, 4000)
}
function stopLoadingMsg() {
  if (_loadingTimer) { clearInterval(_loadingTimer); _loadingTimer = null }
}

async function doSearch() {
  // GPS 측위가 진행 중이면 완료될 때까지 기다림
  if (gpsLocating.value) {
    await requestLocation()
  }
  loading.value = true
  error.value   = ''
  startLoadingMsg()
  try {
    // GPS 모드면 검색 전에 좌표 자동 획득
    if (cond.value.from.includes('GPS') || cond.value.from === '현재 위치') {
      await useGps()
    }
    const params = {
      from: cond.value.from,
      dep:  cond.value.dep,
      arr:  cond.value.arr,
    }
    if (gpsCoord.value) {
      params.lat = gpsCoord.value.lat
      params.lng = gpsCoord.value.lng
    }
    const data = await searchRegions(params)
    regions.value = data || []
    searched.value = true
    imgIdxMap.value = {}
    // 지도/코스 다녀온 뒤 복귀 시 결과 유지 (컴포넌트 unmount 대비)
    saveState()
  } catch (e) {
    error.value = '지역 조회 중 오류가 발생했습니다.'
  } finally {
    loading.value = false
    stopLoadingMsg()
  }
}

const STATE_KEY = 'gadang_home_state'
function saveState() {
  try {
    sessionStorage.setItem(STATE_KEY, JSON.stringify({
      cond: cond.value, regions: regions.value, searched: searched.value,
      sort: sort.value, gpsCoord: gpsCoord.value,
    }))
  } catch {}
}
function restoreState() {
  try {
    const s = JSON.parse(sessionStorage.getItem(STATE_KEY) || 'null')
    if (!s || !s.searched) return false
    cond.value = s.cond ?? cond.value
    regions.value = s.regions ?? []
    searched.value = true
    sort.value = s.sort ?? sort.value
    gpsCoord.value = s.gpsCoord ?? null
    return true
  } catch { return false }
}

// ── 추천 점수: 정규화 + 곱셈형(Cobb-Douglas) ──────────────────
// 단순 가중합은 두 변수의 분포 폭이 달라 명목 가중치가 왜곡됨
// (트렌드 변동폭 ~80 vs 체류비율 ~35 → 50:50이 실제론 트렌드 쏠림).
//
//   추천점수 = normTrend^TREND_EXP × stayRatio^STAY_EXP
//
// · normTrend: 결과 집합 내 min-max 정규화 (0.05~1, 0이면 점수 전체가 죽으므로 하한)
// · stayRatio: 체류시간 / 가용시간
// · 곱셈형이라 한쪽이 바닥이면 못 만회 — "둘 다 어느 정도는 돼야 추천" 직관
// · STAY_EXP > TREND_EXP: 당일치기 서비스라 이동시간 쪽을 우대
//   (β=1.5는 원거리 대도시가 여전히 상위권, 3.0은 근거리 소도시 쏠림 — 2.5가 균형)
const TREND_EXP = 1.0
const STAY_EXP  = 2.5

const recScores = computed(() => {
  const dests = regions.value.filter((r) => !r.self)
  const trends = dests.map((r) => r.trend || 0)
  const tMin = Math.min(...trends), tMax = Math.max(...trends)
  const span = tMax - tMin

  const map = {}
  for (const r of dests) {
    const normTrend = span > 0 ? 0.05 + 0.95 * ((r.trend || 0) - tMin) / span : 1
    const total = (r.stay || 0) + (r.roundTrip || 0)
    const stayRatio = total > 0 ? r.stay / total : 0
    map[r.id || r.name] =
      Math.pow(normTrend, TREND_EXP) * Math.pow(stayRatio, STAY_EXP) * 100
  }
  return map
})
const recScore = (r) => recScores.value[r.id || r.name] || 0

const sorted = computed(() => {
  const list = [...regions.value]
  const selfFirst = (a, b) => (a.self === b.self ? 0 : a.self ? -1 : 1)
  if (sort.value === 'rec')   return list.sort((a, b) => selfFirst(a, b) || recScore(b) - recScore(a))
  if (sort.value === 'trend') return list.sort((a, b) => selfFirst(a, b) || b.trend - a.trend)
  if (sort.value === 'time')  return list.sort((a, b) => selfFirst(a, b) || a.roundTrip - b.roundTrip)
  return list.sort((a, b) => selfFirst(a, b) || a.fare - b.fare)
})

function gap(a, b) {
  const [ah, am] = a.split(':').map(Number)
  const [bh, bm] = b.split(':').map(Number)
  let m = (bh * 60 + bm) - (ah * 60 + am)
  if (m < 0) m += 1440
  return Math.floor(m / 60) + 'h ' + String(m % 60).padStart(2, '0')
}
function fmtMin(m) {
  if (!m) return '0분'
  const h = Math.floor(m / 60), x = m % 60
  return (h ? h + '시간' : '') + (x ? ' ' + x + '분' : '')
}
const roman = (r) => (r.id || r.name || '').toUpperCase().replace(/\s/g, '_')
const SORTS  = [['rec','추천순'],['trend','트렌드순'],['time','이동 짧은순'],['fare','저렴한순']]

// 이미지 슬라이드 상태
const imgIdxMap = ref({})
const getImgIdx = (r) => imgIdxMap.value[rKey(r)] ?? 0

let _slideTimer = null
watch(regions, newList => {
  if (_slideTimer) { clearInterval(_slideTimer); _slideTimer = null }
  // 모든 키를 0으로 초기화
  const next = {}
  newList.forEach(r => { next[rKey(r)] = 0 })
  imgIdxMap.value = next
  const withImgs = newList.filter(r => r.images?.length > 1)
  if (!withImgs.length) return
  _slideTimer = setInterval(() => {
    const updated = { ...imgIdxMap.value }
    withImgs.forEach(r => {
      const k = rKey(r)
      updated[k] = ((updated[k] ?? 0) + 1) % r.images.length
    })
    imgIdxMap.value = updated
  }, 3500)
})
onUnmounted(() => {
  if (_slideTimer) clearInterval(_slideTimer)
  stopLoadingMsg()
})

// 교통수단 선택 상태
const selectedOpts = ref({})
const rKey = (r) => r.id || r.name
const getSelIdx = (r) => selectedOpts.value[rKey(r)] ?? 0
const selectOpt = (r, idx) => { selectedOpts.value[rKey(r)] = idx }
const getSelOpt = (r) => r.options?.[getSelIdx(r)] ?? null

function gapMin(a, b) {
  const [ah, am] = a.split(':').map(Number)
  const [bh, bm] = b.split(':').map(Number)
  let m = (bh * 60 + bm) - (ah * 60 + am)
  if (m < 0) m += 1440
  return m
}
const effRoundTrip = (r) => { const o = getSelOpt(r); return o ? o.oneWayMin * 2 : r.roundTrip }
const effFare      = (r) => { const o = getSelOpt(r); return o ? o.fare * 2      : r.fare }
const effStay      = (r) => r.self ? r.stay : Math.max(0, gapMin(cond.value.dep, cond.value.arr) - effRoundTrip(r))

function courseQuery(r) {
  const opt = getSelOpt(r)
  const query = {
    region: r.name,
    hub: opt?.toHub || r.name,
    from: cond.value.from,
    dep: cond.value.dep,
    arr: cond.value.arr,
  }
  if (gpsCoord.value) {
    query.startLat = gpsCoord.value.lat
    query.startLng = gpsCoord.value.lng
  }
  if (opt) {
    query.transportMode = opt.type
    query.transportFromHub = opt.fromHub
    query.transportToHub = opt.toHub
    query.transportOneWayMin = opt.oneWayMin
    query.transportFare = opt.fare
    query.transportOriginToHubMin = opt.originToHubMin
    if (opt.originToHubFare) query.transportOriginToHubFare = opt.originToHubFare
  }
  return query
}
</script>

<template>
  <div class="screen-wrap" style="max-width:1240px">
    <header class="cover" style="margin-bottom:22px">
      <div class="cover-meta">
        <span class="eyebrow">오늘의 발견 · ISSUE 011</span>
        <span class="coords">금 · 2026·06·02 · 서울 · N 37.55° E 126.94°</span>
      </div>
      <h1 class="disp" style="font-size:clamp(40px,6.5vw,78px)">
        지금, 여기서,<br>오늘 하루 안에<br>
        <span class="disp-i" style="color:var(--accent-deep)">가장 알차게.</span>
      </h1>
      <p class="lede">위치·시간만 알려주면 오늘 안에 다녀올 수 있는 지역을 찾아드려요. 왕복 교통비, 체류 가능 시간, 잔여 예산까지 미리 계산해서.</p>
    </header>

    <!-- Condition ticket stub -->
    <div class="ed-card cond-stub"
         style="overflow:hidden;display:grid;grid-template-columns:minmax(0,1fr) 230px;background:var(--card)">
      <div style="padding:22px 24px">
        <span class="lcap" style="display:block;margin-bottom:16px">오늘의 조건 · TODAY'S BRIEF</span>
        <div style="display:grid;grid-template-columns:1.3fr 1fr 1fr;gap:18px" class="field-row">
          <label style="display:block">
            <span class="lcap" style="display:block;margin-bottom:7px">현재 위치 · FROM</span>
            <div style="display:flex;align-items:center;gap:6px">
              <input v-model="cond.from" class="uline-input" />
              <button class="mono gps-btn" @click="useGps">GPS</button>
            </div>
          </label>
          <label style="display:block">
            <span class="lcap" style="display:block;margin-bottom:7px">출발 · DEP</span>
            <input type="time" v-model="cond.dep" class="uline-input" />
          </label>
          <label style="display:block">
            <span class="lcap" style="display:block;margin-bottom:7px">귀가 · ARR</span>
            <input type="time" v-model="cond.arr" class="uline-input" />
          </label>
        </div>
      </div>

      <div class="cond-stub-r"
           style="border-left:1.5px dashed var(--ink);padding:22px;background:var(--accent-wash);display:flex;flex-direction:column;justify-content:space-between">
        <div>
          <span class="lcap lcap-acc">가용 시간 · WINDOW</span>
          <div class="numark" style="font-size:44px;color:var(--accent-deep);margin:6px 0 2px">
            {{ gap(cond.dep, cond.arr) }}
          </div>
          <div style="font-size:12px;color:var(--ink-soft)">왕복 이동 포함</div>
        </div>
        <button class="btn btn-primary btn-block" style="border-radius:4px;margin-top:16px"
                :disabled="loading" @click="doSearch">
          <AppIcon name="search" style="width:16px;height:16px" />
          {{ loading ? '조회 중...' : '지역 찾기' }}
        </button>
      </div>
    </div>

    <!-- Shortlist -->
    <div style="margin-top:32px">
      <!-- 로딩 -->
      <div v-if="loading" style="text-align:center;padding:60px 0;color:var(--ink-soft)">
        <div class="mono" style="font-size:13px;letter-spacing:0.12em">SEARCHING · · ·</div>
        <div style="font-size:13.5px;margin-top:10px;font-weight:600">{{ loadingMsg }}</div>
        <div style="font-size:12px;margin-top:6px;color:var(--ink-faint)">
          첫 조회는 실시간 교통편을 확인하느라 30초까지 걸릴 수 있어요
        </div>
      </div>

      <!-- 에러 -->
      <div v-else-if="error" style="text-align:center;padding:40px;color:var(--trend)">{{ error }}</div>

      <!-- 미조회 안내 -->
      <div v-else-if="!searched" style="text-align:center;padding:60px 0;color:var(--ink-faint)">
        <div class="mono" style="font-size:13px;letter-spacing:0.1em">출발지·시간을 설정하고 지역 찾기를 눌러주세요</div>
      </div>

      <!-- 결과 없음 -->
      <div v-else-if="sorted.length === 0" style="text-align:center;padding:40px;color:var(--ink-soft)">
        가용 시간 내에 다녀올 수 있는 지역이 없어요. 귀가 시간을 늘려보세요.
      </div>

      <!-- 결과 목록 -->
      <template v-else>
        <div class="rhead">
          <div class="rt">
            <span class="n">THE SHORTLIST</span>
            <h2>당일치기 가능 지역</h2>
            <span style="font-size:13px;color:var(--ink-faint);align-self:center">{{ sorted.length }}곳 발견</span>
          </div>
          <div style="display:flex;gap:16px">
            <button v-for="[k, l] in SORTS" :key="k"
                    class="mono sort-btn" :class="{ 'sort-on': sort === k }"
                    @click="sort = k">{{ l }}</button>
          </div>
        </div>

        <div style="display:grid;grid-template-columns:repeat(auto-fill,minmax(340px,1fr));gap:18px">
          <article v-for="(r, i) in sorted" :key="r.id || r.name"
                   class="ed-card rise"
                   :style="{ overflow:'hidden', display:'flex', flexDirection:'column',
                             animationDelay: (i * 0.04) + 's',
                             outline: r.self ? '2.5px solid var(--accent-deep)' : 'none',
                             background: r.self ? 'var(--accent-wash)' : 'var(--card)' }">
            <!-- 이미지 영역 -->
            <div style="position:relative;height:188px;overflow:hidden;background:var(--card-sunken)">
              <!-- TourAPI 이미지 슬라이드 -->
              <template v-if="r.images && r.images.length">
                <img v-for="(img, ii) in r.images" :key="img"
                     :src="img" alt=""
                     class="slide-img"
                     :class="{ 'slide-active': getImgIdx(r) === ii }"
                     style="position:absolute;inset:0;width:100%;height:100%;object-fit:cover" />
              </template>
              <!-- 이미지 없으면 placeholder -->
              <ImgPh v-else :label="roman(r)" :height="150" />
              <!-- 슬라이드 인디케이터 -->
              <div v-if="r.images && r.images.length > 1"
                   style="position:absolute;bottom:6px;left:50%;transform:translateX(-50%);display:flex;gap:4px">
                <span v-for="(_, ii) in r.images" :key="ii"
                      :style="{ width:'5px', height:'5px', borderRadius:'50%',
                                background: getImgIdx(r) === ii ? '#fff' : 'rgba(255,255,255,.45)' }"/>
              </div>
              <!-- 번호 -->
              <span class="numark" style="position:absolute;top:8px;left:12px;font-size:40px;color:#fff;text-shadow:0 1px 8px rgba(0,0,0,.45);line-height:1">
                №{{ String(i + 1).padStart(2, '0') }}
              </span>
              <span v-if="r.self" class="badge" style="position:absolute;top:12px;right:12px;background:var(--accent-deep);color:#fff;font-weight:700;letter-spacing:0.04em">
                📍 내 지역
              </span>
              <span v-else-if="r.hot" class="badge badge-trend" style="position:absolute;top:12px;right:12px">
                <AppIcon name="trend" style="width:11px;height:11px" /> PICK
              </span>
            </div>

            <div style="padding:15px 17px 17px;display:flex;flex-direction:column;flex:1">
              <div style="display:flex;align-items:baseline;justify-content:space-between;gap:8px">
                <div style="display:flex;align-items:baseline;gap:7px;min-width:0">
                  <h3 class="disp" style="font-size:26px;margin:0;line-height:1.06;flex-shrink:0">{{ r.name }}</h3>
                  <span class="lcap" style="font-size:10px;color:var(--ink-faint);white-space:nowrap">
                    {{ r.sido }}
                  </span>
                </div>
                <TrendMeter :score="r.trend" />
              </div>
              <p style="font-size:13.5px;color:var(--ink-soft);margin:11px 0 13px;line-height:1.5">{{ r.blurb }}</p>

              <!-- 교통수단 옵션 (클릭으로 선택) -->
              <div v-if="r.options && r.options.length"
                   style="display:flex;flex-direction:column;gap:3px;margin-bottom:10px">
                <div v-for="(opt, oi) in r.options" :key="opt.type + '-' + opt.toHub"
                     class="opt-row"
                     :class="{ 'opt-row-on': getSelIdx(r) === oi }"
                     @click="selectOpt(r, oi)">
                  <!-- 메인 줄: 수단 | 도착지 (출발지는 보조·잘려도 무방) | 소요+요금+편수 -->
                  <div class="opt-main">
                    <span class="mono opt-type">{{ opt.type }}</span>
                    <span class="opt-route" :title="opt.toHub + ' · ' + opt.fromHub + ' 출발'"><b>{{ opt.toHub }}</b><span class="opt-from"> · {{ opt.fromHub }} 출발</span></span>
                    <span class="opt-detail">
                      편도 {{ fmtMin(opt.oneWayMin) }} · {{ won(opt.fare) }}원
                      <template v-if="opt.dailyTrips > 0"> · 하루 {{ opt.dailyTrips }}회</template>
                    </span>
                  </div>
                  <!-- 보조 줄: 편도 기준 현위치→허브 vs 지역 간 이동 분리 표시 (왕복은 하단 '왕복 이동' 항목 참고) -->
                  <div v-if="opt.originToHubMin > 0" class="opt-sub">
                    편도 = 현위치→{{ opt.fromHub }} {{ fmtMin(opt.originToHubMin) }}<template v-if="opt.originToHubFare > 0"> ({{ won(opt.originToHubFare) }}원)</template>
                    + 지역 간 이동 {{ fmtMin(opt.oneWayMin - opt.originToHubMin) }}
                  </div>
                </div>
              </div>

              <div style="display:grid;grid-template-columns:1fr 1fr 1fr;border-top:1px solid var(--ink);border-bottom:1px solid var(--line);margin-bottom:10px">
                <div class="mini-col">
                  <span class="lcap lcap-sm" style="display:block;margin-bottom:4px">왕복 이동</span>
                  <div style="font-size:13px;font-weight:700">{{ r.self ? '—' : fmtMin(effRoundTrip(r)) }}</div>
                </div>
                <div class="mini-col" style="border-left:1px solid var(--line);border-right:1px solid var(--line)">
                  <span class="lcap lcap-sm" style="display:block;margin-bottom:4px">왕복 교통</span>
                  <div class="mono" style="font-size:13px;font-weight:700">{{ r.self ? '무료' : won(effFare(r)) + '원' }}</div>
                </div>
                <div class="mini-col">
                  <span class="lcap lcap-sm" style="display:block;margin-bottom:4px">체류 가능</span>
                  <div style="font-size:13px;font-weight:700">{{ fmtMin(effStay(r)) }}</div>
                </div>
              </div>

              <div style="display:flex;gap:6px;flex-wrap:wrap;margin-bottom:14px">
                <span v-for="t in r.tags" :key="t" class="mono" style="font-size:11px;color:var(--ink-soft)">· {{ t }}</span>
              </div>

              <div style="display:flex;gap:8px;margin-top:auto">
                <button class="btn btn-outline btn-sm" style="flex:1;border-radius:4px"
                        @click="router.push({ path: '/map', query: courseQuery(r) })">장소 둘러보기</button>
                <button class="btn btn-primary btn-sm" style="flex:1;border-radius:4px"
                        @click="router.push({ path: '/course', query: courseQuery(r) })">코스 짜기 →</button>
              </div>
            </div>
          </article>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.uline-input {
  width: 100%; border: none; border-bottom: 1.5px solid var(--ink);
  background: transparent; font-size: 16px; font-weight: 600;
  padding: 4px 0; outline: none; font-family: var(--font);
}
.gps-btn {
  font-size: 10.5px; font-weight: 700; letter-spacing: 0.08em;
  color: var(--accent-deep); border: 1px solid var(--accent);
  border-radius: 4px; padding: 3px 7px;
}
.sort-btn { font-size: 12px; font-weight: 600; letter-spacing: 0.04em; color: var(--ink-faint); border-bottom: 1.5px solid transparent; padding-bottom: 2px; }
.sort-on  { color: var(--accent-deep) !important; border-bottom-color: var(--accent) !important; }
.mini-col { text-align: center; padding: 10px 6px; }
.slide-img { opacity: 0; transition: opacity 0.6s ease; }
.slide-active { opacity: 1 !important; }
.opt-row {
  display: flex; flex-direction: column; gap: 2px;
  padding: 6px 8px; border-radius: 3px;
  border: 1px solid var(--line); cursor: pointer;
  transition: all 0.12s;
}
.opt-row:hover { border-color: var(--accent); background: var(--accent-wash); }
.opt-row-on { border-color: var(--accent-deep) !important; background: var(--accent-wash) !important; }
.opt-main {
  display: grid; grid-template-columns: 56px 1fr auto;
  align-items: center; gap: 6px;
}
.opt-type { font-size: 11px; font-weight: 700; color: var(--accent-deep); letter-spacing: 0.03em; }
.opt-row-on .opt-type { color: var(--accent-deep); }
.opt-route { font-size: 11.5px; color: var(--ink); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.opt-route b { font-weight: 700; }
.opt-from { color: var(--ink-faint); font-size: 10.5px; }
/* 선택된 옵션은 말줄임 해제 — 긴 터미널명 전체 표시 */
.opt-row-on .opt-main { grid-template-columns: 56px 1fr; grid-template-rows: auto auto; }
.opt-row-on .opt-route { white-space: normal; overflow: visible; text-overflow: clip; line-height: 1.35; }
.opt-row-on .opt-detail { grid-column: 2; justify-self: start; }
.opt-detail { font-size: 11px; color: var(--ink-soft); white-space: nowrap; }
.opt-sub { font-size: 10.5px; color: var(--ink-faint); padding-left: 62px; }
</style>
