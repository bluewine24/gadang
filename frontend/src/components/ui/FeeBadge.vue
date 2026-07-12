<script setup>
import AppIcon from './AppIcon.vue'
defineProps({ type: { type: String, default: 'confirm' } })
</script>

<template>
  <span v-if="type === 'free'"     class="badge badge-free">무료</span>
  <span v-else-if="type === 'estimate'" class="badge badge-estimate">추정</span>
  <span v-else class="badge badge-confirm">
    <AppIcon name="check" style="width:11px;height:11px" />확정
  </span>
</template>
