<script setup>
import { ref, onMounted, watch } from 'vue'
import {
  getAdminStats, getAdminUsers, updateUserRole, deleteAdminUser,
  getAdminPosts, deleteAdminPost, blindPost,
  getAdminNotices, createNotice, updateNotice, deleteNotice,
  getPlaceAggregates,
  getBlacklist, createBlacklistBrand, deleteBlacklistBrand,
  getCommunityPost,
} from '../api/gadang.js'

const tabs = [
  { key: 'dashboard', label: '대시보드' },
  { key: 'users', label: '회원' },
  { key: 'posts', label: '게시글' },
  { key: 'notices', label: '공지' },
  { key: 'blacklist', label: '블랙리스트' },
  { key: 'placeStats', label: '장소 데이터 집계' },
]

const tab = ref('dashboard')
const KPI = ref([
  { label: '가입 회원', value: '-', unit: '명' },
  { label: '생성 일정', value: '-', unit: '건' },
  { label: '코스 공유', value: '-', unit: '건' },
  { label: '등록 장소', value: '-', unit: '곳' },
])

const users = ref([])
const userPage = ref(1)
const userTotal = ref(0)
const userQuery = ref('')

async function loadUsers() {
  const res = await getAdminUsers(userPage.value, 20, userQuery.value.trim())
  users.value = res?.items ?? []
  userTotal.value = res?.totalCount ?? 0
}

async function searchUsers() {
  userPage.value = 1
  await loadUsers()
}

async function changeRole(userId, role) {
  if (!confirm(`권한을 ${role}로 변경할까요?`)) return
  await updateUserRole(userId, role)
  await loadUsers()
}

async function removeUser(userId, nick) {
  if (!confirm(`${nick} 회원을 삭제할까요? 복구할 수 없습니다.`)) return
  await deleteAdminUser(userId)
  await loadUsers()
}

const posts = ref([])
const postPage = ref(1)
const postTotal = ref(0)
const expandedPost = ref(null)
const expandedDetail = ref(null)

async function loadPosts() {
  const res = await getAdminPosts(postPage.value, 20)
  posts.value = res?.items ?? []
  postTotal.value = res?.totalCount ?? 0
}

async function removePost(postId, title) {
  if (!confirm(`"${title}" 게시글을 삭제할까요?`)) return
  await deleteAdminPost(postId)
  if (expandedPost.value === postId) { expandedPost.value = null; expandedDetail.value = null }
  await loadPosts()
}

async function toggleBlind(p) {
  const newBlind = !p.blinded
  await blindPost(p.postId, newBlind)
  p.blinded = newBlind
}

async function toggleDetail(postId) {
  if (expandedPost.value === postId) {
    expandedPost.value = null
    expandedDetail.value = null
    return
  }
  expandedPost.value = postId
  expandedDetail.value = null
  const d = await getCommunityPost(postId)
  if (expandedPost.value === postId) expandedDetail.value = d
}

const notices = ref([])
const noticePage = ref(1)
const noticeTotal = ref(0)
const noticeForm = ref({ noticeId: null, title: '', content: '' })
const showNoticeForm = ref(false)

async function loadNotices() {
  const res = await getAdminNotices(noticePage.value, 20)
  notices.value = res?.items ?? []
  noticeTotal.value = res?.totalCount ?? 0
}

function openNoticeForm(n = null) {
  noticeForm.value = n
    ? { noticeId: n.noticeId, title: n.title, content: n.content }
    : { noticeId: null, title: '', content: '' }
  showNoticeForm.value = true
}

async function saveNotice() {
  const { noticeId, title, content } = noticeForm.value
  if (!title.trim()) return alert('공지 제목을 입력해 주세요.')
  if (!content.trim()) return alert('공지 내용을 입력해 주세요.')
  if (noticeId) await updateNotice(noticeId, title, content)
  else await createNotice(title, content)
  showNoticeForm.value = false
  await loadNotices()
}

async function removeNotice(noticeId, title) {
  if (!confirm(`"${title}" 공지를 삭제할까요?`)) return
  await deleteNotice(noticeId)
  await loadNotices()
}

const blacklist = ref([])
const blackPage = ref(1)
const blackTotal = ref(0)
const newBrand = ref('')

async function loadBlacklist() {
  const res = await getBlacklist(blackPage.value, 20)
  blacklist.value = res?.items ?? []
  blackTotal.value = res?.totalCount ?? 0
}

async function addBrand() {
  if (!newBrand.value.trim()) return
  await createBlacklistBrand(newBrand.value.trim())
  newBrand.value = ''
  await loadBlacklist()
}

