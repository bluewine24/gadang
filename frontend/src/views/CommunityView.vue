<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import AppIcon from '../components/ui/AppIcon.vue'
import ImgPh   from '../components/ui/ImgPh.vue'
import { won, COMMUNITY as MOCK_COMMUNITY, NOTICES as MOCK_NOTICES } from '../data/mock.js'
import { getCommunity, getNotices } from '../api/gadang.js'

const router    = useRouter()
const route     = useRoute()
const region    = ref(route.query.region || '전체')
const budget    = ref('all')
const saves     = ref([])
const community = ref([])
const notices   = ref(MOCK_NOTICES)
const loading   = ref(true)

onMounted(async () => {
  loading.value = true
  try {
    const [c, n] = await Promise.all([getCommunity(), getNotices()])
    community.value = Array.isArray(c) ? c : []
    if (n?.length) notices.value = n
  } finally {
    loading.value = false
  }
})

// 실제 게시글의 지역 목록 + 전체
const REGIONS = computed(() => {
  const set = new Set(community.value.map(c => c.region).filter(Boolean))
  return ['전체', ...Array.from(set).sort()]
})

const BUDGETS = [
  { k:'all', l:'예산 전체', max: Infinity },
  { k:'b1',  l:'~1만원',   max: 10000 },
  { k:'b2',  l:'~3만원',   max: 30000 },
  { k:'b3',  l:'~5만원',   max: 50000 },
  { k:'b4',  l:'5만원+',   max: Infinity, min: 50000 },
]

const list = computed(() => {
  const b = BUDGETS.find(x => x.k === budget.value)
  return community.value.filter(c =>
    (region.value === '전체' || c.region === region.value) &&
    c.cost <= b.max && (!b.min || c.cost > b.min))
})

function toggleSave(id, e) {
  e.stopPropagation()
  saves.value = saves.value.includes(id)
    ? saves.value.filter(x => x !== id)
    : [...saves.value, id]
}

function openPost(postId) {
  if (postId) router.push(`/community/${postId}`)
}

function noticeBg(tag) {
  return tag === '점검' ? 'var(--trend-wash)' : tag === '이벤트' ? 'var(--free-wash)' : 'var(--accent-wash)'
}
function noticeColor(tag) {
  return tag === '점검' ? 'var(--trend)' : tag === '이벤트' ? 'var(--free)' : 'var(--accent-deep)'
}
</script>

