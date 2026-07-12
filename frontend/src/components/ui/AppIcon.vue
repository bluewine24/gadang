<script setup>
defineProps({ name: { type: String, required: true } })

const PATHS = {
  home:      '<path d="M3 10.5 12 3l9 7.5"/><path d="M5 9.5V20a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V9.5"/><path d="M9.5 21v-6h5v6"/>',
  map:       '<path d="m9 4-6 2.5v13L9 17l6 2.5L21 17V4l-6 2.5L9 4Z"/><path d="M9 4v13M15 6.5v13"/>',
  route:     '<circle cx="6" cy="19" r="2.4"/><circle cx="18" cy="5" r="2.4"/><path d="M8.4 19H14a3.5 3.5 0 0 0 0-7H10a3.5 3.5 0 0 1 0-7h5.6"/>',
  community: '<path d="M21 11.5a8.4 8.4 0 0 1-11.7 7.7L3 21l1.8-5.3A8.4 8.4 0 1 1 21 11.5Z"/><path d="M8.5 11.5h7M8.5 14.5h4"/>',
  user:      '<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>',
  receipt:   '<path d="M5 3.5v17l2-1.2 2 1.2 2-1.2 2 1.2 2-1.2 2 1.2v-17l-2 1.2-2-1.2-2 1.2-2-1.2-2 1.2L5 3.5Z"/><path d="M9 8.5h6M9 12h6M9 15.5h3.5"/>',
  clock:     '<circle cx="12" cy="12" r="9"/><path d="M12 7.5V12l3 2"/>',
  pin:       '<path d="M12 21s7-5.3 7-11a7 7 0 1 0-14 0c0 5.7 7 11 7 11Z"/><circle cx="12" cy="10" r="2.6"/>',
  search:    '<circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/>',
  train:     '<rect x="5" y="3.5" width="14" height="13" rx="3"/><path d="M5 11h14M9 20l-2 1.5M15 20l2 1.5"/><circle cx="8.5" cy="13.7" r="1"/><circle cx="15.5" cy="13.7" r="1"/>',
  bus:       '<rect x="4.5" y="3.5" width="15" height="13.5" rx="2.5"/><path d="M4.5 11h15M7 21v-2M17 21v-2M4.5 7h15"/><circle cx="8" cy="14" r="1"/><circle cx="16" cy="14" r="1"/>',
  walk:      '<circle cx="13" cy="4.5" r="1.8"/><path d="m9 21 2.5-5 1-4 3 2 2 1M11.5 12l-1.5-3.5L13 7l3 1.5 1 2.5M10 21l1.5-5"/>',
  heart:     '<path d="M12 21l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.18L12 21z"/>',
  heartFill: '<path d="M12 21l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.18L12 21z" fill="currentColor" stroke="none"/>',
  star:      '<path d="m12 3 2.6 5.6 6 .7-4.5 4.1 1.2 6L12 16.8 6.7 19.5l1.2-6-4.5-4.1 6-.7L12 3Z"/>',
  comment:   '<path d="M21 11.5a8.4 8.4 0 0 1-11.7 7.7L3 21l1.8-5.3A8.4 8.4 0 1 1 21 11.5Z"/>',
  bookmark:  '<path d="M6 3.5h12v17l-6-4-6 4v-17Z"/>',
  trend:     '<path d="M3 16.5 9 10l4 4 8-9"/><path d="M15 5h6v6"/>',
  plus:      '<path d="M12 5v14M5 12h14"/>',
  chevR:     '<path d="m9 5 7 7-7 7"/>',
  chevD:     '<path d="m5 9 7 7 7-7"/>',
  chevL:     '<path d="m15 5-7 7 7 7"/>',
  swap:      '<path d="M7 4 4 7l3 3M4 7h13M17 20l3-3-3-3M20 17H7"/>',
  trash:     '<path d="M4 7h16M9 7V4.5h6V7M6 7l1 13h10l1-13M10 11v5M14 11v5"/>',
  filter:    '<path d="M4 5h16l-6.5 8v5L10.5 20v-7L4 5Z"/>',
  coffee:    '<path d="M4 9h13v4a5 5 0 0 1-5 5H9a5 5 0 0 1-5-5V9Z"/><path d="M17 10h2.5a2 2 0 0 1 0 4H17M8 5.5V3.5M12 5.5V3.5"/>',
  won:       '<path d="M4 7l3 9 3-7 3 7 3-9M3.5 11h17"/>',
  info:      '<circle cx="12" cy="12" r="9"/><path d="M12 11v5M12 8h.01"/>',
  check:     '<path d="m5 12.5 4.5 4.5L19 7"/>',
  sliders:   '<path d="M4 8h10M18 8h2M4 16h2M10 16h10"/><circle cx="16" cy="8" r="2.2"/><circle cx="8" cy="16" r="2.2"/>',
  bell:      '<path d="M6 9a6 6 0 0 1 12 0c0 5 2 6 2 6H4s2-1 2-6Z"/><path d="M10 20a2 2 0 0 0 4 0"/>',
  flag:      '<path d="M5 21V4M5 5h11l-2 3 2 3H5"/>',
  gauge:     '<path d="M5 18a8 8 0 1 1 14 0"/><path d="M12 14l3.5-3.5"/><circle cx="12" cy="14" r="1.2"/>',
  drag:      '<circle cx="9" cy="6" r="1.3"/><circle cx="9" cy="12" r="1.3"/><circle cx="9" cy="18" r="1.3"/><circle cx="15" cy="6" r="1.3"/><circle cx="15" cy="12" r="1.3"/><circle cx="15" cy="18" r="1.3"/>',
}
</script>

<template>
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"
       stroke-linecap="round" stroke-linejoin="round">
    <g v-html="PATHS[name] || ''" />
  </svg>
</template>
