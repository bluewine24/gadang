<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import AppIcon        from '../components/ui/AppIcon.vue'
import CourseTimeline from '../components/trip/CourseTimeline.vue'
import { won } from '../data/mock.js'
import { getTrips, getTripDetail } from '../api/gadang.js'
import { useAuth } from '../composables/useAuth.js'

const router = useRouter()
const route  = useRoute()
const { isLoggedIn } = useAuth()

const trips         = ref([])
const loading       = ref(true)
const detail        = ref(null)    // 선택한 일정 상세(코스 포함)
const detailLoading = ref(false)
const searchQ       = ref('')

const filteredTrips = computed(() => {
  const q = searchQ.value.trim().toLowerCase()
  if (!q) return trips.value
  return trips.value.filter(t => t.title?.toLowerCase().includes(q))
})

onMounted(async () => {
  trips.value = await getTrips()   // 비로그인 시 빈 배열
  loading.value = false

  // URL에 ID가 있으면 (/trip/123) 해당 일정 바로 오픈
  const paramId = route.params.id
  if (paramId) {
    const target = trips.value.find(t => String(t.tripId) === String(paramId))
    if (target) openTrip(target)
    else {
      // 목록에 없으면 API 직접 조회 (다른 사용자의 일정 등 예외 방어)
      const d = await getTripDetail(Number(paramId))
      if (d) { detail.value = d }
    }
  }
})

async function openTrip(t) {
  detailLoading.value = true
  detail.value = await getTripDetail(t.tripId)
  detailLoading.value = false
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function backToList() {
  detail.value = null
}

function fmtDate(d) {
  if (!d) return ''
  return String(d).slice(0, 10).replace(/-/g, '.')
}
</script>

<template>
  <div class="screen-wrap" style="max-width:1240px">
    <!-- ── 상세 (확정 일정 타임라인) ───────────────────────── -->
    <div v-if="detail">
      <button @click="backToList" class="back-btn mono">
        <AppIcon name="chevL" style="width:14px;height:14px" /> 일정 목록으로
      </button>
      <header class="cover" style="margin-bottom:22px">
        <div class="cover-meta">
          <span class="eyebrow">확정 일정 · CONFIRMED</span>
          <span class="coords">{{ fmtDate(detail.tripDate) }}</span>
        </div>
        <h1 class="disp" style="font-size:clamp(30px,4.5vw,52px)">
          {{ detail.title }}
        </h1>
        <p class="lede">{{ detail.startPoint }} 출발 · {{ detail.departureTime?.slice(0,5) }}~{{ detail.returnTime?.slice(0,5) }}</p>
      </header>

      <CourseTimeline v-if="detail.course" :course="detail.course" :no="String(detail.tripId)" />
      <p v-else style="color:var(--ink-faint)">저장된 코스 정보가 없습니다.</p>
    </div>

    <!-- ── 목록 (확정 일정들) ──────────────────────────────── -->
    <div v-else>
      <header class="cover" style="margin-bottom:22px">
        <div class="cover-meta">
          <span class="eyebrow">내 일정 · ITINERARY</span>
          <span class="coords">확정한 당일치기 일정</span>
        </div>
        <h1 class="disp" style="font-size:clamp(34px,5vw,60px)">
          확정한 <span class="disp-i">하루들.</span>
        </h1>
        <p class="lede">코스 탭에서 추천받은 코스를 확정하면 여기에 모여요.</p>
      </header>

      <!-- 비로그인 -->
      <div v-if="!isLoggedIn" class="empty-card">
        <AppIcon name="user" style="width:30px;height:30px;color:var(--ink-faint)" />
        <p>로그인하면 확정한 일정을 모아볼 수 있어요.</p>
        <button class="btn btn-primary" @click="router.push('/login')">로그인 하러가기</button>
      </div>

      <!-- 로딩 -->
      <div v-else-if="loading" class="empty-card"><p>불러오는 중…</p></div>

      <!-- 빈 목록 -->
      <div v-else-if="!trips.length" class="empty-card">
        <AppIcon name="route" style="width:30px;height:30px;color:var(--ink-faint)" />
        <p>아직 확정한 일정이 없어요.<br>코스 탭에서 추천받고 확정해 보세요.</p>
        <button class="btn btn-primary" @click="router.push('/course')">
          <AppIcon name="route" style="width:17px;height:17px" /> 코스 추천 받으러가기
        </button>
      </div>

      <!-- 목록 -->
      <div v-else>
        <!-- 검색 필터 -->
        <div style="position:relative;margin-bottom:14px">
          <AppIcon name="search" style="position:absolute;left:12px;top:11px;width:16px;height:16px;color:var(--ink-faint)" />
          <input v-model="searchQ" placeholder="일정 제목으로 검색"
                 style="width:100%;box-sizing:border-box;padding:10px 12px 10px 36px;border-radius:11px;border:1px solid var(--line-strong);background:var(--card);font-size:14px;outline:none" />
        </div>
        <div v-if="!filteredTrips.length" style="text-align:center;padding:24px;color:var(--ink-faint);font-size:13px">
          검색 결과가 없습니다.
        </div>
        <div class="trip-list">
        <button v-for="t in filteredTrips" :key="t.tripId" class="trip-card" @click="openTrip(t)">
          <div style="flex:1;min-width:0">
            <div style="display:flex;align-items:center;gap:8px;margin-bottom:6px">
              <span class="badge badge-accent">{{ fmtDate(t.tripDate) }}</span>
              <span style="font-size:16px;font-weight:700;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ t.title }}</span>
            </div>
            <div class="mono" style="font-size:12.5px;color:var(--ink-faint)">
              {{ t.startPoint }} · {{ t.departureTime?.slice(0,5) }}~{{ t.returnTime?.slice(0,5) }}
            </div>
          </div>
          <div style="text-align:right;flex-shrink:0">
            <div class="mono" style="font-size:17px;font-weight:700;color:var(--accent-deep)">{{ won(t.totalCost) }}원</div>
            <div style="font-size:11px;color:var(--ink-faint)">+ 식비 {{ won(t.foodCostEst) }}원</div>
          </div>
          <AppIcon name="chevR" style="width:18px;height:18px;color:var(--ink-faint);flex-shrink:0" />
        </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.back-btn {
  display: inline-flex; align-items: center; gap: 4px;
  font-size: 12px; color: var(--ink-faint); font-weight: 600;
  letter-spacing: 0.04em; margin-bottom: 14px; cursor: pointer;
}
.empty-card {
  display: flex; flex-direction: column; align-items: center; gap: 14px;
  padding: 56px 24px; text-align: center;
  border: 1px dashed var(--line); border-radius: var(--radius-lg, 16px);
  color: var(--ink-soft); background: var(--card);
}
.empty-card p { line-height: 1.6; font-size: 14px; }
.trip-list { display: flex; flex-direction: column; gap: 12px; }
.trip-card {
  display: flex; align-items: center; gap: 16px;
  padding: 18px 20px; text-align: left; cursor: pointer;
  background: var(--paper, #fff); border: 1px solid var(--line);
  border-radius: var(--radius-lg, 16px); transition: border-color .12s, transform .12s, box-shadow .12s;
}
.trip-card:hover {
  border-color: var(--accent); transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
}
</style>