<template>
  <div class="screen-wrap" style="max-width:1240px">
    <header class="cover" style="margin-bottom:22px">
      <div class="cover-meta">
        <span class="eyebrow">COMMUNITY · 코스 라이브러리 · VOL.03</span>
        <span class="coords">2026 · 06 · 02</span>
      </div>
      <div style="display:flex;align-items:flex-end;justify-content:space-between;gap:24px;flex-wrap:wrap">
        <h1 class="disp" style="font-size:clamp(34px,5vw,60px)">
          다른 사람의<br><span class="disp-i">가성비 하루.</span>
        </h1>
        <button class="btn btn-primary btn-sm" style="border-radius:4px" @click="router.push('/community/write')">
          <AppIcon name="plus" style="width:15px;height:15px" /> 내 코스 공유하기
        </button>
      </div>
      <p class="lede">실제로 다녀온 사람들의 영수증 첨부 일기. 마음에 드는 코스는 한 번의 클릭으로 내 일정으로 복사할 수 있어요.</p>
    </header>

    <div style="display:grid;grid-template-columns:minmax(0,1fr) 280px;gap:22px;align-items:start" class="map-grid">
      <div>
        <!-- Filters -->
        <div style="display:flex;flex-direction:column;gap:9px;margin-bottom:18px">
          <div style="display:flex;gap:6px;overflow-x:auto;padding-bottom:2px">
            <button v-for="r in REGIONS" :key="r" class="chip" :class="{ 'on accent': region === r }"
                    style="flex-shrink:0" @click="region = r">{{ r }}</button>
          </div>
          <div style="display:flex;gap:6px;overflow-x:auto">
            <button v-for="b in BUDGETS" :key="b.k" class="chip" :class="{ on: budget === b.k }"
                    style="flex-shrink:0" @click="budget = b.k">{{ b.l }}</button>
          </div>
        </div>

        <!-- Cards -->
        <div style="display:grid;grid-template-columns:repeat(auto-fill,minmax(250px,1fr));gap:16px">
          <div v-for="(c, i) in list" :key="c.id"
               class="card rise"
               style="cursor:pointer"
               :style="{ overflow:'hidden', display:'flex', flexDirection:'column', animationDelay: (i * 0.04) + 's' }"
               @click="openPost(c.postId ?? c.id)">
            <div style="position:relative">
              <ImgPh :label="c.region + ' 코스 썸네일'" :height="124" />
              <span v-if="c.hot" class="badge badge-trend" style="position:absolute;top:10px;left:10px">
                <AppIcon name="trend" style="width:11px;height:11px" /> 인기
              </span>
              <span class="badge badge-confirm" style="position:absolute;top:10px;right:10px">
                <AppIcon name="pin" style="width:11px;height:11px" /> {{ c.region }}
              </span>
              <!-- receipt stub overlay -->
              <div style="position:absolute;bottom:10px;left:10px;right:10px;display:flex;gap:6px">
                <span class="stub"><b class="mono">{{ won(c.cost) }}</b>원</span>
                <span class="stub">{{ Math.floor(c.min/60) }}h{{ c.min % 60 }}m</span>
                <span class="stub">{{ c.places }}곳</span>
              </div>
            </div>
            <div style="padding:13px 15px 15px;display:flex;flex-direction:column;flex:1">
              <div style="font-size:15px;font-weight:700;line-height:1.35">{{ c.title }}</div>
              <div style="display:flex;gap:5px;flex-wrap:wrap;margin:9px 0 11px">
                <span v-for="t in c.tags" :key="t"
                      style="font-size:11px;color:var(--ink-soft);background:var(--card-sunken);padding:3px 8px;border-radius:999px">#{{ t }}</span>
              </div>
              <div style="display:flex;align-items:center;gap:12px;font-size:12px;color:var(--ink-faint);margin-top:auto;padding-top:11px;border-top:1px solid var(--line)">
                <span style="display:inline-flex;align-items:center;gap:4px">
                  <span class="avatar" style="width:22px;height:22px;font-size:10px">{{ c.author[0] }}</span>
                  {{ c.author }}
                </span>
                <span style="margin-left:auto;display:inline-flex;gap:10px">
                  <span style="display:inline-flex;align-items:center;gap:3px"><AppIcon name="heart" style="width:13px;height:13px" />{{ c.likes }}</span>
                  <span style="display:inline-flex;align-items:center;gap:3px"><AppIcon name="comment" style="width:13px;height:13px" />{{ c.comments }}</span>
                </span>
              </div>
              <div style="display:flex;gap:7px;margin-top:12px">
                <button @click="toggleSave(c.id ?? c.postId, $event)" class="btn btn-outline btn-sm"
                        :style="{ color: saves.includes(c.id ?? c.postId) ? 'var(--trend)' : 'var(--ink-soft)', padding:'7px 11px' }">
                  <AppIcon name="bookmark" style="width:15px;height:15px" :style="{ fill: saves.includes(c.id ?? c.postId) ? 'var(--trend)' : 'none' }" />
                </button>
                <button class="btn btn-primary btn-sm" style="flex:1"
                        @click.stop="openPost(c.postId ?? c.id)">
                  <AppIcon name="swap" style="width:15px;height:15px" /> 내 일정으로 가져오기
                </button>
              </div>
            </div>
          </div>
        </div>
        <div v-if="list.length === 0" class="card" style="padding:40px;text-align:center;color:var(--ink-faint);font-size:13.5px">
          조건에 맞는 공유 코스가 없어요. 필터를 바꿔보세요.
        </div>
      </div>

      <!-- Right: notices -->
      <div style="position:sticky;top:18px;display:flex;flex-direction:column;gap:14px">
        <div class="card" style="padding:16px 18px">
          <div style="display:flex;align-items:center;gap:7px;margin-bottom:13px">
            <AppIcon name="bell" style="width:16px;height:16px;color:var(--accent-deep)" />
            <span style="font-weight:700;font-size:14.5px">공지사항</span>
          </div>
          <div style="display:flex;flex-direction:column;gap:12px">
            <div v-for="n in notices" :key="n.id" style="display:flex;gap:9px">
              <span class="badge" style="flex-shrink:0;height:fit-content"
                    :style="{ background: noticeBg(n.tag), color: noticeColor(n.tag) }">{{ n.tag }}</span>
              <div>
                <div style="font-size:13px;line-height:1.4;font-weight:500">{{ n.title }}</div>
                <div class="mono" style="font-size:11px;color:var(--ink-faint);margin-top:3px">{{ n.date }}</div>
              </div>
            </div>
          </div>
        </div>

        <div class="card" style="padding:16px 18px;background:var(--accent-wash);border:none">
          <div style="font-size:13.5px;font-weight:700;color:var(--accent-deep)">가져오면 자동 재계산</div>
          <p style="font-size:12px;color:var(--ink-soft);margin:7px 0 0;line-height:1.5">
            공유 코스를 가져오면 내 출발지·시간 기준으로 교통 경로·비용이 다시 계산돼요.
          </p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.stub {
  font-size: 11px; background: var(--card); color: var(--ink);
  padding: 3px 8px; border-radius: 7px; font-weight: 600;
  box-shadow: var(--shadow-sm); white-space: nowrap;
}
</style>
