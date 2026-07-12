<script setup>
import { computed, ref } from 'vue'
import AppIcon          from '../ui/AppIcon.vue'
import Money            from '../ui/Money.vue'
import FeeBadge         from '../ui/FeeBadge.vue'
import SegBar           from '../ui/SegBar.vue'
import EditorialReceipt from '../ui/EditorialReceipt.vue'
import { won, CATS, CAT_HUE } from '../../data/mock.js'

// course: 백엔드 CourseResponse 형태 (코스 추천 결과 / 확정 일정 상세 공용)
const props = defineProps({
  course: { type: Object, required: true },
  no:     { type: String, default: '0042' },
  returnLimit: { type: String, default: '' },
  editable: { type: Boolean, default: false },
  editEntries: { type: Array, default: () => [] },
  dirty: { type: Boolean, default: false },
  regenerating: { type: Boolean, default: false },
  activities: { type: Array, default: () => [] },
  foodKinds: { type: Array, default: () => [] },
})

const emit = defineEmits([
  'remove-entry',
  'update-entry',
  'add-activity',
  'add-cafe',
  'add-meal',
  'add-specific',
  'regenerate',
])

const openEditIndex = ref(null)

const items  = computed(() => props.course?.items ?? [])
const places = computed(() => items.value.filter((x) => x.type === 'place'))
const originalPlaceNames = computed(() => new Set(places.value.map((place) => place.name)))
const visiblePlaceNames = computed(() => new Set(props.editEntries
  .filter((entry) => ['LOCKED_PLACE', 'SPECIFIC_PLACE'].includes(entry.type))
  .map((entry) => entry.placeName)))
const addedEntryBuckets = computed(() => {
  const buckets = Array.from({ length: places.value.length + 1 }, () => [])
  props.editEntries.forEach((entry, entryIndex) => {
    if (isOriginalEntry(entry)) return
    const nextOriginal = props.editEntries.slice(entryIndex + 1).find(isOriginalEntry)
    const bucketIndex = nextOriginal
      ? places.value.findIndex((place) => place.name === nextOriginal.placeName)
      : places.value.length
    buckets[Math.max(0, bucketIndex)].push({ entry, entryIndex })
  })
  return buckets
})

const sum = (arr) => arr.reduce((s, x) => s + (Number(x) || 0), 0)
const transport = computed(() => sum(items.value.filter((x) => x.type === 'transit').map((x) => x.fare)))
const admission = computed(() => sum(places.value.filter((p) => !['food', 'cafe'].includes(p.cat)).map((p) => p.fee)))
const food      = computed(() => sum(places.value.filter((p) => p.cat === 'food').map((p) => p.fee)))
const cafe      = computed(() => sum(places.value.filter((p) => p.cat === 'cafe').map((p) => p.fee)))
const total     = computed(() => props.course?.totalCost ?? (transport.value + admission.value + food.value + cafe.value))
const totalMin  = computed(() => props.course?.totalMin
  ?? sum(items.value.filter((x) => x.type === 'transit').map((x) => x.min)) + sum(places.value.map((p) => p.stay)))

const receiptGroups = computed(() => {
  const transitItems = items.value.filter((x) => x.type === 'transit').map((x) => ({
    name: `${x.mode} · ${x.from} → ${x.to}`, sub: `${x.min}분`, amount: x.fare, type: 'confirm',
  }))
  const foodItems = places.value.filter((p) => ['food', 'cafe'].includes(p.cat)).map((p) => ({
    name: p.name, sub: `${CATS[p.cat]?.ko ?? p.cat} · 1인 추정`, amount: p.fee, type: p.feeType,
  }))
  const ticketItems = places.value.filter((p) => !['food', 'cafe'].includes(p.cat)).map((p) => ({
    name: p.name, sub: p.fee === 0 ? '무료 · 확정' : '입장료', amount: p.fee, type: p.feeType,
  }))
  return [
    { label: '교통 · TRANSIT',   items: transitItems, subtotal: transport.value },
    { label: '식사 · FOOD',      items: foodItems,    subtotal: food.value + cafe.value },
    { label: '입장료 · TICKETS', items: ticketItems,  subtotal: admission.value },
  ].filter((g) => g.items.length)
})

const guide = computed(() => props.course?.budgetGuide ?? null)

