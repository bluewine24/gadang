<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '../components/ui/AppIcon.vue'
import { getCommunityPost, createComment, deleteComment, deletePost, toAbsoluteUrl } from '../api/gadang.js'
import { useAuth } from '../composables/useAuth.js'

const route  = useRoute()
const router = useRouter()
const { isLoggedIn, user } = useAuth()

const isAdmin = computed(() => user.value?.role === 'ADMIN')
const myId    = computed(() => user.value?.userId ?? user.value?.id)

const post        = ref(null)
const newComment  = ref('')
const loading     = ref(true)
const submitting  = ref(false)

async function load() {
  loading.value = true
  try {
    post.value = await getCommunityPost(route.params.id)
  } finally {
    loading.value = false
  }
}

async function submitComment() {
  if (!newComment.value.trim() || submitting.value) return
  submitting.value = true
  try {
    await createComment(post.value.postId, newComment.value.trim())
    newComment.value = ''
    await load()
  } finally {
    submitting.value = false
  }
}

async function removeComment(commentId) {
  if (!confirm('댓글을 삭제할까요?')) return
  await deleteComment(commentId)
  await load()
}

async function removePost() {
  if (!confirm('게시글을 삭제할까요?')) return
  await deletePost(post.value.postId)
  router.push('/community')
}

function fmtDate(iso) {
  if (!iso) return ''
  return String(iso).slice(0, 16).replace('T', ' ')
}

// 장소 상세가 없는 오래된 게시글 — content 마크다운 파싱
function renderContent(raw) {
  if (!raw) return ''
  return raw
    .replace(/^## (.+)$/gm, '<h3 class="place-heading">$1</h3>')
    .replace(/\[image:([^\]]+)\]/g, (_, u) => `<img src="${toAbsoluteUrl(u)}" class="place-img" />`)
    .replace(/\n/g, '<br>')
}

function formatDuration(min) {
  if (!min) return null
  const h = Math.floor(min / 60), m = min % 60
  return h > 0 ? `${h}시간 ${m > 0 ? m + '분' : ''}` : `${m}분`
}

function won(n) { return Number(n || 0).toLocaleString('ko-KR') }

const hasStructured = computed(() =>
  post.value?.places?.length > 0)

onMounted(load)
</script>

