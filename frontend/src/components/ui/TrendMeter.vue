<script setup>
import { computed } from 'vue'
import AppIcon from './AppIcon.vue'

const props = defineProps({
  score: Number,
  showLabel: { type: Boolean, default: true },
  noun: { type: String, default: '여행지' }, // 지역추천=여행지 / 지도 장소=장소
})

// 검색 트렌드 4단계 등급 — 단계별 색상
const LEVELS = [
  { min: 75, prefix: '최고의',    color: '#d6453a' },
  { min: 50, prefix: '인기 만점', color: '#e07b39' },
  { min: 25, prefix: '요즘 뜨는', color: '#c9a227' },
  { min: 0,  prefix: '숨은',      color: '#5b7fa6' },
]
const level = computed(() => {
  const s = props.score || 0
  const l = LEVELS.find((x) => s >= x.min) ?? LEVELS[LEVELS.length - 1]
  return { ...l, label: `${l.prefix} ${props.noun}` }
})
</script>

<template>
  <span class="tm" :data-tip="'검색 트렌드 ' + Math.round(score || 0) + ' / 100'">
    <AppIcon name="trend" :style="{ width:'13px', height:'13px', color: level.color }" />
    <span v-if="showLabel" class="tm-label" :style="{ color: level.color }">{{ level.label }}</span>
    <span class="tm-bar">
      <span :style="{ display:'block', height:'100%', width: (score || 0) + '%', background: level.color, borderRadius:'3px' }" />
    </span>
  </span>
</template>

<style scoped>
.tm {
  position: relative;
  display: inline-flex; align-items: center; gap: 6px;
  cursor: default;
}
.tm-label { font-size: 12px; font-weight: 700; letter-spacing: 0.02em; white-space: nowrap; }
.tm-bar {
  width: 38px; height: 5px; border-radius: 3px;
  background: var(--card-sunken); overflow: hidden; display: inline-block;
}
/* 호버 시 원점수 툴팁 */
.tm::after {
  content: attr(data-tip);
  position: absolute; bottom: calc(100% + 6px); right: 0;
  background: var(--ink, #222); color: #fff;
  font-size: 11px; font-weight: 600; white-space: nowrap;
  padding: 4px 8px; border-radius: 4px;
  opacity: 0; pointer-events: none; transform: translateY(3px);
  transition: opacity 0.15s, transform 0.15s;
  z-index: 10;
}
.tm:hover::after { opacity: 1; transform: translateY(0); }
</style>
