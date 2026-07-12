<script setup>
import { computed } from 'vue'
import { CATS, CAT_HUE } from '../../data/mock.js'

const props = defineProps({ cat: String, size: { type: Number, default: 30 } })
const info  = computed(() => CATS[props.cat] || {})
const color = computed(() => CAT_HUE[props.cat] || 'var(--ink-soft)')
</script>

<template>
  <span :style="{
    width: size + 'px', height: size + 'px', borderRadius: '9px', flexShrink: 0,
    display: 'grid', placeItems: 'center',
    background: `color-mix(in oklch, ${color} 13%, var(--card))`,
    color, fontSize: size * 0.5 + 'px', fontWeight: 700,
  }">{{ info.emoji }}</span>
</template>