async function removeBrand(id, name) {
  if (!confirm(`"${name}"을 블랙리스트에서 삭제할까요?`)) return
  await deleteBlacklistBrand(id)
  await loadBlacklist()
}

const placeStats = ref([])
const placeStatsPage = ref(1)
const placeStatsTotal = ref(0)
const statFilters = ref({
  trimPercent: 10,
  minSamples: 1,
  minCost: '',
  maxCost: '',
  minDuration: '',
  maxDuration: '',
})

function cleanNumber(value) {
  if (value === '' || value == null) return undefined
  const n = Number(value)
  return Number.isFinite(n) ? n : undefined
}

async function loadPlaceStats() {
  const filters = {
    trimPercent: Number(statFilters.value.trimPercent) || 0,
    minSamples: Number(statFilters.value.minSamples) || 1,
    minCost: cleanNumber(statFilters.value.minCost),
    maxCost: cleanNumber(statFilters.value.maxCost),
    minDuration: cleanNumber(statFilters.value.minDuration),
    maxDuration: cleanNumber(statFilters.value.maxDuration),
  }
  const res = await getPlaceAggregates(placeStatsPage.value, 20, filters)
  placeStats.value = res?.items ?? []
  placeStatsTotal.value = res?.totalCount ?? 0
}

async function applyStatFilters() {
  placeStatsPage.value = 1
  await loadPlaceStats()
}

watch(tab, async (next) => {
  if (next === 'users') await loadUsers()
  if (next === 'posts') await loadPosts()
  if (next === 'notices') await loadNotices()
  if (next === 'blacklist') await loadBlacklist()
  if (next === 'placeStats') await loadPlaceStats()
})

onMounted(async () => {
  const data = await getAdminStats()
  if (data?.kpi) KPI.value = data.kpi
})

function fmtDate(iso) {
  if (!iso) return '-'
  return String(iso).slice(0, 10)
}

function fmtWon(value) {
  return `${Number(value || 0).toLocaleString('ko-KR')}원`
}
</script>