function modeIcon(m) {
  return ['KTX', 'ITX', '무궁화'].includes(m) ? 'train' : m === '버스' ? 'bus' : 'walk'
}

function minutes(time) {
  const [h, m] = String(time || '').split(':').map(Number)
  if (!Number.isFinite(h) || !Number.isFinite(m)) return null
  return h * 60 + m
}

function timelineStartMinutes(item) {
  if (item.type === 'place') return minutes(item.arr || item.dep)
  return minutes(item.dep || item.arr)
}

function timelineEndMinutes(item) {
  if (item.type === 'place') {
    const departure = minutes(item.dep)
    if (departure != null) return departure
    const arrival = minutes(item.arr)
    return arrival == null ? null : arrival + (Number(item.stay) || 0)
  }
  return minutes(item.arr || item.dep)
}

function timeLabel(totalMinutes) {
  const minutesInDay = ((totalMinutes % 1440) + 1440) % 1440
  const h = Math.floor(minutesInDay / 60)
  const m = minutesInDay % 60
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`
}

function freeTimeLabel(totalMinutes) {
  const min = Math.max(0, Number(totalMinutes) || 0)
  const h = Math.floor(min / 60)
  const m = min % 60
  if (h > 0 && m > 0) return `${h}시간 ${m}분`
  if (h > 0) return `${h}시간`
  return `${m}분`
}

function pushFreeTime(out, key, start, end) {
  if (start == null || end == null || end <= start + 5) return
  const min = end - start
  out.push({
    type: 'free',
    key: `free-${key}-${start}-${end}`,
    start: timeLabel(start),
    end: timeLabel(end),
    min,
    label: freeTimeLabel(min),
  })
}

const timelineEntries = computed(() => {
  const out = []
  let previousEnd = null

  items.value.forEach((item, index) => {
    const start = timelineStartMinutes(item)
    pushFreeTime(out, index, previousEnd, start)

    out.push(item)
    const end = timelineEndMinutes(item)
    if (end != null) previousEnd = end
  })

  const timelineLimit = minutes(props.returnLimit || props.course?.endTime)
  pushFreeTime(out, 'end', previousEnd, timelineLimit)

  return out
})

function isOverReturnLimit(item) {
  const limit = minutes(props.returnLimit)
  const end = timelineEndMinutes(item)
  return limit != null && end != null && end > limit
}

function crossesReturnLimit(item) {
  const limit = minutes(props.returnLimit)
  const start = timelineStartMinutes(item)
  const end = timelineEndMinutes(item)
  return limit != null && start != null && end != null && start <= limit && end > limit
}

function lineLimitClass(item) {
  return {
    'timeline-limit-line': isOverReturnLimit(item),
    'timeline-limit-cross': crossesReturnLimit(item),
  }
}

const actualEndMinutes = computed(() => {
  const timelineItems = items.value
  for (let i = timelineItems.length - 1; i >= 0; i--) {
    const end = timelineEndMinutes(timelineItems[i])
    if (end != null) return end
  }
  return minutes(props.course?.endTime)
})

const actualEndLabel = computed(() => actualEndMinutes.value == null ? props.course?.endTime : timeLabel(actualEndMinutes.value))

const isActualEndOverLimit = computed(() => {
  const limit = minutes(props.returnLimit)
  return limit != null && actualEndMinutes.value != null && actualEndMinutes.value > limit
})

function placeIndex(item) {
  return props.editable && props.dirty
    ? props.editEntries.findIndex((entry) =>
        ['LOCKED_PLACE', 'SPECIFIC_PLACE'].includes(entry.type) && entry.placeName === item.name)
    : places.value.indexOf(item)
}

function isDeletedPlace(item) {
  return props.editable && props.dirty && !visiblePlaceNames.value.has(item.name)
}

function isOriginalEntry(entry) {
  return ['LOCKED_PLACE', 'SPECIFIC_PLACE'].includes(entry?.type) && originalPlaceNames.value.has(entry.placeName)
}

function additionsBefore(placeIndex) {
  return props.editable && props.dirty ? addedEntryBuckets.value[placeIndex] || [] : []
}

function toggleEdit(index) {
  openEditIndex.value = openEditIndex.value === index ? null : index
}

function insertActivity(index, activityKey) {
  emit('add-activity', { index, activityKey })
}

function insertCafe(index) {
  emit('add-cafe', { index })
}

function insertMeal(index, type) {
  emit('add-meal', { index, type })
}

function insertSpecific(index) {
  emit('add-specific', { index })
}

function updateEntry(index, patch) {
  emit('update-entry', { index, patch })
}

function mealSlotTypeForItem(item) {
  if (item?.role === 'LUNCH') return 'LUNCH'
  if (item?.role === 'DINNER') return 'DINNER'
  return null
}

function editEntryAt(index) {
  return props.editEntries[index] || {}
}

function mealKindAt(index) {
  return editEntryAt(index).foodKind || props.foodKinds[0]
}

function replaceMealEntry(index, type, patch) {
  updateEntry(index, { type, ...patch })
}

function activityKeyForCategory(cat) {
  if (['culture', 'park', 'photo', 'shop', 'sight'].includes(cat)) return cat
  return 'sight'
}

function replaceWithSameCategory(index, item) {
  const mealType = mealSlotTypeForItem(item)
  if (mealType) {
    replaceMealEntry(index, mealType, { foodKind: mealKindAt(index) })
    return
  }
  if (item?.cat === 'food') {
    updateEntry(index, { type: 'LUNCH', foodKind: mealKindAt(index) })
    return
  }
  if (item?.cat === 'cafe') {
    updateEntry(index, { type: 'CAFE_SLOT' })
    return
  }
  updateEntry(index, { type: 'ACTIVITY_SLOT', activityKey: activityKeyForCategory(item?.cat) })
}

function removeEntry(index) {
  emit('remove-entry', { index })
  openEditIndex.value = null
}

function eventTitle(entry) {
  if (entry.type === 'LOCKED_PLACE' || entry.type === 'SPECIFIC_PLACE') return entry.placeName
  if (entry.type === 'ACTIVITY_SLOT') return props.activities.find((a) => a.k === entry.activityKey)?.l || '활동'
  if (entry.type === 'CAFE_SLOT') return '카페'
  if (entry.type === 'LUNCH') return '점심'
  if (entry.type === 'DINNER') return '저녁'
  return entry.name || '장소'
}

function eventMeta(entry) {
  if (entry.type === 'LOCKED_PLACE') return `고정 장소 · ${entry.stayMinutes || 60}분`
  if (entry.type === 'SPECIFIC_PLACE') return '직접 추가한 장소'
  if (entry.type === 'ACTIVITY_SLOT') return '재생성 시 맞는 장소를 찾습니다'
  if (entry.type === 'CAFE_SLOT') return '카페 · 45분'
  if (entry.type === 'LUNCH') return '점심 · 60분'
  if (entry.type === 'DINNER') return '저녁 · 60분'
  if (entry.note) return `${entry.note} · 체류 ${entry.stay}분`
  return `체류 ${entry.stay || 60}분`
}

function entryCat(entry) {
  if (entry.type === 'CAFE_SLOT') return 'cafe'
  if (entry.type === 'LUNCH' || entry.type === 'DINNER') return 'food'
  if (entry.type === 'SPECIFIC_PLACE') return entry.cat || 'sight'
  return 'sight'
}

</script>

<template>
  <div style="display:grid;grid-template-columns:minmax(0,1.6fr) minmax(0,1fr);gap:22px;align-items:start" class="trip-grid">
    <!-- Timeline -->
    <div>
      <div class="rhead">
        <div class="rt">
          <span class="n">TIMELINE</span>
          <h2>타임라인</h2>
          <span style="font-size:13px;color:var(--ink-faint);align-self:center">
            {{ places.length }}곳 · 총 {{ Math.floor(totalMin / 60) }}시간 {{ totalMin % 60 }}분
          </span>
        </div>
        <button
          v-if="editable"
          class="timeline-regen btn btn-primary btn-sm"
          :disabled="regenerating || editEntries.length === 0"
          @click="emit('regenerate')"
        >
          <AppIcon name="route" style="width:15px;height:15px" />
          {{ regenerating ? '재생성 중...' : '편집 내용으로 재생성' }}
        </button>
      </div>

      <div v-if="editable && dirty" class="timeline-dirty-note">
        동선/시간은 재생성 시 다시 계산됩니다.
      </div>

      <div>
        <template v-for="(it, idx) in timelineEntries" :key="it.key || idx">
          <!-- free time -->
          <div
            v-if="it.type === 'free'"
            class="timeline-row timeline-free"
            style="display:flex;gap:14px;align-items:stretch"
          >
            <div style="width:44px;display:flex;flex-direction:column;align-items:center">
              <span class="timeline-stem timeline-stem-dotted" />
            </div>
            <div style="padding:6px 0;flex:1">
              <div class="timeline-free-pill">
                <AppIcon name="clock" style="width:15px;height:15px" />
                자유 시간 {{ it.start }}-{{ it.end }}
                <span class="mono">{{ it.label }}</span>
              </div>
            </div>
          </div>

          <!-- transit -->
          <div
            v-if="it.type === 'transit'"
            class="timeline-row"
            :class="{ 'timeline-over-limit': isOverReturnLimit(it) }"
            style="display:flex;gap:14px;align-items:stretch"
          >
            <div style="width:44px;display:flex;flex-direction:column;align-items:center">
              <span class="timeline-stem timeline-stem-dotted" :class="lineLimitClass(it)" />
            </div>
            <div style="padding:8px 0;flex:1">
              <div class="timeline-transit-pill" style="display:inline-flex;align-items:center;gap:9px;padding:8px 13px;border-radius:10px;background:var(--transit-wash);color:var(--transit);font-size:12.5px;font-weight:600">
                <AppIcon :name="modeIcon(it.mode)" style="width:16px;height:16px" />
                {{ it.mode }} · {{ it.min }}분
                <span style="color:var(--ink-soft);font-weight:500">{{ it.from }} → {{ it.to }}</span>
                <span class="mono" style="font-weight:700">{{ won(it.fare) }}원</span>
              </div>
            </div>
          </div>

          <!-- place -->
          <template v-if="it.type === 'place'">
            <div
              v-for="{ entry, entryIndex } in additionsBefore(places.indexOf(it))"
              :key="entry.clientId || entryIndex"
              class="timeline-row timeline-added"
              style="display:flex;gap:14px;align-items:stretch"
            >
              <div style="width:44px;display:flex;flex-direction:column;align-items:center;padding-top:6px">
                <span
                  class="place-dot mono place-dot-draft"
                  :style="{ borderColor: CAT_HUE[entryCat(entry)] || 'var(--accent-deep)', color: CAT_HUE[entryCat(entry)] || 'var(--accent-deep)' }"
                >
                  +
                </span>
                <span class="timeline-stem" />
              </div>
              <div class="ed-hair timeline-card" style="flex:1;padding:14px;margin-bottom:12px">
                <div style="display:flex;gap:12px">
                  <div style="flex:1;min-width:0">
                    <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap">
                      <span class="mono" style="font-size:12px;color:var(--accent-deep);font-weight:700">추가 예정</span>
                      <span style="font-size:15.5px;font-weight:700">{{ eventTitle(entry) }}</span>
                      <span class="badge" :style="{ background: `color-mix(in oklch, ${CAT_HUE[entryCat(entry)] || 'var(--accent)'} 12%, var(--card))`, color: CAT_HUE[entryCat(entry)] || 'var(--accent)' }">
                        {{ CATS[entryCat(entry)]?.emoji }} {{ CATS[entryCat(entry)]?.ko ?? entryCat(entry) }}
                      </span>
                    </div>
                    <div style="font-size:12.5px;color:var(--ink-faint);margin:5px 0 0">
                      {{ eventMeta(entry) }} · 동선/시간은 재생성 시 다시 계산됩니다
                    </div>
                  </div>
                  <div style="display:flex;flex-direction:column;align-items:flex-end;gap:8px;flex-shrink:0">
                    <Money :amount="entry.fee || 0" :type="entry.feeType || 'estimate'" :size="14.5" />
                    <FeeBadge :type="entry.feeType || 'estimate'" />
                    <button class="timeline-edit-btn" @click="toggleEdit(entryIndex)">편집</button>
                  </div>
                </div>
                <div v-if="openEditIndex === entryIndex" class="timeline-edit-panel">
                  <button class="timeline-danger" @click="removeEntry(entryIndex)">이 항목 삭제</button>
                  <div v-if="entry.type === 'LUNCH' || entry.type === 'DINNER'" class="timeline-meal-edit">
                    <span>음식 종류</span>
                    <div class="timeline-chip-row timeline-chip-row-scroll">
                      <button
                        v-for="kind in foodKinds"
                        :key="'food-kind-' + entryIndex + '-' + kind"
                        class="timeline-chip"
                        :class="{ on: (entry.foodKind || foodKinds[0]) === kind }"
                        @click="updateEntry(entryIndex, { foodKind: kind })"
                      >{{ kind }}</button>
                    </div>
                  </div>
                  <span>앞에 추가</span>
                  <button v-for="a in activities" :key="'added-before-' + entryIndex + '-' + a.k" class="timeline-chip" @click="insertActivity(entryIndex, a.k)">{{ a.l }}</button>
                  <button class="timeline-chip" @click="insertCafe(entryIndex)">카페</button>
                  <button class="timeline-chip" @click="insertMeal(entryIndex, 'LUNCH')">점심</button>
                  <button class="timeline-chip" @click="insertMeal(entryIndex, 'DINNER')">저녁</button>
                  <button class="timeline-chip accent" @click="insertSpecific(entryIndex)">장소</button>
                  <span>뒤에 추가</span>
                  <button v-for="a in activities" :key="'added-after-' + entryIndex + '-' + a.k" class="timeline-chip" @click="insertActivity(entryIndex + 1, a.k)">{{ a.l }}</button>
                  <button class="timeline-chip" @click="insertCafe(entryIndex + 1)">카페</button>
                  <button class="timeline-chip" @click="insertMeal(entryIndex + 1, 'LUNCH')">점심</button>
                  <button class="timeline-chip" @click="insertMeal(entryIndex + 1, 'DINNER')">저녁</button>
                  <button class="timeline-chip accent" @click="insertSpecific(entryIndex + 1)">장소</button>
                </div>
              </div>
            </div>
          </template>

          <div
            v-if="it.type === 'place'"
            class="timeline-row"
            :class="{ 'timeline-over-limit': isOverReturnLimit(it), 'timeline-deleted': isDeletedPlace(it) }"
            style="display:flex;gap:14px;align-items:stretch"
          >
            <div style="width:44px;display:flex;flex-direction:column;align-items:center;padding-top:6px">
              <span class="place-dot mono" :style="{ border: '2px solid ' + (CAT_HUE[it.cat] || 'var(--ink)'), color: CAT_HUE[it.cat] || 'var(--ink)' }">
                {{ places.indexOf(it) + 1 }}
              </span>
              <span class="timeline-stem" :class="lineLimitClass(it)" />
            </div>
            <div class="ed-hair timeline-card" style="flex:1;padding:14px;margin-bottom:12px">
              <div style="display:flex;gap:12px">
                <div style="flex:1;min-width:0">
                  <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap">
                    <span class="mono" style="font-size:12px;color:var(--accent-deep);font-weight:700">{{ it.arr }}</span>
                    <span style="font-size:15.5px;font-weight:700">{{ it.name }}</span>
                    <span v-if="isDeletedPlace(it)" class="badge">삭제 예정</span>
                    <span v-if="it.meal" class="badge badge-accent">{{ it.meal }}</span>
                    <span class="badge" :style="{ background: `color-mix(in oklch, ${CAT_HUE[it.cat]} 12%, var(--card))`, color: CAT_HUE[it.cat] }">
                      {{ CATS[it.cat]?.emoji }} {{ CATS[it.cat]?.ko ?? it.cat }}
                    </span>
                  </div>
                  <div v-if="it.note" style="font-size:12.5px;color:var(--ink-faint);margin:5px 0 0">{{ it.note }} · 체류 {{ it.stay }}분</div>
                  <div v-else style="font-size:12.5px;color:var(--ink-faint);margin:5px 0 0">체류 {{ it.stay }}분</div>
                </div>
                <div style="display:flex;flex-direction:column;align-items:flex-end;gap:8px;flex-shrink:0">
                  <Money :amount="it.fee" :type="it.feeType" :size="14.5" />
                  <FeeBadge :type="it.feeType" />
                  <button
                    v-if="editable && !isDeletedPlace(it)"
                    class="timeline-edit-btn"
                    @click="toggleEdit(placeIndex(it))"
                  >
                    편집
                  </button>
                </div>
              </div>
              <div v-if="editable && !isDeletedPlace(it) && openEditIndex === placeIndex(it)" class="timeline-edit-panel">
                <button class="timeline-danger" @click="removeEntry(placeIndex(it))">이 장소 삭제</button>
                <button class="timeline-chip accent" @click="replaceWithSameCategory(placeIndex(it), it)">같은 종류 새 장소</button>
                <div v-if="mealSlotTypeForItem(it)" class="timeline-meal-edit">
                  <span>음식 종류 변경</span>
                  <div class="timeline-chip-row timeline-chip-row-scroll">
                    <button
                      v-for="kind in foodKinds"
                      :key="'replace-food-kind-' + placeIndex(it) + '-' + kind"
                      class="timeline-chip"
                      :class="{ on: mealKindAt(placeIndex(it)) === kind }"
                      @click="replaceMealEntry(placeIndex(it), mealSlotTypeForItem(it), { foodKind: kind })"
                    >{{ kind }}</button>
                  </div>
                </div>
                <span>앞에 추가</span>
                <button v-for="a in activities" :key="'before-' + a.k" class="timeline-chip" @click="insertActivity(placeIndex(it), a.k)">{{ a.l }}</button>
                <button class="timeline-chip" @click="insertCafe(placeIndex(it))">카페</button>
                <button class="timeline-chip" @click="insertMeal(placeIndex(it), 'LUNCH')">점심</button>
                <button class="timeline-chip" @click="insertMeal(placeIndex(it), 'DINNER')">저녁</button>
                <button class="timeline-chip accent" @click="insertSpecific(placeIndex(it))">장소</button>
                <span>뒤에 추가</span>
                <button v-for="a in activities" :key="'after-' + a.k" class="timeline-chip" @click="insertActivity(placeIndex(it) + 1, a.k)">{{ a.l }}</button>
                <button class="timeline-chip" @click="insertCafe(placeIndex(it) + 1)">카페</button>
                <button class="timeline-chip" @click="insertMeal(placeIndex(it) + 1, 'LUNCH')">점심</button>
                <button class="timeline-chip" @click="insertMeal(placeIndex(it) + 1, 'DINNER')">저녁</button>
                <button class="timeline-chip accent" @click="insertSpecific(placeIndex(it) + 1)">장소</button>
              </div>
            </div>
          </div>
        </template>

        <div
          v-for="{ entry, entryIndex } in additionsBefore(places.length)"
          :key="entry.clientId || entryIndex"
          class="timeline-row timeline-added"
          style="display:flex;gap:14px;align-items:stretch"
        >
          <div style="width:44px;display:flex;flex-direction:column;align-items:center;padding-top:6px">
            <span
              class="place-dot mono place-dot-draft"
              :style="{ borderColor: CAT_HUE[entryCat(entry)] || 'var(--accent-deep)', color: CAT_HUE[entryCat(entry)] || 'var(--accent-deep)' }"
            >
              +
            </span>
            <span class="timeline-stem" />
          </div>
          <div class="ed-hair timeline-card" style="flex:1;padding:14px;margin-bottom:12px">
            <div style="display:flex;gap:12px">
              <div style="flex:1;min-width:0">
                <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap">
                  <span class="mono" style="font-size:12px;color:var(--accent-deep);font-weight:700">추가 예정</span>
                  <span style="font-size:15.5px;font-weight:700">{{ eventTitle(entry) }}</span>
                  <span class="badge" :style="{ background: `color-mix(in oklch, ${CAT_HUE[entryCat(entry)] || 'var(--accent)'} 12%, var(--card))`, color: CAT_HUE[entryCat(entry)] || 'var(--accent)' }">
                    {{ CATS[entryCat(entry)]?.emoji }} {{ CATS[entryCat(entry)]?.ko ?? entryCat(entry) }}
                  </span>
                </div>
                <div style="font-size:12.5px;color:var(--ink-faint);margin:5px 0 0">
                  {{ eventMeta(entry) }} · 동선/시간은 재생성 시 다시 계산됩니다
                </div>
              </div>
              <div style="display:flex;flex-direction:column;align-items:flex-end;gap:8px;flex-shrink:0">
                <Money :amount="entry.fee || 0" :type="entry.feeType || 'estimate'" :size="14.5" />
                <FeeBadge :type="entry.feeType || 'estimate'" />
                <button class="timeline-edit-btn" @click="toggleEdit(entryIndex)">편집</button>
              </div>
            </div>
            <div v-if="openEditIndex === entryIndex" class="timeline-edit-panel">
              <button class="timeline-danger" @click="removeEntry(entryIndex)">이 항목 삭제</button>
              <div v-if="entry.type === 'LUNCH' || entry.type === 'DINNER'" class="timeline-meal-edit">
                <span>음식 종류</span>
                <div class="timeline-chip-row timeline-chip-row-scroll">
                  <button
                    v-for="kind in foodKinds"
                    :key="'tail-food-kind-' + entryIndex + '-' + kind"
                    class="timeline-chip"
                    :class="{ on: (entry.foodKind || foodKinds[0]) === kind }"
                    @click="updateEntry(entryIndex, { foodKind: kind })"
                  >{{ kind }}</button>
                </div>
              </div>
              <span>앞에 추가</span>
              <button v-for="a in activities" :key="'tail-before-' + entryIndex + '-' + a.k" class="timeline-chip" @click="insertActivity(entryIndex, a.k)">{{ a.l }}</button>
              <button class="timeline-chip" @click="insertCafe(entryIndex)">카페</button>
              <button class="timeline-chip" @click="insertMeal(entryIndex, 'LUNCH')">점심</button>
              <button class="timeline-chip" @click="insertMeal(entryIndex, 'DINNER')">저녁</button>
              <button class="timeline-chip accent" @click="insertSpecific(entryIndex)">장소</button>
              <span>뒤에 추가</span>
              <button v-for="a in activities" :key="'tail-after-' + entryIndex + '-' + a.k" class="timeline-chip" @click="insertActivity(entryIndex + 1, a.k)">{{ a.l }}</button>
              <button class="timeline-chip" @click="insertCafe(entryIndex + 1)">카페</button>
              <button class="timeline-chip" @click="insertMeal(entryIndex + 1, 'LUNCH')">점심</button>
              <button class="timeline-chip" @click="insertMeal(entryIndex + 1, 'DINNER')">저녁</button>
              <button class="timeline-chip accent" @click="insertSpecific(entryIndex + 1)">장소</button>
            </div>
          </div>
        </div>

        <div style="display:flex;gap:14px;align-items:center;margin-top:4px">
          <div style="width:44px;display:flex;justify-content:center">
            <span style="width:12px;height:12px;border-radius:50%;background:var(--ink);box-shadow:0 0 0 4px var(--paper)" />
          </div>
          <div style="font-size:13.5px;font-weight:600;color:var(--ink-soft)">{{ course.startPoint }} 도착 · 하루 끝!</div>
        </div>
      </div>

      <div v-if="isActualEndOverLimit" class="timeline-end-over-row">
        <span class="timeline-end-over mono">현재 종료 {{ actualEndLabel }}</span>
      </div>

      <div v-if="course.warnings && course.warnings.length" class="ed-hair" style="margin-top:14px;padding:12px 14px;display:flex;flex-direction:column;gap:5px">
        <span v-for="(w, i) in course.warnings" :key="i" style="font-size:12px;color:var(--trend);display:flex;gap:6px">
          <AppIcon name="info" style="width:14px;height:14px;flex-shrink:0;margin-top:1px" /> {{ w }}
        </span>
      </div>
    </div>

    <!-- Receipt sidebar -->
    <div style="position:sticky;top:138px;display:flex;flex-direction:column;gap:14px">
      <EditorialReceipt :no="no" :groups="receiptGroups" :total="total" :guide="guide" />

      <div class="ed-hair" style="padding:16px 18px">
        <span class="lcap" style="display:block;margin-bottom:12px">비용 구성 · BREAKDOWN</span>
        <SegBar :segs="[
          { label: '교통', v: transport, color: 'var(--transit)' },
          { label: '입장', v: admission, color: 'var(--accent)' },
          { label: '식사', v: food,      color: 'var(--trend)' },
          { label: '카페', v: cafe,      color: 'var(--accent-deep)' },
        ]" />
      </div>

      <p style="font-size:11.5px;color:var(--ink-faint);line-height:1.5;margin:0 4px;display:flex;gap:6px">
        <AppIcon name="info" style="width:14px;height:14px;flex-shrink:0;margin-top:1px" />
        교통비·입장료는 확정값, 식비·카페비는 카테고리 평균 기반 추정값이에요.
      </p>
    </div>
  </div>
</template>

<style scoped>
.place-dot {
  width: 26px; height: 26px; border-radius: 50%; background: var(--card);
  display: grid; place-items: center; font-size: 12.5px; font-weight: 700;
  flex-shrink: 0; z-index: 1;
}
.place-dot-draft {
  border: 2px dashed var(--accent-deep);
  color: var(--accent-deep);
}
.timeline-stem {
  flex: 1;
  width: 0;
  border-left: 2px solid var(--line);
  margin-top: 2px;
}
.timeline-stem-dotted {
  border-left-style: dotted;
  border-left-color: var(--line-strong);
  margin-top: 0;
}
.timeline-limit-line {
  border-left-color: var(--trend);
}
.timeline-limit-cross {
  position: relative;
}
.timeline-limit-cross::before {
  content: "";
  position: absolute;
  left: -5px;
  top: 0;
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: var(--trend);
  box-shadow: 0 0 0 3px var(--paper);
}
.timeline-regen {
  border-radius: 6px;
  flex-shrink: 0;
}
.timeline-dirty-note {
  border: 1px solid color-mix(in srgb, var(--accent) 35%, var(--line));
  border-radius: 8px;
  background: var(--accent-wash);
  color: var(--accent-deep);
  padding: 8px 10px;
  margin-bottom: 12px;
  font-size: 12px;
  font-weight: 700;
}
.timeline-free-pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 7px 12px;
  border: 1px dashed color-mix(in srgb, var(--accent) 34%, var(--line));
  border-radius: 999px;
  background: color-mix(in srgb, var(--accent-wash) 45%, var(--card));
  color: var(--ink-soft);
  font-size: 12px;
  font-weight: 700;
}
.timeline-free-pill .mono {
  color: var(--accent-deep);
  font-weight: 800;
}
.timeline-edit-btn {
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--card);
  color: var(--ink-soft);
  padding: 4px 9px;
  font-size: 11.5px;
  font-weight: 800;
}
.timeline-edit-panel {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  border-top: 1px dashed var(--line);
  margin-top: 12px;
  padding-top: 10px;
}
.timeline-edit-panel span {
  font-size: 11px;
  color: var(--ink-faint);
  font-weight: 800;
  margin: 0 2px;
}
.timeline-meal-edit {
  flex-basis: 100%;
  display: flex;
  flex-direction: column;
  gap: 7px;
  padding: 8px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: color-mix(in srgb, var(--trend-wash) 34%, var(--card));
}
.timeline-chip-row {
  display: flex;
  align-items: center;
  gap: 6px;
}
.timeline-chip-row-scroll {
  overflow-x: auto;
  overscroll-behavior-x: contain;
  scrollbar-width: thin;
  padding-bottom: 2px;
}
.timeline-chip-row-scroll .timeline-chip {
  flex: 0 0 auto;
}
.timeline-chip,
.timeline-danger {
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--card);
  color: var(--ink-soft);
  padding: 4px 8px;
  font-size: 11.5px;
  font-weight: 700;
}
.timeline-chip.on {
  color: var(--trend);
  border-color: color-mix(in srgb, var(--trend) 48%, var(--line));
  background: var(--trend-wash);
}
.timeline-chip.accent {
  color: var(--accent-deep);
  border-color: color-mix(in srgb, var(--accent) 42%, var(--line));
  background: var(--accent-wash);
}
.timeline-danger {
  color: var(--trend);
  border-color: color-mix(in srgb, var(--trend) 40%, var(--line));
  background: var(--trend-wash);
}
.timeline-added .timeline-card {
  border-color: #2b6cb0;
  box-shadow: 0 0 0 2px #2b6cb0;
}
.timeline-deleted {
  opacity: .48;
  filter: grayscale(1);
}
.timeline-deleted .timeline-card {
  background: color-mix(in srgb, var(--card-sunken) 72%, var(--line));
  border-style: dashed;
}
.timeline-over-limit .timeline-card {
  border-color: var(--trend);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--trend) 42%, transparent);
}
.timeline-over-limit .timeline-transit-pill {
  outline: 2px solid var(--trend);
  outline-offset: 2px;
}
.timeline-end-over-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}
.timeline-end-over {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--trend);
  border: 1px solid color-mix(in srgb, var(--trend) 52%, var(--line));
  background: var(--trend-wash);
  border-radius: 999px;
  padding: 5px 10px;
  font-size: 12px;
  font-weight: 900;
}
</style>
