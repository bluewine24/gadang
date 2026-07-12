export const won = (n) => Number(n).toLocaleString('ko-KR')

export const CATS = {
  sight:   { ko: '관광명소', emoji: '◎', stay: 60 },
  park:    { ko: '공원·산책', emoji: '⬡', stay: 50 },
  photo:   { ko: '포토스팟', emoji: '◇', stay: 30 },
  culture: { ko: '문화·전시', emoji: '▤', stay: 80 },
  cafe:    { ko: '카페',     emoji: '○', stay: 45 },
  food:    { ko: '음식점',   emoji: '◐', stay: 55 },
  shop:    { ko: '쇼핑',    emoji: '▢', stay: 40 },
}

export const CAT_HUE = {
  sight:   'var(--accent)',
  park:    'var(--free)',
  photo:   'var(--trend)',
  culture: 'var(--transit)',
  cafe:    'var(--accent-deep)',
  food:    'var(--trend)',
  shop:    'var(--ink-soft)',
}

export const REGIONS = [
  { id: 'gangneung', name: '강릉', sido: '강원', trend: 92, roundTrip: 200, fare: 27400, stay: 360, transport: 'KTX', tags: ['바다', '카페거리', '드라이브'], blurb: '경포 바다 + 안목 커피거리. 당일치기 인기 1위', hot: true },
  { id: 'incheon',   name: '인천 개항장', sido: '인천', trend: 78, roundTrip: 95, fare: 5800, stay: 405, transport: '지하철', tags: ['근대건축', '차이나타운', '도보'], blurb: '걸어서 도는 근대 거리 + 월미도 노을', hot: true },
  { id: 'suwon',    name: '수원 화성', sido: '경기', trend: 71, roundTrip: 70, fare: 4200, stay: 430, transport: '지하철', tags: ['성곽', '야경', '저예산'], blurb: '성곽 한 바퀴 + 통닭거리. 입장료 거의 무료' },
  { id: 'chuncheon',name: '춘천', sido: '강원', trend: 66, roundTrip: 160, fare: 8600, stay: 380, transport: 'ITX', tags: ['호수', '닭갈비', '레일바이크'], blurb: '의암호 자전거길 + 명동 닭갈비골목' },
  { id: 'jeonju',   name: '전주 한옥마을', sido: '전북', trend: 84, roundTrip: 230, fare: 34200, stay: 330, transport: 'KTX', tags: ['한옥', '맛집', '전통'], blurb: '한옥 골목 + 가맥집. 먹거리 끝판왕', hot: true },
  { id: 'asan',     name: '아산 외암마을', sido: '충남', trend: 52, roundTrip: 150, fare: 11800, stay: 390, transport: '무궁화', tags: ['민속마을', '온천', '한적'], blurb: '돌담 민속마을 + 온양온천 족욕' },
]

export const PLACES = [
  { id: 'p1', name: '경포해변',       cat: 'sight',   addr: '강릉시 창해로 514',              fee: 0,     feeType: 'free',     trend: 88, dist: '0.0km', lat: 37.795, lng: 128.913 },
  { id: 'p2', name: '안목 커피거리',   cat: 'cafe',    addr: '강릉시 창해로14번길',             fee: 7000,  feeType: 'estimate', trend: 91, dist: '2.1km', lat: 37.773, lng: 128.947 },
  { id: 'p3', name: '오죽헌',         cat: 'culture', addr: '강릉시 율곡로3139번길 24',        fee: 3000,  feeType: 'confirm',  trend: 64, dist: '4.8km', lat: 37.779, lng: 128.878 },
  { id: 'p4', name: '강문해변 포토존', cat: 'photo',   addr: '강릉시 창해로350번길',            fee: 0,     feeType: 'free',     trend: 79, dist: '1.4km', lat: 37.801, lng: 128.918 },
  { id: 'p5', name: '초당 순두부마을', cat: 'food',    addr: '강릉시 초당순두부길',             fee: 11000, feeType: 'estimate', trend: 73, dist: '3.2km', lat: 37.789, lng: 128.905 },
  { id: 'p6', name: '경포호 둘레길',  cat: 'park',    addr: '강릉시 경포로',                  fee: 0,     feeType: 'free',     trend: 58, dist: '1.0km', lat: 37.791, lng: 128.901 },
  { id: 'p7', name: '강릉중앙시장',   cat: 'shop',    addr: '강릉시 금성로 21',               fee: 8000,  feeType: 'estimate', trend: 70, dist: '5.1km', lat: 37.756, lng: 128.898 },
  { id: 'p8', name: '하슬라아트월드', cat: 'culture', addr: '강릉시 율곡로 1441',              fee: 12000, feeType: 'confirm',  trend: 61, dist: '12.4km',lat: 37.696, lng: 129.034 },
]