<template>
  <div class="screen-wrap" style="max-width:1240px">
    <div class="admin-tabs">
      <button
        v-for="t in tabs"
        :key="t.key"
        class="admin-tab"
        :class="{ 'admin-tab-on': tab === t.key }"
        @click="tab = t.key"
      >
        {{ t.label }}
      </button>
    </div>

    <div v-if="tab === 'dashboard'">
      <header class="cover" style="margin-bottom:22px">
        <div class="cover-meta">
          <span class="eyebrow">OPS · DASHBOARD</span>
        </div>
        <h1 class="disp" style="font-size:clamp(28px,4vw,52px)">
          관리자<br><span class="disp-i">운영 현황</span>
        </h1>
      </header>

      <div class="kpi-grid">
        <div v-for="k in KPI" :key="k.label" class="card kpi-card">
          <div class="kpi-label">{{ k.label }}</div>
          <div class="mono kpi-value">
            {{ typeof k.value === 'number' ? k.value.toLocaleString('ko-KR') : k.value }}
            <span>{{ k.unit }}</span>
          </div>
        </div>
      </div>

      <div class="card notice-help">
        회원 권한, 공지사항, 블랙리스트, 장소 데이터 집계를 이 페이지에서 관리합니다.
      </div>
    </div>

    <div v-else-if="tab === 'users'">
      <div class="rhead section-head">
        <div class="rt">
          <span class="n">MEMBERS</span>
          <h2>회원 관리</h2>
          <span class="count-text">총 {{ userTotal }}명</span>
        </div>
      </div>

      <div class="card filter-row">
        <input
          v-model="userQuery"
          placeholder="이메일 또는 닉네임 검색"
          @keyup.enter="searchUsers"
        />
        <button class="btn btn-primary btn-sm" @click="searchUsers">검색</button>
      </div>

      <div class="card table-card">
        <table class="admin-table wide">
          <thead>
            <tr>
              <th class="th">ID</th>
              <th class="th">닉네임</th>
              <th class="th">이메일</th>
              <th class="th">권한</th>
              <th class="th">가입일</th>
              <th class="th">액션</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="u in users" :key="u.userId">
              <td class="td mono">{{ u.userId }}</td>
              <td class="td strong">{{ u.nickname }}</td>
              <td class="td mono muted">{{ u.email || '-' }}</td>
              <td class="td">
                <span class="badge" :class="{ admin: u.role === 'ADMIN' }">{{ u.role }}</span>
              </td>
              <td class="td mono muted">{{ fmtDate(u.createdAt) }}</td>
              <td class="td">
                <div class="actions">
                  <button class="btn btn-outline btn-sm" @click="changeRole(u.userId, u.role === 'ADMIN' ? 'USER' : 'ADMIN')">
                    {{ u.role === 'ADMIN' ? 'USER로 변경' : 'ADMIN 부여' }}
                  </button>
                  <button class="btn btn-sm danger-btn" @click="removeUser(u.userId, u.nickname)">삭제</button>
                </div>
              </td>
            </tr>
            <tr v-if="users.length === 0">
              <td colspan="6" class="empty">회원 데이터가 없습니다.</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div v-else-if="tab === 'posts'">
      <div class="rhead section-head">
        <div class="rt">
          <span class="n">POSTS</span>
          <h2>게시글 관리</h2>
          <span class="count-text">총 {{ postTotal }}건</span>
        </div>
      </div>
      <div class="card table-card">
        <table class="admin-table wide">
          <thead>
            <tr>
              <th class="th">ID</th>
              <th class="th">제목</th>
              <th class="th">작성자</th>
              <th class="th">작성일</th>
              <th class="th">댓글</th>
              <th class="th">상태</th>
              <th class="th">액션</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="p in posts" :key="p.postId">
              <tr :style="p.blinded ? 'opacity:.55' : ''">
                <td class="td mono">{{ p.postId }}</td>
                <td class="td ellipsis">
                  <button style="background:none;border:none;padding:0;cursor:pointer;text-align:left;font-weight:600;color:var(--accent);font-size:13px"
                          @click="toggleDetail(p.postId)">
                    {{ p.title }}
                  </button>
                </td>
                <td class="td">{{ p.authorNickname }}</td>
                <td class="td mono muted">{{ fmtDate(p.createdAt) }}</td>
                <td class="td mono">{{ p.commentCount ?? 0 }}</td>
                <td class="td">
                  <span v-if="p.blinded" style="font-size:11px;font-weight:600;color:var(--ink-faint);background:var(--bg);padding:2px 7px;border-radius:4px">블라인드</span>
                  <span v-else style="font-size:11px;color:var(--ink-faint)">공개</span>
                </td>
                <td class="td">
                  <div style="display:flex;gap:6px">
                    <button class="btn btn-sm btn-outline"
                            :style="p.blinded ? 'color:var(--accent-deep)' : 'color:var(--ink-soft)'"
                            @click="toggleBlind(p)">
                      {{ p.blinded ? '해제' : '블라인드' }}
                    </button>
                    <button class="btn btn-sm danger-btn" @click="removePost(p.postId, p.title)">삭제</button>
                  </div>
                </td>
              </tr>
              <!-- 상세 펼침 -->
              <tr v-if="expandedPost === p.postId">
                <td colspan="7" style="padding:0">
                  <div style="padding:16px 20px;background:var(--bg);border-top:1px solid var(--line);font-size:13px">
                    <div v-if="!expandedDetail" style="color:var(--ink-faint);text-align:center;padding:12px">불러오는 중…</div>
                    <template v-else>
                      <div style="display:flex;gap:16px;flex-wrap:wrap;margin-bottom:10px">
                        <span v-if="expandedDetail.intro" style="color:var(--ink-soft)"><b>머리말:</b> {{ expandedDetail.intro }}</span>
                        <span><b>총 비용:</b> {{ (expandedDetail.totalCost ?? 0).toLocaleString() }}원</span>
                        <span><b>소요시간:</b> {{ expandedDetail.totalDurationMin ?? 0 }}분</span>
                      </div>
                      <div style="white-space:pre-wrap;color:var(--ink);line-height:1.6;max-height:220px;overflow-y:auto;padding:10px 12px;background:var(--card);border-radius:8px">{{ expandedDetail.content || '(내용 없음)' }}</div>
                      <div v-if="expandedDetail.outro" style="margin-top:8px;color:var(--ink-soft)"><b>꼬리말:</b> {{ expandedDetail.outro }}</div>
                    </template>
                  </div>
                </td>
              </tr>
            </template>
            <tr v-if="posts.length === 0">
              <td colspan="7" class="empty">게시글이 없습니다.</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div v-else-if="tab === 'notices'">
      <div class="rhead section-head">
        <div class="rt">
          <span class="n">NOTICE</span>
          <h2>공지사항 관리</h2>
          <span class="count-text">총 {{ noticeTotal }}건</span>
        </div>
        <button class="btn btn-primary btn-sm" @click="openNoticeForm()">+ 공지 작성</button>
      </div>

      <div v-if="showNoticeForm" class="card edit-box">
        <div class="form-title">{{ noticeForm.noticeId ? '공지 수정' : '공지 작성' }}</div>
        <input v-model="noticeForm.title" placeholder="제목" />
        <textarea v-model="noticeForm.content" placeholder="내용" rows="4" />
        <div class="actions">
          <button class="btn btn-primary btn-sm" @click="saveNotice">저장</button>
          <button class="btn btn-outline btn-sm" @click="showNoticeForm = false">취소</button>
        </div>
      </div>

      <div class="card table-card">
        <table class="admin-table wide">
          <thead>
            <tr>
              <th class="th">ID</th>
              <th class="th">제목</th>
              <th class="th">작성자</th>
              <th class="th">작성일</th>
              <th class="th">액션</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="n in notices" :key="n.noticeId">
              <td class="td mono">{{ n.noticeId }}</td>
              <td class="td ellipsis">{{ n.title }}</td>
              <td class="td">{{ n.authorNickname }}</td>
              <td class="td mono muted">{{ fmtDate(n.createdAt) }}</td>
              <td class="td">
                <div class="actions">
                  <button class="btn btn-outline btn-sm" @click="openNoticeForm(n)">수정</button>
                  <button class="btn btn-sm danger-btn" @click="removeNotice(n.noticeId, n.title)">삭제</button>
                </div>
              </td>
            </tr>
            <tr v-if="notices.length === 0">
              <td colspan="5" class="empty">공지사항이 없습니다.</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div v-else-if="tab === 'blacklist'">
      <div class="rhead section-head">
        <div class="rt">
          <span class="n">BLACKLIST</span>
          <h2>블랙리스트 관리</h2>
          <span class="count-text">총 {{ blackTotal }}건</span>
        </div>
      </div>
      <div class="card filter-row">
        <input
          v-model="newBrand"
          placeholder="브랜드명 입력 예: 스타벅스"
          @keyup.enter="addBrand"
        />
        <button class="btn btn-primary btn-sm" @click="addBrand">추가</button>
      </div>
      <div class="card table-card">
        <table class="admin-table">
          <thead>
            <tr>
              <th class="th">ID</th>
              <th class="th">브랜드명</th>
              <th class="th">액션</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="b in blacklist" :key="b.id">
              <td class="td mono">{{ b.id }}</td>
              <td class="td strong">{{ b.brandName }}</td>
              <td class="td">
                <button class="btn btn-sm danger-btn" @click="removeBrand(b.id, b.brandName)">삭제</button>
              </td>
            </tr>
            <tr v-if="blacklist.length === 0">
              <td colspan="3" class="empty">블랙리스트가 비어 있습니다.</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div v-else-if="tab === 'placeStats'">
      <div class="rhead section-head">
        <div class="rt">
          <span class="n">PLACE METRICS</span>
          <h2>장소 데이터 집계</h2>
          <span class="count-text">총 {{ placeStatsTotal }}곳</span>
        </div>
      </div>

      <div class="card stats-help">
        저장된 일정과 커뮤니티 후기의 장소 비용·체류시간 데이터를 합쳐 평균을 냅니다.
        컷오프는 낮은 값과 높은 값을 같은 비율로 제외하고, 차단 범위는 허용 범위 밖의 샘플을 제외합니다.
      </div>

      <div class="card stat-controls">
        <label>
          상/하위 컷오프(%)
          <input v-model.number="statFilters.trimPercent" type="number" min="0" max="40" />
        </label>
        <label>
          최소 샘플 수
          <input v-model.number="statFilters.minSamples" type="number" min="1" />
        </label>
        <label>
          최소 금액
          <input v-model="statFilters.minCost" type="number" min="0" placeholder="없음" />
        </label>
        <label>
          최대 금액
          <input v-model="statFilters.maxCost" type="number" min="0" placeholder="없음" />
        </label>
        <label>
          최소 시간(분)
          <input v-model="statFilters.minDuration" type="number" min="0" placeholder="없음" />
        </label>
        <label>
          최대 시간(분)
          <input v-model="statFilters.maxDuration" type="number" min="0" placeholder="없음" />
        </label>
        <button class="btn btn-primary btn-sm" @click="applyStatFilters">적용</button>
      </div>

      <div class="card table-card">
        <table class="admin-table wide">
          <thead>
            <tr>
              <th class="th">장소</th>
              <th class="th">카테고리</th>
              <th class="th">샘플</th>
              <th class="th">평균 금액</th>
              <th class="th">금액 범위</th>
              <th class="th">평균 소요</th>
              <th class="th">시간 범위</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="p in placeStats" :key="`${p.placeId || 'name'}-${p.placeName}`">
              <td class="td strong">
                {{ p.placeName || '이름 없음' }}
              </td>
              <td class="td muted">{{ p.categoryName || p.categoryCode || '-' }}</td>
              <td class="td mono">{{ p.sampleCount }}개</td>
              <td class="td mono">{{ fmtWon(p.averageCost) }}</td>
              <td class="td mono muted">{{ fmtWon(p.minCost) }} ~ {{ fmtWon(p.maxCost) }}</td>
              <td class="td mono">{{ p.averageDurationMin || 0 }}분</td>
              <td class="td mono muted">{{ p.minDurationMin || 0 }} ~ {{ p.maxDurationMin || 0 }}분</td>
            </tr>
            <tr v-if="placeStats.length === 0">
              <td colspan="7" class="empty">집계할 장소 데이터가 없습니다.</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<style scoped>
