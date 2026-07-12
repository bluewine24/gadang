<script setup>
defineProps({ label: { type: String, default: 'IMAGE' }, height: { type: [Number, String], default: 160 } })
</script>

<template>
  <div class="imgph" :style="{ height: typeof height === 'number' ? height + 'px' : height }">
    <span>{{ label }}</span>
  </div>
</template>
