// 앱 전역 공유 GPS 위치 캐시 — 탭(Home/Map/Course)마다 따로 측위하면
// 콜드 진입과 웜 진입의 결과가 달라지는 문제가 생기므로 단일 캐시·단일 진행중 요청으로 통일한다.
const CACHE_KEY = 'gadang_gps'
const TTL = 600000 // 10분

let inflight = null

function readCache() {
  try {
    const c = JSON.parse(sessionStorage.getItem(CACHE_KEY) || 'null')
    if (c && Date.now() - c.t < TTL) return c
  } catch { /* 캐시 손상 시 무시 */ }
  return null
}

function writeCache(coord, name) {
  const entry = { c: coord, t: Date.now(), n: name || '' }
  sessionStorage.setItem(CACHE_KEY, JSON.stringify(entry))
  return entry
}

/**
 * GPS 위치 선요청 — 페이지 진입 즉시 호출해 이후 탭 전환 시 대기시간을 없앤다.
 * 캐시가 신선하면 즉시 반환, 진행 중인 요청이 있으면 그 결과를 공유, 없으면 새로 측위.
 * 반환값: { c: [lat, lng], t: timestamp, n: 역지오코딩 이름(없으면 '') } | null
 */
export function requestLocation() {
  const cached = readCache()
  if (cached) return Promise.resolve(cached)
  if (inflight) return inflight
  if (!navigator.geolocation) return Promise.resolve(null)

  inflight = new Promise(resolve => {
    navigator.geolocation.getCurrentPosition(
      pos => {
        const entry = writeCache([pos.coords.latitude, pos.coords.longitude], '')
        inflight = null
        resolve(entry)
      },
      () => { inflight = null; resolve(null) },
      { enableHighAccuracy: false, timeout: 8000, maximumAge: 600000 },
    )
  })
  return inflight
}

export function getCachedLocation() {
  return readCache()
}

/** 역지오코딩 등으로 얻은 위치명을 캐시에 보강 — 다른 탭이 재조회하지 않도록 공유 */
export function setLocationName(name) {
  const cached = readCache()
  if (cached) writeCache(cached.c, name)
}
