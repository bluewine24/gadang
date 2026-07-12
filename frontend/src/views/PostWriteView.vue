<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '../components/ui/AppIcon.vue'
import { getTripDetail, getTrips, createPost, uploadImage } from '../api/gadang.js'

const route  = useRoute()
const router = useRouter()

// ── 상태 ─────────────────────────────────────────────────────────────

const trip       = ref(null)
const myTrips    = ref([])
const title      = ref('')
const intro      = ref('')   // 머리말
const outro      = ref('')   // 꼬리말
const sections   = ref([])   // { placeId, placeName, text, cost, durationH, durationM, images }
const activeIdx  = ref(0)
const saving     = ref(false)
const saveError  = ref('')
const loadingImg = ref(null)

// ── 초기 로딩 ─────────────────────────────────────────────────────────

onMounted(async () => {
  const tripId = route.query.tripId ? Number(route.query.tripId) : null
  if (tripId) await loadTrip(tripId)
  else myTrips.value = (await getTrips()) ?? []
})

async function loadTrip(tripId) {
  const detail = await getTripDetail(tripId)
  if (!detail) return
  trip.value = detail
  title.value = detail.title ?? ''

  const items = detail.course?.items ?? []
  sections.value = items
    .filter(it => it.type === 'place' || it.name)
    .map(it => ({
      placeId:   it.placeId ?? it.id ?? null,
      placeName: it.name ?? it.placeName ?? '장소',
      text:      '',
      cost:      0,
      durationH: 0,
      durationM: 0,
      images:    [],
    }))
  if (sections.value.length === 0)
    sections.value = [{ placeId: null, placeName: '여행 전체', text: '', cost: 0, durationH: 1, durationM: 0, images: [] }]
}

function selectTrip(t) { loadTrip(t.tripId) }

// ── TOC ──────────────────────────────────────────────────────────────