export const COURSE = {
  title: '강릉 바다 + 커피 당일치기',
  region: '강릉', date: '2026-06-06 (토)', startPoint: '서울역',
  startTime: '08:00', endTime: '20:00', totalMin: 360, placeCount: 5,
  items: [
    { type: 'transit', mode: 'KTX',  from: '서울역',  to: '강릉역',    dep: '08:00', arr: '09:54', min: 114, fare: 13700, feeType: 'confirm' },
    { type: 'place',  pid: 'p1', name: '경포해변',       cat: 'sight',   arr: '10:20', stay: 60,  fee: 0,     feeType: 'free',     note: '바다 산책 + 인증샷' },
    { type: 'transit', mode: '버스', from: '경포해변',  to: '강문해변',  dep: '11:20', arr: '11:34', min: 14, fare: 1500,  feeType: 'confirm' },
    { type: 'place',  pid: 'p4', name: '강문해변 포토존', cat: 'photo',   arr: '11:34', stay: 30,  fee: 0,     feeType: 'free',     note: '강문 솟대다리 포토존' },
    { type: 'place',  pid: 'p5', name: '초당 순두부마을', cat: 'food',    arr: '12:20', stay: 55,  fee: 11000, feeType: 'estimate', meal: '점심', note: '순두부 백반 (저가)' },
    { type: 'transit', mode: '버스', from: '초당동',    to: '안목',      dep: '13:15', arr: '13:33', min: 18, fare: 1500,  feeType: 'confirm' },
    { type: 'place',  pid: 'p2', name: '안목 커피거리', cat: 'cafe',    arr: '13:33', stay: 60,  fee: 7000,  feeType: 'estimate', note: '바다뷰 핸드드립' },
    { type: 'place',  pid: 'p3', name: '오죽헌',        cat: 'culture', arr: '15:00', stay: 70,  fee: 3000,  feeType: 'confirm',  note: '신사임당 생가' },
    { type: 'transit', mode: 'KTX',  from: '강릉역',  to: '서울역',    dep: '17:42', arr: '19:36', min: 114, fare: 13700, feeType: 'confirm' },
  ],
}

export const COURSE_VARIANTS = [
  { id: 'trend',   name: '요즘 뜨는 코스', desc: '트렌드 70 · 거리 30', cost: 48400, min: 372, places: 5, accent: 'trend' },
  { id: 'balance', name: '추천 코스',      desc: '트렌드 50 · 거리 50', cost: 44400, min: 360, places: 5, accent: 'accent', default: true },
  { id: 'route',   name: '동선 효율 코스', desc: '트렌드 30 · 거리 70', cost: 39200, min: 338, places: 6, accent: 'transit' },
]

export const COMMUNITY = [
  { id: 'c1', title: '강릉 노을 맛집 코스 (커플 추천)', region: '강릉', author: '여름밤', cost: 52000, min: 390, places: 6, likes: 248, comments: 31, saves: 96, tags: ['커플', '노을', '카페'], hot: true },
  { id: 'c2', title: '인천 개항장 도보 한바퀴 (뚜벅이)', region: '인천', author: '걷는사람', cost: 18500, min: 300, places: 5, likes: 187, comments: 22, saves: 74, tags: ['뚜벅이', '저예산', '근대건축'] },
  { id: 'c3', title: '수원화성 야경 + 통닭 (3만원 이하)', region: '수원', author: '성곽러', cost: 24000, min: 270, places: 4, likes: 156, comments: 18, saves: 61, tags: ['야경', '가성비', '맛집'], hot: true },
  { id: 'c4', title: '전주 한옥 골목 미식 투어', region: '전주', author: '먹깨비', cost: 61000, min: 420, places: 7, likes: 312, comments: 44, saves: 138, tags: ['미식', '한옥', '전통주'] },
  { id: 'c5', title: '춘천 호수 자전거 + 닭갈비', region: '춘천', author: '의암호지기', cost: 33500, min: 360, places: 5, likes: 142, comments: 15, saves: 53, tags: ['액티비티', '호수', '닭갈비'] },
  { id: 'c6', title: '무료 위주 한강 라이딩 코스', region: '서울', author: '0원여행', cost: 4200, min: 240, places: 4, likes: 203, comments: 27, saves: 88, tags: ['무료', '한강', '자전거'], hot: true },
]

export const ME = {
  nick: '김절약', email: 'budget.kim@gadang.app', region: '서울 강서구', joined: '2026-03-12',
  stats: { trips: 7, spent: 284000, saved: 161000, shared: 3 },
  trips: [
    { id: 't1', title: '강릉 바다 + 커피 당일치기', region: '강릉', date: '06-06', cost: 44400, status: '예정' },
    { id: 't2', title: '인천 개항장 도보 코스',      region: '인천', date: '05-24', cost: 18500, status: '완료' },
    { id: 't3', title: '수원화성 야경 투어',         region: '수원', date: '05-10', cost: 23800, status: '완료' },
    { id: 't4', title: '춘천 호수 라이딩',           region: '춘천', date: '04-27', cost: 31200, status: '완료' },
  ],
  favorites: ['p1', 'p2', 'p4', 'p5'],
  posts: [{ id: 'c3', title: '수원화성 야경 + 통닭 (3만원 이하)', likes: 156, comments: 18 }],
}

export const NOTICES = [
  { id: 'n1', tag: '업데이트', title: 'ODsay 도시간 경로(KTX·고속버스) 추천 정확도 개선', date: '06-01' },
  { id: 'n2', tag: '이벤트',   title: '여름 당일치기 코스 공유 이벤트 — 추첨 커피 기프티콘',   date: '05-28' },
  { id: 'n3', tag: '점검',    title: '6/5 새벽 2–4시 트렌드 데이터 배치 점검 안내',          date: '05-25' },
]
