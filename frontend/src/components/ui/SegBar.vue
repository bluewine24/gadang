<script setup>
import { computed } from 'vue'
import { won } from '../../data/mock.js'

const props = defineProps({
  segs: { type: Array, default: () => [] },
})

const total = computed(() => props.segs.reduce((s, x) => s + x.v, 0) || 1)
</script>

<template>
  <div>
    <div style="display:flex;height:12px;border-radius:7px;overflow:hidden;gap:2px;background:var(--card-sunken)">
      <template v-for="s in segs" :key="s.label">
        <div v-if="s.v > 0"
             :title="s.label"
             :style="{ width: (s.v / total * 100) + '%', background: s.color, minWidth: '4px' }" />
      </template>
    </div>
    <div style="display:flex;flex-wrap:wrap;gap:6px 16px;margin-top:10px">
      <span v-for="s in segs" :key="s.label"
            style="display:inline-flex;align-items:center;gap:6px;font-size:12.5px;color:var(--ink-soft)">
        <span :style="{ width:'9px', height:'9px', borderRadius:'3px', background: s.color }" />
        {{ s.label }}&nbsp;<b class="mono" style="color:var(--ink);font-weight:600">{{ won(s.v) }}</b>
      </span>
    </div>
  </div>
</template>