function scrollTo(idx) {
  activeIdx.value = idx
  nextTick(() => {
    document.getElementById(`section-${idx}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}

// ── 사진 업로드 ───────────────────────────────────────────────────────

async function pickImage(idx) {
  const input = document.createElement('input')
  input.type = 'file'; input.accept = 'image/*'; input.multiple = true
  input.onchange = async (e) => {
    const files = Array.from(e.target.files ?? [])
    if (!files.length) return
    loadingImg.value = idx
    try {
      for (const file of files) {
        const preview = URL.createObjectURL(file)
        const url = await uploadImage(file)
        sections.value[idx].images.push({ url: url ?? preview, preview })
      }
    } finally { loadingImg.value = null }
  }
  input.click()
}

function removeImage(sIdx, iIdx) { sections.value[sIdx].images.splice(iIdx, 1) }

// ── 금액 / 시간 계산 ──────────────────────────────────────────────────

const totalCost = computed(() => sections.value.reduce((s, x) => s + (Number(x.cost) || 0), 0))
const totalMin  = computed(() => sections.value.reduce((s, x) =>
  s + (Number(x.durationH) || 0) * 60 + (Number(x.durationM) || 0), 0))

function formatDuration(totalMinutes) {
  const h = Math.floor(totalMinutes / 60)
  const m = totalMinutes % 60
  return h > 0 ? `${h}시간 ${m > 0 ? m + '분' : ''}` : `${m}분`
}

// ── 저장 ─────────────────────────────────────────────────────────────

async function save() {
  if (!title.value.trim()) return alert('제목을 입력해 주세요.')
  const hasContent = sections.value.some(s => s.text.trim() || s.images.length || s.cost > 0 || s.durationH > 0)
  if (!hasContent) return alert('내용을 하나 이상 입력해 주세요.')

  saving.value = true
  try {
    const places = sections.value.map(s => ({
      placeId:     s.placeId ?? null,
      placeName:   s.placeName,
      text:        s.text.trim(),
      cost:        Number(s.cost) || 0,
      durationMin: (Number(s.durationH) || 0) * 60 + (Number(s.durationM) || 0),
      images:      s.images.map(img => img.url),
    }))
    saveError.value = ''
    try {
      await createPost({
        tripId: trip.value?.tripId ?? null,
        title:  title.value.trim(),
        intro:  intro.value.trim() || null,
        outro:  outro.value.trim() || null,
        places,
      })
      router.push('/community')
    } catch (e) {
      saveError.value = e?.response?.data?.message || '저장 중 오류가 발생했습니다. 다시 시도해 주세요.'
    }
  } finally {
    saving.value = false
  }
}

// ── 헬퍼 ─────────────────────────────────────────────────────────────

const hasTrip = computed(() => !!trip.value)
const sectionsDone = computed(() =>
  sections.value.filter(s => s.text.trim() || s.images.length || s.cost > 0).length)

function won(n) { return Number(n || 0).toLocaleString('ko-KR') }
</script>

<template>
  <div class="screen-wrap" style="max-width:1200px">

    <!-- 일정 선택 -->
    <div v-if="!hasTrip && myTrips.length">
      <div class="rhead" style="margin-bottom:18px">
        <div class="rt"><span class="n">WRITE</span><h2>어떤 일정으로 쓸까요?</h2></div>
        <button class="btn btn-outline btn-sm" @click="router.push('/community')">취소</button>
      </div>
      <div style="display:grid;grid-template-columns:repeat(auto-fill,minmax(260px,1fr));gap:14px">
        <div v-for="t in myTrips" :key="t.tripId"
             class="card rise" style="padding:16px 18px;cursor:pointer" @click="selectTrip(t)">
          <div style="font-weight:700;font-size:15px;margin-bottom:6px">{{ t.title }}</div>
          <div class="mono" style="font-size:12px;color:var(--ink-faint)">{{ t.tripDate }}</div>
          <div style="margin-top:10px;font-size:12.5px;color:var(--ink-soft)">
            {{ t.region ?? '' }} · {{ (t.totalCost ?? 0).toLocaleString('ko-KR') }}원
          </div>
        </div>
      </div>
      <div class="card" style="padding:20px;text-align:center;margin-top:12px">
        <div style="font-size:13.5px;color:var(--ink-soft);margin-bottom:12px">일정 없이 자유 글 쓰기</div>
        <button class="btn btn-outline btn-sm"
                @click="sections = [{ placeId:null, placeName:'여행 후기', text:'', cost:0, durationH:0, durationM:0, images:[] }]; trip = {}">
          자유 글 작성
        </button>
      </div>
    </div>

    <!-- 일정 없을 때 -->
    <div v-else-if="!hasTrip && !myTrips.length" style="text-align:center;padding:60px 20px">
      <p style="color:var(--ink-soft);font-size:14.5px;margin-bottom:16px">저장된 일정이 없어요.</p>
      <button class="btn btn-primary btn-sm" @click="router.push('/home')">홈으로</button>
    </div>

    <!-- 에디터 -->
    <div v-else style="display:grid;grid-template-columns:220px 1fr;gap:0;align-items:start">

      <!-- TOC 사이드바 -->
      <aside style="position:sticky;top:18px;padding-right:18px;border-right:1px solid var(--line)">
        <div style="font-size:11px;font-weight:700;color:var(--ink-faint);font-family:var(--mono);letter-spacing:.06em;margin-bottom:12px">
          TABLE OF CONTENTS
        </div>

        <!-- 진행률 -->
        <div style="margin-bottom:14px">
          <div style="font-size:11.5px;color:var(--ink-soft);margin-bottom:5px">
            {{ sectionsDone }}/{{ sections.length }} 섹션
          </div>
          <div style="height:4px;background:var(--card-sunken);border-radius:2px;overflow:hidden">
            <div :style="{ width: sections.length ? (sectionsDone/sections.length*100)+'%' : '0%',
                           height:'100%', background:'var(--accent)', transition:'width .3s' }" />
          </div>
        </div>

        <!-- 합계 칩 -->
        <div style="display:flex;flex-direction:column;gap:5px;margin-bottom:14px">
          <div class="summary-chip">
            <span style="color:var(--ink-faint);font-size:11px">총 지출</span>
            <span style="font-weight:700;font-size:13px">{{ won(totalCost) }}원</span>
          </div>
          <div class="summary-chip">
            <span style="color:var(--ink-faint);font-size:11px">총 소요</span>
            <span style="font-weight:700;font-size:13px">{{ formatDuration(totalMin) }}</span>
          </div>
        </div>

        <!-- 목차 -->
        <nav style="display:flex;flex-direction:column;gap:2px;margin-bottom:14px">
          <div class="toc-item toc-special" @click="document.getElementById('intro-section')?.scrollIntoView({behavior:'smooth'})">
            머리말
          </div>
          <button v-for="(s, i) in sections" :key="i"
                  :class="['toc-item', { 'toc-active': activeIdx === i }]"
                  @click="scrollTo(i)">
            <span class="toc-num"
                  :style="{ background: (s.text.trim()||s.images.length||s.cost>0) ? 'var(--accent)' : 'transparent',
                            color: (s.text.trim()||s.images.length||s.cost>0) ? '#fff' : 'var(--ink-faint)',
                            borderColor: (s.text.trim()||s.images.length||s.cost>0) ? 'var(--accent)' : 'var(--line)' }">
              {{ i + 1 }}
            </span>
            <span style="overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:12.5px">{{ s.placeName }}</span>
          </button>
          <div class="toc-item toc-special" @click="document.getElementById('outro-section')?.scrollIntoView({behavior:'smooth'})">
            꼬리말
          </div>
        </nav>

        <div style="display:flex;flex-direction:column;gap:8px">
          <button class="btn btn-primary btn-sm" :disabled="saving" @click="save">
            {{ saving ? '저장 중...' : '발행하기' }}
          </button>
          <button class="btn btn-outline btn-sm" @click="router.push('/community')">취소</button>
        </div>
      </aside>

      <!-- 편집 영역 -->
      <main style="padding-left:28px;padding-bottom:60px">

        <!-- 제목 -->
        <input v-model="title" placeholder="제목을 입력하세요"
               style="width:100%;font-size:26px;font-weight:800;border:none;border-bottom:2px solid var(--ink);
                      padding:10px 0;margin-bottom:24px;background:transparent;color:var(--ink);outline:none;box-sizing:border-box" />

        <!-- 여행 요약 칩 -->
        <div v-if="trip?.course" style="display:flex;gap:8px;flex-wrap:wrap;margin-bottom:24px">
          <span class="badge badge-confirm">{{ trip.course.region ?? '' }}</span>
          <span class="badge" style="background:var(--card-sunken)">{{ trip.tripDate }}</span>
        </div>

        <!-- 머리말 -->
        <div id="intro-section" style="margin-bottom:32px;scroll-margin-top:18px">
          <div class="section-label">머리말</div>
          <textarea v-model="intro" placeholder="여행을 시작하기 전 한 마디... (선택)"
                    rows="3"
                    style="width:100%;border:1px solid var(--line);border-radius:10px;padding:12px 14px;
                           font-size:14.5px;line-height:1.75;resize:vertical;background:var(--bg);
                           color:var(--ink);box-sizing:border-box;font-family:inherit" />
        </div>

        <div style="height:1px;background:var(--line);margin-bottom:32px" />

        <!-- 장소별 섹션 -->
        <div v-for="(s, i) in sections" :key="i" :id="`section-${i}`"
             style="margin-bottom:44px;scroll-margin-top:18px" @click="activeIdx = i">

          <!-- 섹션 헤더 -->
          <div style="display:flex;align-items:center;gap:10px;margin-bottom:14px">
            <span style="width:26px;height:26px;border-radius:50%;background:var(--accent);color:#fff;
                         display:inline-flex;align-items:center;justify-content:center;font-size:11.5px;font-weight:800;flex-shrink:0">
              {{ i + 1 }}
            </span>
            <h2 style="font-size:18px;font-weight:800;margin:0">{{ s.placeName }}</h2>
          </div>

          <!-- 금액 + 소요 시간 -->
          <div style="display:flex;gap:12px;margin-bottom:14px;flex-wrap:wrap">
            <div class="meta-field">
              <span class="meta-label">💰 지출 금액</span>
              <div style="display:flex;align-items:center;gap:4px">
                <input v-model.number="s.cost" type="number" min="0" step="100"
                       placeholder="0"
                       style="width:120px;border:1.5px solid var(--line);border-radius:7px;padding:7px 10px;
                              font-size:14px;text-align:right;background:var(--bg);color:var(--ink)" />
                <span style="font-size:13.5px;color:var(--ink-soft)">원</span>
              </div>
              <div v-if="s.cost > 0" style="font-size:11px;color:var(--ink-faint);margin-top:3px">
                {{ won(s.cost) }}원
              </div>
            </div>

            <div class="meta-field">
              <span class="meta-label">⏱ 소요 시간</span>
              <div style="display:flex;align-items:center;gap:6px">
                <input v-model.number="s.durationH" type="number" min="0" max="24"
                       placeholder="0"
                       style="width:60px;border:1.5px solid var(--line);border-radius:7px;padding:7px 8px;
                              font-size:14px;text-align:center;background:var(--bg);color:var(--ink)" />
                <span style="font-size:13px;color:var(--ink-soft)">시간</span>
                <input v-model.number="s.durationM" type="number" min="0" max="59" step="5"
                       placeholder="0"
                       style="width:60px;border:1.5px solid var(--line);border-radius:7px;padding:7px 8px;
                              font-size:14px;text-align:center;background:var(--bg);color:var(--ink)" />
                <span style="font-size:13px;color:var(--ink-soft)">분</span>
              </div>
            </div>
          </div>

          <!-- 사진 그리드 -->
          <div v-if="s.images.length"
               style="display:grid;grid-template-columns:repeat(auto-fill,minmax(160px,1fr));gap:8px;margin-bottom:12px">
            <div v-for="(img, ii) in s.images" :key="ii"
                 style="position:relative;aspect-ratio:4/3;overflow:hidden;border-radius:8px;background:var(--card-sunken)">
              <img :src="img.preview ?? img.url" :alt="`${s.placeName} 사진`"
                   style="width:100%;height:100%;object-fit:cover" />
              <button @click.stop="removeImage(i, ii)"
                      style="position:absolute;top:5px;right:5px;width:22px;height:22px;border-radius:50%;
                             background:rgba(0,0,0,.55);color:#fff;font-size:13px;display:flex;align-items:center;justify-content:center">×</button>
            </div>
            <button @click.stop="pickImage(i)"
                    style="aspect-ratio:4/3;border:1.5px dashed var(--line);border-radius:8px;
                           display:flex;flex-direction:column;align-items:center;justify-content:center;
                           gap:4px;color:var(--ink-faint);font-size:12px;background:transparent;cursor:pointer">
              <AppIcon name="plus" style="width:18px;height:18px" /> 사진 추가
            </button>
          </div>
          <button v-else @click.stop="pickImage(i)"
                  style="width:100%;height:90px;border:1.5px dashed var(--line);border-radius:10px;
                         display:flex;align-items:center;justify-content:center;gap:10px;
                         color:var(--ink-faint);font-size:13.5px;background:transparent;cursor:pointer;margin-bottom:12px">
            <AppIcon name="plus" style="width:18px;height:18px" />
            {{ loadingImg === i ? '업로드 중...' : '사진 추가' }}
          </button>

          <!-- 텍스트 -->
          <textarea v-model="s.text" :placeholder="`${s.placeName}에서의 이야기를 적어보세요...`" rows="4"
                    style="width:100%;border:1px solid var(--line);border-radius:10px;padding:12px 14px;
                           font-size:14.5px;line-height:1.75;resize:vertical;background:var(--bg);
                           color:var(--ink);box-sizing:border-box;font-family:inherit" />

          <div style="height:1px;background:var(--line);margin-top:30px" />
        </div>

        <!-- 꼬리말 -->
        <div id="outro-section" style="margin-bottom:44px;scroll-margin-top:18px">
          <div class="section-label">꼬리말</div>
          <textarea v-model="outro" placeholder="여행을 마치며 한 마디... (선택)" rows="3"
                    style="width:100%;border:1px solid var(--line);border-radius:10px;padding:12px 14px;
                           font-size:14.5px;line-height:1.75;resize:vertical;background:var(--bg);
                           color:var(--ink);box-sizing:border-box;font-family:inherit" />
        </div>

        <!-- 하단 저장 -->
        <div v-if="saveError" style="padding:12px 14px;background:var(--trend-wash);color:var(--trend);
                                      border-radius:8px;font-size:13.5px;margin-bottom:10px">
          {{ saveError }}
        </div>
        <div style="display:flex;gap:10px;margin-top:8px">
          <button class="btn btn-primary" :disabled="saving" @click="save"
                  style="padding:12px 28px;font-size:15px">
            {{ saving ? '저장 중...' : '발행하기' }}
          </button>
          <button class="btn btn-outline" @click="router.push('/community')"
                  style="padding:12px 20px">취소</button>
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped>
.toc-item {
  display: flex; align-items: center; gap: 8px;
  padding: 7px 10px; border-radius: 7px;
  font-size: 12.5px; text-align: left; width: 100%;
  color: var(--ink-soft); background: transparent; transition: background .15s;
}
.toc-item:hover { background: var(--card-sunken); }
.toc-active     { background: var(--accent-wash) !important; color: var(--accent-deep); font-weight: 600; }
.toc-special    { color: var(--ink-faint); font-size: 11.5px; cursor: pointer; font-family: var(--mono); }
.toc-num {
  width: 18px; height: 18px; border-radius: 50%;
  display: inline-flex; align-items: center; justify-content: center;
  font-size: 10px; font-weight: 700; flex-shrink: 0; border: 1px solid;
}
.summary-chip {
  display: flex; justify-content: space-between; align-items: center;
  padding: 7px 10px; border-radius: 7px; background: var(--card-sunken);
}
.meta-field { display: flex; flex-direction: column; gap: 6px; }
.meta-label { font-size: 11.5px; font-weight: 600; color: var(--ink-faint); }
.section-label {
  font-size: 11.5px; font-weight: 700; color: var(--ink-faint);
  font-family: var(--mono); letter-spacing: .06em; margin-bottom: 8px;
  text-transform: uppercase;
}
</style>
