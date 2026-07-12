<script setup>
import { computed } from 'vue'
import { won } from '../../data/mock.js'
import Money from './Money.vue'

const props = defineProps({
  no:     { type: String, default: '' },
  groups: { type: Array, default: () => [] },
  total:  { type: Number, default: 0 },
  guide:  { type: Number, default: null },
})

const over = computed(() => props.guide != null && props.total > props.guide)
const pct  = computed(() => props.guide ? Math.round(props.total / props.guide * 100) : 0)
</script>

<template>
  <div class="ed-card" style="overflow:hidden;background:var(--card)">
    <!-- header -->
    <div style="padding:18px 20px 14px;text-align:center;border-bottom:1.5px dashed var(--ink)">
      <div class="lcap lcap-acc" style="letter-spacing:0.4em">GADANG · RECEIPT</div>
      <div class="disp" style="font-size:26px;margin:8px 0 6px">가성비 영수증</div>
      <div class="mono" style="font-size:11px;color:var(--ink-faint)">NO. {{ no }}</div>
    </div>

    <!-- groups -->
    <div style="padding:8px 20px 4px">
      <div v-for="(g, gi) in groups" :key="gi"
           style="padding:12px 0;border-bottom:1px solid var(--line)">
        <div class="lcap" style="display:block;margin-bottom:9px">{{ g.label }}</div>
        <div style="display:flex;flex-direction:column;gap:9px">
          <div v-for="(it, i) in g.items" :key="i"
               style="display:flex;align-items:baseline;justify-content:space-between;gap:12px">
            <div style="min-width:0">
              <div style="font-size:13.5px;font-weight:600">{{ it.name }}</div>
              <div v-if="it.sub" style="font-size:11px;color:var(--ink-faint);margin-top:1px">{{ it.sub }}</div>
            </div>
            <Money :amount="it.amount" :type="it.type" :size="14" />
          </div>
        </div>
        <div v-if="g.subtotal != null"
             style="display:flex;justify-content:space-between;margin-top:10px;font-size:12px">
          <span class="lcap-sm lcap" style="letter-spacing:0.2em">{{ g.label }} 소계</span>
          <span class="mono" style="font-weight:600">{{ won(g.subtotal) }}원</span>
        </div>
      </div>
    </div>

    <!-- total -->
    <div style="padding:14px 20px;border-top:1.5px dashed var(--ink)">
      <div style="display:flex;align-items:baseline;justify-content:space-between">
        <span class="disp" style="font-size:18px">총 예상 비용</span>
        <span class="mono"
              :style="{ fontSize:'27px', fontWeight:700, letterSpacing:'-0.03em', color: over ? 'var(--trend)' : 'var(--ink)' }">
          {{ won(total) }}<span style="font-size:15px">원</span>
        </span>
      </div>

      <div v-if="guide != null"
           style="display:flex;justify-content:space-between;margin-top:8px;font-size:12.5px;color:var(--ink-soft)">
        <span>예산 {{ won(guide) }}원 대비</span>
        <span class="mono" :style="{ fontWeight:700, color: over ? 'var(--trend)' : 'var(--free)' }">
          {{ over ? '+' + won(total - guide) + '원 초과' : '−' + won(guide - total) + '원 잉여' }}
        </span>
      </div>

      <div v-if="guide != null" style="margin-top:10px">
        <div style="height:8px;background:var(--card-sunken);border-radius:2px;overflow:hidden;border:1px solid var(--line)">
          <div :style="{ height:'100%', width: Math.min(100, pct) + '%', background: over ? 'var(--trend)' : 'var(--accent)' }" />
        </div>
        <div style="display:flex;justify-content:space-between;margin-top:4px" class="mono">
          <span style="font-size:10px;color:var(--ink-faint)">₩0</span>
          <span style="font-size:10px;color:var(--ink-faint)">{{ pct }}% 사용</span>
          <span style="font-size:10px;color:var(--ink-faint)">{{ won(guide) }}</span>
        </div>
      </div>
    </div>

    <!-- footer -->
    <div style="padding:12px 20px 16px;border-top:1px solid var(--line);text-align:center">
      <div class="lcap" style="letter-spacing:0.3em;font-size:10px">★ THANK YOU FOR TRAVELING ★</div>
    </div>
  </div>
</template>
