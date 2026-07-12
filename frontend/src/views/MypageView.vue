<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '../components/ui/AppIcon.vue'
import CatDot  from '../components/ui/CatDot.vue'
import Money   from '../components/ui/Money.vue'
import { won, ME as MOCK_ME, PLACES, CATS } from '../data/mock.js'
import { getMe, updateProfile } from '../api/gadang.js'
import { useAuth } from '../composables/useAuth.js'

const router = useRouter()
const { isLoggedIn, user, logout, updateUser } = useAuth()

const tab    = ref('trips')
const me     = ref(MOCK_ME)
const editProfileOpen = ref(false)
const editNick  = ref('')
const editEmail = ref('')
const editRegion = ref('')

onMounted(async () => {
  const data = await getMe()
  if (data) me.value = data
  if (user.value) {
    editNick.value   = user.value.nickname || me.value.nick
    editEmail.value  = user.value.email    || me.value.email
    editRegion.value = user.value.region   || me.value.region
  }
})

const favPlaces = computed(() => PLACES.filter(p => me.value.favorites.includes(p.id)))

const displayNick = computed(() =>
  user.value?.nickname || me.value.nick
)
const displayEmail = computed(() =>
  user.value?.email || me.value.email
)
const avatarLabel = computed(() =>
  displayNick.value.charAt(0)
)

// 일정 상태별 필터
const tripFilter = ref('all')
const TRIP_FILTERS = [
  { k: 'all',  l: '전체' },
  { k: '예정', l: '예정' },
  { k: '저장', l: '완료' },
]
const filteredTrips = computed(() => {
  if (tripFilter.value === 'all') return me.value.trips
  return me.value.trips.filter(t => t.status === tripFilter.value)
})

const saveProfileLoading = ref(false)
const saveProfileError   = ref('')

async function saveProfile() {
  saveProfileLoading.value = true
  saveProfileError.value   = ''
  try {
    const res = await updateProfile(editNick.value, editEmail.value, editRegion.value)
    if (res?.success) {
      // 로컬 상태 업데이트
      updateUser({ nickname: editNick.value, email: editEmail.value })
      me.value.nick   = editNick.value
      me.value.email  = editEmail.value
      me.value.region = editRegion.value
      editProfileOpen.value = false
    } else {
      // fallback: 백엔드 없어도 로컬에서는 반영
      updateUser({ nickname: editNick.value })
      me.value.nick   = editNick.value
      me.value.email  = editEmail.value
      me.value.region = editRegion.value
      editProfileOpen.value = false
    }
  } catch (e) {
    saveProfileError.value = e?.response?.data?.message || '저장에 실패했습니다.'
  } finally {
    saveProfileLoading.value = false
  }
}

function deleteTrip(id) {
  me.value.trips = me.value.trips.filter(t => t.id !== id)
}

function handleLogout() {
  logout()
  router.push('/login')
}
</script>

