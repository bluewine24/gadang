<script setup>
import { computed } from 'vue'
import { won } from '../../data/mock.js'

const props = defineProps({
  amount: { type: Number, default: 0 },
  type:   { type: String, default: 'confirm' },
  size:   { type: [Number, String], default: 14 },
})

const isFree = computed(() => props.amount === 0 || props.type === 'free')
const isEst  = computed(() => props.type === 'estimate')
</script>

<template>
  <span v-if="isFree" class="mono free-text" :style="{ fontSize: size + 'px' }">무료</span>
  <span v-else class="mono" :class="{ 'est-text': isEst, 'ink-text': !isEst }"
        :style="{ fontSize: size + 'px', borderBottom: isEst ? '1.5px dashed var(--line-strong)' : 'none', paddingBottom: isEst ? '1px' : 0 }">
    {{ won(amount) }}<span style="font-size: 0.78em; font-weight: 500; margin-left: 1px">원</span>
  </span>
</template>

<style scoped>
.free-text { color: var(--free); font-weight: 700; }
.est-text  { color: var(--ink-soft); font-weight: 600; }
.ink-text  { color: var(--ink); font-weight: 600; }
</style>
