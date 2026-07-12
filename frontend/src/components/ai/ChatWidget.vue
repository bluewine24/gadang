<script setup>
import { ref, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { askAI } from '../../api/gadang.js'

const router = useRouter()
const open = ref(false)
const input = ref('')
const loading = ref(false)
const scroller = ref(null)
const messages = ref([
  { role: 'ai', text: '안녕하세요! 가당 AI 컨시어지예요. 지역 내 추천 장소나 다른 사용자의 공유 코스를 찾아드릴게요.' },
])

const suggestions = [
  '부산에서 가볼 만한 카페와 문화공간 추천해줘',
  '전주 한옥마을 근처 맛집 추천해줘',
  '바다 보면서 회 먹는 가성비 공유 코스 찾아줘',
]

async function scrollToBottom() {
  await nextTick()
  if (scroller.value) scroller.value.scrollTop = scroller.value.scrollHeight
}

async function send(text) {
  const msg = (text ?? input.value).trim()
  if (!msg || loading.value) return
  input.value = ''
  messages.value.push({ role: 'user', text: msg })
  loading.value = true
  await scrollToBottom()
  try {
    const data = await askAI(msg)
    messages.value.push({
      role: 'ai',
      text: data?.reply || '응답을 받지 못했어요.',
      actions: Array.isArray(data?.actions) ? data.actions : [],
    })
  } catch {
    messages.value.push({ role: 'ai', text: '죄송해요, 일시적인 오류가 발생했어요. 잠시 후 다시 시도해 주세요.' })
  } finally {
    loading.value = false
    await scrollToBottom()
  }
}

function runAction(a) {
  if (!a?.route) return
  router.push({ path: a.route, query: a.query || {} })
  open.value = false
}

const placeActions = (m) => (m.actions || []).filter((a) => a.type === 'place')
const otherActions = (m) => (m.actions || []).filter((a) => a.type !== 'place')
</script>

<template>
  <div class="cw">
    <button class="cw-fab" :class="{ on: open }" @click="open = !open" aria-label="AI 챗봇 열기">
      <span v-if="!open">AI</span>
      <span v-else>×</span>
    </button>

    <transition name="cw-pop">
      <section v-if="open" class="cw-panel">
        <header class="cw-head">
          <strong>가당 AI 컨시어지</strong>
          <span class="cw-dot" /> 온라인
        </header>

        <div ref="scroller" class="cw-body">
          <div v-for="(m, i) in messages" :key="i" class="cw-row" :class="m.role">
            <div class="cw-msg">
              <div class="cw-bubble">{{ m.text }}</div>
              <div v-if="placeActions(m).length" class="cw-places">
                <button
                  v-for="(a, j) in placeActions(m)"
                  :key="'p' + j"
                  class="cw-place"
                  @click="runAction(a)"
                >
                  <span class="cw-place-pin">핀</span>
                  <span class="cw-place-name">{{ a.label }}</span>
                  <span class="cw-place-go">지도</span>
                </button>
              </div>
              <div v-if="otherActions(m).length" class="cw-acts">
                <button
                  v-for="(a, j) in otherActions(m)"
                  :key="'a' + j"
                  class="cw-act"
                  :class="a.type"
                  @click="runAction(a)"
                >
                  {{ a.label }} →
                </button>
              </div>
            </div>
          </div>
          <div v-if="loading" class="cw-row ai">
            <div class="cw-bubble cw-typing"><span /><span /><span /></div>
          </div>

          <div v-if="messages.length <= 1" class="cw-sugs">
            <button v-for="s in suggestions" :key="s" class="cw-sug" @click="send(s)">{{ s }}</button>
          </div>
        </div>

        <form class="cw-input" @submit.prevent="send()">
          <input
            v-model="input"
            type="text"
            placeholder="찾고 싶은 장소나 공유 코스를 말해 주세요"
            :disabled="loading"
          />
          <button type="submit" :disabled="loading || !input.trim()">보내기</button>
        </form>
      </section>
    </transition>
  </div>
</template>

<style scoped>
.cw {
  position: fixed;
  right: 20px;
  bottom: 84px;
  z-index: 1000;
  font-family: var(--font);
}

.cw-fab {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  border: none;
  background: var(--accent, #2b6cb0);
  color: #fff;
  font-weight: 800;
  font-size: 18px;
  cursor: pointer;
  box-shadow: var(--shadow-lg, 0 8px 24px rgba(0, 0, 0, 0.2));
  margin-left: auto;
  display: block;
  transition: transform 0.15s ease, background 0.15s ease;
}
.cw-fab:hover { transform: translateY(-2px); }
.cw-fab.on { background: var(--accent-deep, #1f4e79); }

.cw-panel {
  position: absolute;
  right: 0;
  bottom: 68px;
  width: min(380px, calc(100vw - 32px));
  height: 540px;
  max-height: calc(100vh - 180px);
  background: var(--paper, #fff);
  border: 1px solid var(--line, #e2e2e2);
  border-radius: var(--radius-lg, 16px);
  box-shadow: var(--shadow-lg, 0 16px 48px rgba(0, 0, 0, 0.22));
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.cw-head {
  padding: 14px 16px;
  background: var(--accent, #2b6cb0);
  color: #fff;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}
.cw-head strong { flex: 1; font-size: 15px; }
.cw-dot {
  width: 8px; height: 8px; border-radius: 50%;
  background: #5be36a; display: inline-block;
}

.cw-body {
  flex: 1;
  overflow-y: auto;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: var(--card, #f7f7f5);
}

.cw-row { display: flex; }
.cw-row.user { justify-content: flex-end; }
.cw-row.ai { justify-content: flex-start; }

.cw-bubble {
  max-width: 82%;
  padding: 10px 13px;
  border-radius: 14px;
  font-size: 13.5px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
}
.cw-row.user .cw-bubble {
  background: var(--accent, #2b6cb0);
  color: #fff;
  border-bottom-right-radius: 4px;
}
.cw-row.ai .cw-bubble {
  background: #fff;
  color: var(--ink, #222);
  border: 1px solid var(--line, #e8e8e8);
  border-bottom-left-radius: 4px;
}

.cw-msg { max-width: 82%; }
.cw-row.user .cw-msg { max-width: 82%; }
.cw-msg .cw-bubble { max-width: 100%; }

.cw-acts { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 6px; }
.cw-act {
  padding: 7px 11px;
  font-size: 12px;
  font-weight: 700;
  background: #fff;
  color: var(--accent-deep, #1f4e79);
  border: 1.5px solid var(--accent, #2b6cb0);
  border-radius: 999px;
  cursor: pointer;
  transition: background 0.12s ease, color 0.12s ease;
}
.cw-act:hover { background: var(--accent, #2b6cb0); color: #fff; }
.cw-act.shared { border-color: #059669; color: #047857; }
.cw-act.shared:hover { background: #059669; color: #fff; }

.cw-places { display: flex; flex-direction: column; gap: 5px; margin-top: 7px; }
.cw-place {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 9px 11px;
  background: #fff;
  border: 1px solid var(--line, #e2e2e2);
  border-radius: 11px;
  cursor: pointer;
  text-align: left;
  transition: border-color 0.12s ease, background 0.12s ease, transform 0.12s ease;
}
.cw-place:hover {
  border-color: var(--accent, #2b6cb0);
  background: var(--accent-wash, #eaf2fb);
  transform: translateX(2px);
}
.cw-place-pin { font-size: 11px; font-weight: 700; color: var(--accent-deep, #1f4e79); flex-shrink: 0; }
.cw-place-name {
  flex: 1;
  font-size: 13px;
  font-weight: 600;
  color: var(--ink, #222);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cw-place-go {
  font-size: 11px;
  font-weight: 700;
  color: var(--accent-deep, #1f4e79);
  flex-shrink: 0;
}

.cw-typing { display: flex; gap: 4px; }
.cw-typing span {
  width: 6px; height: 6px; border-radius: 50%;
  background: var(--ink-faint, #aaa);
  animation: cw-blink 1.2s infinite both;
}
.cw-typing span:nth-child(2) { animation-delay: 0.2s; }
.cw-typing span:nth-child(3) { animation-delay: 0.4s; }
@keyframes cw-blink { 0%, 80%, 100% { opacity: 0.3; } 40% { opacity: 1; } }

.cw-sugs { display: flex; flex-direction: column; gap: 6px; margin-top: 4px; }
.cw-sug {
  text-align: left;
  padding: 9px 12px;
  font-size: 12.5px;
  background: var(--accent-wash, #eaf2fb);
  color: var(--accent-deep, #1f4e79);
  border: 1px solid var(--line, #e2e2e2);
  border-radius: 10px;
  cursor: pointer;
}
.cw-sug:hover { background: #fff; }

.cw-input {
  display: flex;
  gap: 8px;
  padding: 10px;
  border-top: 1px solid var(--line, #e2e2e2);
  background: var(--paper, #fff);
}
.cw-input input {
  flex: 1;
  padding: 10px 12px;
  border: 1px solid var(--line, #ddd);
  border-radius: 10px;
  font-size: 13.5px;
  outline: none;
  font-family: inherit;
}
.cw-input input:focus { border-color: var(--accent, #2b6cb0); }
.cw-input button {
  padding: 0 16px;
  border: none;
  border-radius: 10px;
  background: var(--accent, #2b6cb0);
  color: #fff;
  font-weight: 700;
  font-size: 13px;
  cursor: pointer;
}
.cw-input button:disabled { opacity: 0.45; cursor: default; }

.cw-pop-enter-active, .cw-pop-leave-active { transition: opacity 0.18s ease, transform 0.18s ease; }
.cw-pop-enter-from, .cw-pop-leave-to { opacity: 0; transform: translateY(12px) scale(0.97); }
</style>