<template>
  <div class="screen-wrap" style="max-width:780px">
    <button class="btn btn-outline btn-sm" style="margin-bottom:18px" @click="router.push('/community')">
      ← 목록으로
    </button>

    <div v-if="loading" style="padding:60px;text-align:center;color:var(--ink-faint)">불러오는 중...</div>

    <template v-else-if="post">
      <!-- 게시글 헤더 -->
      <div class="card" style="padding:24px 26px;margin-bottom:14px">
        <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:14px">
          <div style="display:flex;align-items:center;gap:10px">
            <span class="avatar" style="width:34px;height:34px;font-size:14px">{{ post.authorNickname?.[0] }}</span>
            <div>
              <div style="font-weight:700;font-size:14px">{{ post.authorNickname }}</div>
              <div class="mono" style="font-size:11.5px;color:var(--ink-faint)">{{ fmtDate(post.createdAt) }}</div>
            </div>
          </div>
          <button v-if="myId === post.userId || isAdmin"
                  class="btn btn-sm" style="color:var(--trend);border:1px solid var(--trend)"
                  @click="removePost">삭제</button>
        </div>

        <h1 style="font-size:22px;font-weight:800;line-height:1.3;margin-bottom:14px">{{ post.title }}</h1>

        <!-- 총 지출 / 총 시간 요약 칩 -->
        <div v-if="post.totalCost > 0 || post.totalDurationMin > 0"
             style="display:flex;gap:8px;flex-wrap:wrap;margin-bottom:18px">
          <span v-if="post.totalCost > 0"
                style="background:var(--card-sunken);border-radius:20px;padding:5px 13px;font-size:12.5px;font-weight:600">
            💰 {{ won(post.totalCost) }}원
          </span>
          <span v-if="post.totalDurationMin > 0"
                style="background:var(--card-sunken);border-radius:20px;padding:5px 13px;font-size:12.5px;font-weight:600">
            ⏱ {{ formatDuration(post.totalDurationMin) }}
          </span>
        </div>
      </div>

      <!-- 머리말 -->
      <div v-if="post.intro" class="card prose-card">
        <div class="section-tag">머리말</div>
        <p style="margin:0;font-size:15px;line-height:1.8;color:var(--ink)">{{ post.intro }}</p>
      </div>

      <!-- 장소별 상세 (구조화된 신규 게시글) -->
      <template v-if="hasStructured">
        <div v-for="(place, i) in post.places" :key="place.detailId ?? i" class="card place-card">
          <!-- 헤더 -->
          <div style="display:flex;align-items:center;gap:10px;margin-bottom:14px">
            <span class="place-num">{{ i + 1 }}</span>
            <h2 style="font-size:18px;font-weight:800;margin:0">{{ place.placeName }}</h2>
          </div>

          <!-- 금액 / 소요 시간 -->
          <div v-if="place.cost > 0 || place.durationMin > 0"
               style="display:flex;gap:10px;flex-wrap:wrap;margin-bottom:14px">
            <span v-if="place.cost > 0" class="meta-chip">💰 {{ won(place.cost) }}원</span>
            <span v-if="place.durationMin > 0" class="meta-chip">⏱ {{ formatDuration(place.durationMin) }}</span>
          </div>

          <!-- 이미지 -->
          <div v-if="place.images?.length"
               style="display:grid;grid-template-columns:repeat(auto-fill,minmax(200px,1fr));gap:8px;margin-bottom:14px">
            <img v-for="(url, ii) in place.images" :key="ii" :src="toAbsoluteUrl(url)"
                 style="width:100%;aspect-ratio:4/3;object-fit:cover;border-radius:10px;background:var(--card-sunken)" />
          </div>

          <!-- 텍스트 -->
          <p v-if="place.textContent" style="margin:0;font-size:14.5px;line-height:1.8;color:var(--ink);white-space:pre-wrap">{{ place.textContent }}</p>
        </div>
      </template>

      <!-- 기존 마크다운 content (구조화 데이터 없을 때) -->
      <div v-else-if="post.content" class="card" style="padding:22px 26px">
        <div class="post-body" v-html="renderContent(post.content)" />
      </div>

      <!-- 꼬리말 -->
      <div v-if="post.outro" class="card prose-card">
        <div class="section-tag">꼬리말</div>
        <p style="margin:0;font-size:15px;line-height:1.8;color:var(--ink)">{{ post.outro }}</p>
      </div>

      <!-- 댓글 -->
      <div class="card" style="padding:20px 24px">
        <div style="font-weight:700;font-size:15px;margin-bottom:14px">
          댓글 <span class="mono" style="color:var(--accent-deep)">{{ post.comments?.length ?? 0 }}</span>
        </div>

        <div v-for="c in post.comments" :key="c.commentId"
             style="display:flex;gap:10px;padding:12px 0;border-bottom:1px solid var(--line)">
          <span class="avatar" style="width:28px;height:28px;font-size:11px;flex-shrink:0">{{ c.authorNickname?.[0] }}</span>
          <div style="flex:1">
            <div style="display:flex;align-items:baseline;gap:8px;margin-bottom:4px">
              <span style="font-weight:600;font-size:13px">{{ c.authorNickname }}</span>
              <span class="mono" style="font-size:11px;color:var(--ink-faint)">{{ fmtDate(c.createdAt) }}</span>
            </div>
            <div style="font-size:13.5px;color:var(--ink-soft)">{{ c.content }}</div>
          </div>
          <button v-if="myId === c.userId || isAdmin"
                  style="font-size:11px;color:var(--ink-faint);align-self:flex-start;padding:2px 6px"
                  @click="removeComment(c.commentId)">삭제</button>
        </div>

        <div v-if="!post.comments?.length"
             style="color:var(--ink-faint);font-size:13.5px;padding:16px 0;text-align:center">
          첫 번째 댓글을 남겨보세요.
        </div>

        <div v-if="isLoggedIn" style="display:flex;gap:8px;margin-top:16px">
          <input v-model="newComment" placeholder="댓글 입력..." @keyup.enter="submitComment"
                 style="flex:1;border:1px solid var(--line);border-radius:8px;padding:10px 13px;font-size:13.5px;background:var(--bg);color:var(--ink)" />
          <button class="btn btn-primary btn-sm" :disabled="submitting" @click="submitComment">등록</button>
        </div>
        <div v-else style="margin-top:14px;font-size:13px;color:var(--ink-faint);text-align:center">
          댓글을 작성하려면
          <button style="color:var(--accent-deep);font-weight:600" @click="router.push('/login')">로그인</button>
          해주세요.
        </div>
      </div>
    </template>

    <div v-else style="padding:60px;text-align:center;color:var(--ink-faint)">
      <div style="font-size:32px;margin-bottom:12px">🚫</div>
      <div style="font-weight:700;font-size:16px;margin-bottom:8px">접근할 수 없는 게시글입니다.</div>
      <div style="font-size:13px">관리자에 의해 블라인드 처리되었거나 삭제된 게시글입니다.</div>
    </div>
  </div>
</template>

<style scoped>
.place-card  { padding: 22px 26px; margin-bottom: 14px; }
.prose-card  { padding: 20px 26px; margin-bottom: 14px; }
.place-num {
  width: 28px; height: 28px; border-radius: 50%;
  background: var(--accent); color: #fff;
  display: inline-flex; align-items: center; justify-content: center;
  font-size: 12px; font-weight: 800; flex-shrink: 0;
}
.meta-chip {
  background: var(--card-sunken); border-radius: 20px;
  padding: 4px 12px; font-size: 12.5px; font-weight: 600;
}
.section-tag {
  font-size: 10.5px; font-weight: 700; color: var(--ink-faint);
  font-family: var(--mono); letter-spacing: .07em; text-transform: uppercase;
  margin-bottom: 10px;
}
.post-body :deep(h3)  { font-size: 17px; font-weight: 800; margin: 22px 0 10px; }
.post-body :deep(img) { max-width: 100%; border-radius: 10px; margin: 6px 0; }
</style>