.admin-tabs {
  display: flex;
  gap: 0;
  margin-bottom: 22px;
  border-bottom: 1px solid var(--ink);
  overflow-x: auto;
}
.admin-tab {
  flex: 0 0 auto;
  padding: 10px 14px;
  font-size: 14px;
  font-weight: 600;
  color: var(--ink-faint);
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
}
.admin-tab-on {
  color: var(--ink);
  border-bottom-color: var(--accent);
}
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 14px;
  margin-bottom: 24px;
}
.kpi-card {
  padding: 18px 20px;
}
.kpi-label {
  font-size: 12.5px;
  color: var(--ink-faint);
  font-weight: 600;
  margin-bottom: 8px;
}
.kpi-value {
  font-size: 28px;
  font-weight: 700;
}
.kpi-value span {
  font-size: 14px;
  margin-left: 2px;
}
.notice-help,
.stats-help {
  padding: 18px 20px;
  font-size: 13px;
  color: var(--ink-soft);
}
.section-head {
  margin-bottom: 16px;
}
.count-text {
  font-size: 13px;
  color: var(--ink-faint);
  align-self: center;
}
.filter-row {
  display: flex;
  gap: 8px;
  padding: 16px;
  margin-bottom: 14px;
}
.filter-row input,
.edit-box input,
.edit-box textarea,
.stat-controls input {
  border: 1px solid var(--line);
  border-radius: 6px;
  padding: 9px 12px;
  font-size: 14px;
  background: var(--bg);
  color: var(--ink);
  box-sizing: border-box;
}
.filter-row input {
  flex: 1;
}
.table-card {
  overflow: auto;
}
.admin-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13.5px;
}
.admin-table.wide {
  min-width: 680px;
}
.admin-table tr {
  border-bottom: 1px solid var(--line);
}
.admin-table thead tr {
  border-bottom: 1px solid var(--ink);
}
.th {
  text-align: left;
  padding: 8px 10px 10px;
  font-size: 11.5px;
  font-weight: 600;
  color: var(--ink-faint);
  font-family: var(--mono);
  letter-spacing: 0.04em;
  white-space: nowrap;
}
.td {
  padding: 10px;
  vertical-align: middle;
}
.strong {
  font-weight: 600;
}
.muted {
  color: var(--ink-soft);
}
.ellipsis {
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.badge.admin {
  background: var(--accent-wash);
  color: var(--accent-deep);
}
.actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.danger-btn {
  color: var(--trend);
  border: 1px solid var(--trend);
}
.empty {
  padding: 30px;
  text-align: center;
  color: var(--ink-faint);
}
.edit-box {
  padding: 18px 20px;
  margin-bottom: 16px;
  border: 1.5px solid var(--accent);
}
.form-title {
  font-size: 14px;
  font-weight: 700;
  margin-bottom: 12px;
}
.edit-box input,
.edit-box textarea {
  width: 100%;
  margin-bottom: 10px;
}
.edit-box textarea {
  resize: vertical;
}
.stat-controls {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
  gap: 12px;
  padding: 16px;
  margin-bottom: 14px;
  align-items: end;
}
.stat-controls label {
  display: grid;
  gap: 5px;
  min-width: 0;
  font-size: 12px;
  color: var(--ink-soft);
  font-weight: 600;
  line-height: 1.35;
  overflow-wrap: anywhere;
}
.stat-controls input {
  width: 100%;
  min-width: 0;
}
.stat-controls .btn {
  width: 100%;
  min-height: 38px;
  justify-content: center;
}
@media (max-width: 720px) {
  .stat-controls {
    grid-template-columns: 1fr 1fr;
  }
}
@media (max-width: 460px) {
  .stat-controls {
    grid-template-columns: 1fr;
  }
}
</style>