<template>
  <div class="screen-wrap" style="max-width:1240px">

    <!-- 비로그인 -->
    <div v-if="!isLoggedIn" style="padding:60px 0;text-align:center">
      <div style="font-size:40px;margin-bottom:16px">🔐</div>
      <div style="font-size:20px;font-weight:700;margin-bottom:8px">로그인이 필요해요</div>
      <div style="font-size:14px;color:var(--ink-soft);margin-bottom:24px">내 여행 일정과 즐겨찾기를 확인하려면 로그인하세요.</div>
      <div style="display:flex;gap:10px;justify-content:center">
        <button class="btn btn-outline" @click="router.push('/login')">로그인</button>
        <button class="btn btn-primary" @click="router.push('/signup')">회원가입</button>
      </div>
    </div>

    <template v-else>
      <header class="cover" style="margin-bottom:22px">
        <div class="cover-meta">
          <span class="eyebrow">MEMBER · 2026</span>
          <span class="coords">가입 {{ me.joined }} · {{ me.region }}</span>
        </div>
        <h1 class="disp" style="font-size:clamp(34px,5vw,60px)">
          my.<span class="disp-i">{{ displayNick }}</span>
        </h1>
        <p class="lede">내 여행 일정·즐겨찾기·작성 게시글을 한곳에서.</p>
      </header>

      <!-- 프로필 카드 -->
      <div class="ed-card" style="overflow:hidden;margin-bottom:18px">
        <div style="height:5px;background:var(--accent)" />
        <div style="padding:20px 22px;display:flex;align-items:center;gap:16px;flex-wrap:wrap">
          <span class="avatar" style="width:56px;height:56px;font-size:22px;border-radius:14px;background:var(--accent-wash);color:var(--accent-deep);display:flex;align-items:center;justify-content:center;flex-shrink:0">
            {{ avatarLabel }}
          </span>
          <div style="flex:1;min-width:180px">
            <div style="display:flex;align-items:center;gap:9px">
              <span style="font-size:20px;font-weight:700">{{ displayNick }}</span>
              <span class="badge badge-accent">회원</span>
            </div>
            <div style="display:flex;gap:14px;margin-top:6px;font-size:12.5px;color:var(--ink-soft);flex-wrap:wrap">
              <span class="mono">{{ displayEmail }}</span>
              <span style="display:inline-flex;align-items:center;gap:4px">
                <AppIcon name="pin" style="width:13px;height:13px" />{{ me.region }}
              </span>
            </div>
          </div>
          <div style="display:flex;gap:8px">
            <button class="btn btn-outline btn-sm" @click="editProfileOpen = true">프로필 수정</button>
            <button class="btn btn-ghost btn-sm" style="color:var(--ink-faint)" @click="handleLogout">로그아웃</button>
          </div>
        </div>

        <hr style="border:none;border-top:1.5px dashed var(--line);margin:0" />

        <div style="display:flex;padding:16px 22px;gap:12px;flex-wrap:wrap">
          <div style="flex:1;min-width:0;text-align:center">
            <div style="font-size:11px;color:var(--ink-faint);font-weight:600;margin-bottom:4px">여행 횟수</div>
            <div class="mono" style="font-size:20px;font-weight:700">{{ me.stats.trips }}회</div>
          </div>
          <span style="width:1px;align-self:stretch;background:var(--line)" />
          <div style="flex:1;min-width:0;text-align:center">
            <div style="font-size:11px;color:var(--ink-faint);font-weight:600;margin-bottom:4px">총 여행 비용</div>
            <div class="mono" style="font-size:20px;font-weight:700">{{ won(me.stats.spent) }}<span style="font-size:12px">원</span></div>
          </div>
          <span style="width:1px;align-self:stretch;background:var(--line)" />
          <div style="flex:1;min-width:0;text-align:center">
            <div style="font-size:11px;color:var(--ink-faint);font-weight:600;margin-bottom:4px">즐겨찾기</div>
            <div class="mono" style="font-size:20px;font-weight:700">{{ me.stats.favorites }}<span style="font-size:12px">곳</span></div>
          </div>
          <span style="width:1px;align-self:stretch;background:var(--line)" />
          <div style="flex:1;min-width:0;text-align:center">
            <div style="font-size:11px;color:var(--ink-faint);font-weight:600;margin-bottom:4px">공유 코스</div>
            <div class="mono" style="font-size:20px;font-weight:700">{{ me.stats.shared }}개</div>
          </div>
        </div>
      </div>

      <!-- 탭 -->
      <div style="display:flex;gap:6px;margin-bottom:16px;border-bottom:1px solid var(--line)">
        <button v-for="[k, l, n] in [['trips','내 여행 일정',me.trips.length],['favs','즐겨찾기',favPlaces.length],['posts','작성 게시글',me.posts.length]]"
                :key="k" @click="tab = k"
                class="my-tab" :class="{ 'my-tab-on': tab === k }">
          {{ l }} <span class="mono" style="font-size:12px">{{ n }}</span>
        </button>
      </div>

      <!-- 여행 일정 탭 -->
      <div v-if="tab === 'trips'">
        <!-- 상태 필터 + 새 일정 버튼 -->
        <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:12px;flex-wrap:wrap;gap:10px">
          <div style="display:flex;gap:6px">
            <button v-for="f in TRIP_FILTERS" :key="f.k"
                    class="chip" :style="tripFilter === f.k ? { background:'var(--accent-wash)', borderColor:'var(--accent)', color:'var(--accent-deep)', fontWeight:'600' } : {}"
                    @click="tripFilter = f.k">{{ f.l }}</button>
          </div>
          <button class="btn btn-primary btn-sm" style="border-radius:6px" @click="router.push('/course')">
            <AppIcon name="plus" style="width:14px;height:14px" /> 새 일정 만들기
          </button>
        </div>

        <div v-if="!filteredTrips.length" style="padding:36px;text-align:center;color:var(--ink-faint);font-size:13px">
          일정이 없습니다.
          <button class="btn btn-ghost" style="display:block;margin:12px auto 0" @click="router.push('/course')">코스 짜러 가기 →</button>
        </div>

        <div style="display:flex;flex-direction:column;gap:10px">
          <div v-for="t in filteredTrips" :key="t.id"
               class="card rise" style="padding:14px 16px;display:flex;align-items:center;gap:14px">
            <!-- 날짜 -->
            <div style="text-align:center;flex-shrink:0;width:44px">
              <div class="mono" style="font-size:18px;font-weight:700;letter-spacing:-0.03em">{{ String(t.date).slice(5, 7) }}</div>
              <div class="mono" style="font-size:10px;color:var(--ink-faint)">{{ String(t.date).slice(0, 4) }}</div>
            </div>
            <span style="width:1px;height:36px;background:var(--line)" />
            <!-- 정보 -->
            <div style="flex:1;min-width:0;cursor:pointer" @click="router.push('/trip/' + t.id)">
              <div style="font-size:14.5px;font-weight:600">{{ t.title }}</div>
              <div style="display:flex;align-items:center;gap:8px;margin-top:4px;font-size:12px;color:var(--ink-faint)">
                <span style="display:inline-flex;align-items:center;gap:3px">
                  <AppIcon name="pin" style="width:12px;height:12px" />{{ t.region }}
                </span>
                <span>·</span>
                <span class="mono">{{ won(t.cost) }}원</span>
              </div>
            </div>
            <!-- 상태 뱃지 -->
            <span class="badge" :style="{ background: t.status === '예정' ? 'var(--accent-wash)' : 'var(--card-sunken)', color: t.status === '예정' ? 'var(--accent-deep)' : 'var(--ink-faint)' }">
              {{ t.status }}
            </span>
            <!-- 액션 버튼들 -->
            <div style="display:flex;gap:6px">
              <button class="btn btn-ghost btn-sm" style="padding:5px 8px" title="일정 보기"
                      @click="router.push('/trip/' + t.id)">
                <AppIcon name="receipt" style="width:14px;height:14px" />
              </button>
              <button class="btn btn-ghost btn-sm" style="padding:5px 8px;color:var(--trend)" title="삭제"
                      @click="deleteTrip(t.id)">
                <AppIcon name="trash" style="width:14px;height:14px" />
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 즐겨찾기 탭 -->
      <div v-if="tab === 'favs'">
        <div v-if="!favPlaces.length" style="padding:36px;text-align:center;color:var(--ink-faint);font-size:13px">
          즐겨찾기한 장소가 없습니다.
          <button class="btn btn-ghost" style="display:block;margin:12px auto 0" @click="router.push('/map')">장소 찾으러 가기 →</button>
        </div>
        <div style="display:grid;grid-template-columns:repeat(auto-fill,minmax(240px,1fr));gap:12px">
          <div v-for="p in favPlaces" :key="p.id"
               class="card rise" style="padding:13px;display:flex;gap:11px;align-items:center">
            <CatDot :cat="p.cat" :size="40" />
            <div style="flex:1;min-width:0">
              <div style="font-size:14px;font-weight:600">{{ p.name }}</div>
              <div style="font-size:11.5px;color:var(--ink-faint);margin-top:3px">{{ CATS[p.cat]?.ko }}</div>
            </div>
            <Money :amount="p.fee" :type="p.feeType" :size="13" />
          </div>
          <button class="card" style="padding:13px;display:flex;gap:9px;align-items:center;justify-content:center;color:var(--ink-faint);font-size:13px;font-weight:600;border:1.5px dashed var(--line-strong);cursor:pointer"
                  @click="router.push('/map')">
            <AppIcon name="plus" style="width:16px;height:16px" /> 장소 더 찾기
          </button>
        </div>
      </div>

      <!-- 게시글 탭 -->
      <div v-if="tab === 'posts'">
        <div v-if="!me.posts.length" style="padding:36px;text-align:center;color:var(--ink-faint);font-size:13px">
          작성한 게시글이 없습니다.
          <button class="btn btn-ghost" style="display:block;margin:12px auto 0" @click="router.push('/community')">커뮤니티 가기 →</button>
        </div>
        <div style="display:flex;flex-direction:column;gap:10px">
          <div v-for="p in me.posts" :key="p.id"
               class="card rise" style="padding:14px 16px;display:flex;align-items:center;gap:14px;cursor:pointer"
               @click="router.push('/community')">
            <AppIcon name="flag" style="width:18px;height:18px;color:var(--accent-deep);flex-shrink:0" />
            <div style="flex:1;min-width:0;font-size:14.5px;font-weight:600">{{ p.title }}</div>
            <span style="display:inline-flex;align-items:center;gap:4px;font-size:12.5px;color:var(--ink-faint)">
              <AppIcon name="heart" style="width:14px;height:14px" />{{ p.likes }}
            </span>
            <span style="display:inline-flex;align-items:center;gap:4px;font-size:12.5px;color:var(--ink-faint)">
              <AppIcon name="comment" style="width:14px;height:14px" />{{ p.comments }}
            </span>
          </div>
          <button class="btn btn-ghost" style="align-self:flex-start" @click="router.push('/community')">
            <AppIcon name="plus" style="width:16px;height:16px" /> 새 코스 공유하기
          </button>
        </div>
      </div>
    </template>

    <!-- 프로필 수정 모달 -->
    <div v-if="editProfileOpen"
         style="position:fixed;inset:0;background:rgba(0,0,0,.45);z-index:200;display:flex;align-items:center;justify-content:center;padding:24px"
         @click.self="editProfileOpen = false">
      <div class="card" style="width:100%;max-width:420px;padding:28px 24px;display:flex;flex-direction:column;gap:16px">
        <div style="font-size:17px;font-weight:700">프로필 수정</div>

        <label style="display:flex;flex-direction:column;gap:6px">
          <span style="font-size:12px;font-weight:600;color:var(--ink-faint)">닉네임</span>
          <input v-model="editNick" style="border:1.5px solid var(--line-strong);border-radius:8px;padding:10px 14px;font-size:15px;background:var(--bg);outline:none;width:100%;box-sizing:border-box" />
        </label>
        <label style="display:flex;flex-direction:column;gap:6px">
          <span style="font-size:12px;font-weight:600;color:var(--ink-faint)">이메일</span>
          <input v-model="editEmail" type="email" style="border:1.5px solid var(--line-strong);border-radius:8px;padding:10px 14px;font-size:15px;background:var(--bg);outline:none;width:100%;box-sizing:border-box" />
        </label>
        <label style="display:flex;flex-direction:column;gap:6px">
          <span style="font-size:12px;font-weight:600;color:var(--ink-faint)">지역</span>
          <input v-model="editRegion" placeholder="서울 마포구" style="border:1.5px solid var(--line-strong);border-radius:8px;padding:10px 14px;font-size:15px;background:var(--bg);outline:none;width:100%;box-sizing:border-box" />
        </label>

        <div v-if="saveProfileError" style="font-size:12.5px;color:var(--trend);text-align:center;margin-top:-4px">{{ saveProfileError }}</div>
        <div style="display:flex;gap:8px;margin-top:4px">
          <button class="btn btn-outline" style="flex:1" @click="editProfileOpen = false">취소</button>
          <button class="btn btn-primary" style="flex:1" :disabled="saveProfileLoading" @click="saveProfile">
            {{ saveProfileLoading ? '저장 중…' : '저장' }}
          </button>
        </div>
      </div>
    </div>

  </div>
</template>

<style scoped>
.my-tab {
  padding: 10px 4px; margin-right: 16px; font-size: 14.5px; font-weight: 600;
  color: var(--ink-faint); border-bottom: 2px solid transparent; margin-bottom: -1px;
}
.my-tab-on { color: var(--accent-deep); border-bottom-color: var(--accent); }
</style>
