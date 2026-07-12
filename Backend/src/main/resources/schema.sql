-- =============================================
-- 가당 (가성비 당일치기) DB 스키마
-- MySQL 8.0 기준
-- =============================================

CREATE DATABASE IF NOT EXISTS gadang
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE gadang;

-- ── 회원 ──────────────────────────────────────────
CREATE TABLE IF NOT EXISTS USER (
    user_id     BIGINT       NOT NULL AUTO_INCREMENT,
    email       VARCHAR(100) NULL UNIQUE,
    password    VARCHAR(255) NULL,
    nickname    VARCHAR(50)  NOT NULL,
    role        VARCHAR(20)  NOT NULL DEFAULT 'USER',
    provider    VARCHAR(20)  NOT NULL DEFAULT 'local',
    social_id   VARCHAR(100) NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id)
);

-- ── 지역 마스터 ────────────────────────────────────
CREATE TABLE IF NOT EXISTS REGION (
    region_id     BIGINT       NOT NULL AUTO_INCREMENT,
    sido          VARCHAR(50)  NOT NULL,
    sigungu       VARCHAR(50)  NOT NULL,
    name          VARCHAR(100) NOT NULL,
    center_lat    DOUBLE       NOT NULL,
    center_lng    DOUBLE       NOT NULL,
    content_count INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (region_id)
);

-- ── 장소 ──────────────────────────────────────────
CREATE TABLE IF NOT EXISTS PLACE (
    place_id         BIGINT       NOT NULL AUTO_INCREMENT,
    region_id        BIGINT,
    kakao_place_id   VARCHAR(50)  NOT NULL UNIQUE,  -- Kakao API place id
    name             VARCHAR(200) NOT NULL,
    category_code    VARCHAR(10)  NOT NULL,          -- AT4 / CT1 / CE7 / FD6 등
    category_name    VARCHAR(200),
    address          VARCHAR(300),
    lat              DOUBLE       NOT NULL,
    lng              DOUBLE       NOT NULL,
    place_url        VARCHAR(300),                   -- 카카오맵 링크 (운영시간 확인용)
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (place_id),
    INDEX idx_place_category (category_code),
    INDEX idx_place_region   (region_id),
    INDEX idx_place_location (lat, lng)
);

-- ── 입장료 큐레이션 ────────────────────────────────
CREATE TABLE IF NOT EXISTS ADMISSION_FEE (
    fee_id      BIGINT      NOT NULL AUTO_INCREMENT,
    place_id    BIGINT      NOT NULL UNIQUE,
    fee         INT         NOT NULL DEFAULT 0,
    fee_type    VARCHAR(20) NOT NULL DEFAULT 'FREE', -- FREE / FIXED / ESTIMATED
    updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (fee_id),
    FOREIGN KEY (place_id) REFERENCES PLACE(place_id)
);

-- ── 프랜차이즈 블랙리스트 ──────────────────────────
CREATE TABLE IF NOT EXISTS FRANCHISE_BLACKLIST (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    brand_name   VARCHAR(100) NOT NULL UNIQUE,
    registered_at DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

-- ── 즐겨찾기 ──────────────────────────────────────
CREATE TABLE IF NOT EXISTS FAVORITE (
    favorite_id BIGINT   NOT NULL AUTO_INCREMENT,
    user_id     BIGINT   NOT NULL,
    place_id    BIGINT   NOT NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (favorite_id),
    UNIQUE KEY uq_favorite (user_id, place_id),
    FOREIGN KEY (user_id)  REFERENCES USER(user_id)  ON DELETE CASCADE,
    FOREIGN KEY (place_id) REFERENCES PLACE(place_id)
);

-- ── 여행 일정 ─────────────────────────────────────
CREATE TABLE IF NOT EXISTS TRIP_PLAN (
    trip_id       BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    region_id     BIGINT,
    title         VARCHAR(200) NOT NULL,
    trip_date     DATE         NOT NULL,
    start_point   VARCHAR(300) NOT NULL,
    end_point     VARCHAR(300),
    departure_time TIME        NOT NULL,
    return_time    TIME        NOT NULL,
    budget_guide  INT,                               -- 소프트 예산 가이드 (NULL 허용)
    total_cost    INT          NOT NULL DEFAULT 0,   -- 교통비 + 입장료 확정값
    food_cost_est INT          NOT NULL DEFAULT 0,   -- 식비 추정값
    course_json   LONGTEXT,                          -- 확정 코스 타임라인 스냅샷(JSON) — 카카오 장소라 정규화 대신 스냅샷 저장
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (trip_id),
    FOREIGN KEY (user_id) REFERENCES USER(user_id) ON DELETE CASCADE
);

-- ── 일정 내 방문 장소 ──────────────────────────────
CREATE TABLE IF NOT EXISTS TRIP_ITEM (
    item_id        BIGINT   NOT NULL AUTO_INCREMENT,
    trip_id        BIGINT   NOT NULL,
    place_id       BIGINT   NOT NULL,
    visit_order    INT      NOT NULL,
    arrival_time   TIME,
    stay_minutes   INT      NOT NULL DEFAULT 60,
    admission_fee  INT      NOT NULL DEFAULT 0,
    food_cost      INT      NOT NULL DEFAULT 0,
    PRIMARY KEY (item_id),
    FOREIGN KEY (trip_id)  REFERENCES TRIP_PLAN(trip_id) ON DELETE CASCADE,
    FOREIGN KEY (place_id) REFERENCES PLACE(place_id)
);

-- ── 일정 내 이동 경로 ──────────────────────────────
CREATE TABLE IF NOT EXISTS TRIP_ROUTE (
    route_id         BIGINT      NOT NULL AUTO_INCREMENT,
    trip_id          BIGINT      NOT NULL,
    from_item_id     BIGINT,
    to_item_id       BIGINT      NOT NULL,
    transport_type   VARCHAR(20) NOT NULL,  -- WALK / BUS / SUBWAY / KTX / ESTIMATED
    duration_minutes INT         NOT NULL DEFAULT 0,
    fare             INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (route_id),
    FOREIGN KEY (trip_id) REFERENCES TRIP_PLAN(trip_id) ON DELETE CASCADE
);

-- ── 코스 공유 게시판 ───────────────────────────────
CREATE TABLE IF NOT EXISTS COMMUNITY_POST (
    post_id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    trip_id          BIGINT,
    title            VARCHAR(200) NOT NULL,
    intro            TEXT,
    outro            TEXT,
    content          TEXT,
    total_cost       INT          NOT NULL DEFAULT 0,
    total_duration_min INT        NOT NULL DEFAULT 0,
    is_blinded       TINYINT(1)   NOT NULL DEFAULT 0,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (post_id),
    FOREIGN KEY (user_id) REFERENCES USER(user_id) ON DELETE CASCADE,
    FOREIGN KEY (trip_id) REFERENCES TRIP_PLAN(trip_id) ON DELETE SET NULL
);

-- ── 게시글 장소별 상세 (머리말/꼬리말 제외 본문 데이터) ──────────────
CREATE TABLE IF NOT EXISTS POST_PLACE_DETAIL (
    detail_id    BIGINT       NOT NULL AUTO_INCREMENT,
    post_id      BIGINT       NOT NULL,
    seq          INT          NOT NULL DEFAULT 0,
    place_id     BIGINT       NULL,
    place_name   VARCHAR(100) NOT NULL,
    text_content TEXT,
    cost         INT          NOT NULL DEFAULT 0,  -- 해당 장소 지출 (원)
    duration_min INT          NOT NULL DEFAULT 0,  -- 소요 시간 (분)
    images       TEXT,                             -- JSON 배열 ["url1","url2"]
    PRIMARY KEY (detail_id),
    FOREIGN KEY (post_id) REFERENCES COMMUNITY_POST(post_id) ON DELETE CASCADE
);

-- ── 댓글 ──────────────────────────────────────────
CREATE TABLE IF NOT EXISTS COMMENT (
    comment_id BIGINT   NOT NULL AUTO_INCREMENT,
    post_id    BIGINT   NOT NULL,
    user_id    BIGINT   NOT NULL,
    content    TEXT     NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (comment_id),
    FOREIGN KEY (post_id)  REFERENCES COMMUNITY_POST(post_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id)  REFERENCES USER(user_id) ON DELETE CASCADE
);

-- ── 공지사항 ──────────────────────────────────────
CREATE TABLE IF NOT EXISTS NOTICE (
    notice_id  BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    title      VARCHAR(200) NOT NULL,
    content    TEXT         NOT NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (notice_id),
    FOREIGN KEY (user_id) REFERENCES USER(user_id)
);

-- ── 지역 트렌드 ────────────────────────────────────
CREATE TABLE IF NOT EXISTS REGION_TREND (
    trend_id     BIGINT      NOT NULL AUTO_INCREMENT,
    region_id    BIGINT      NOT NULL,
    trend_score  DOUBLE      NOT NULL DEFAULT 0,
    source       VARCHAR(50) NOT NULL DEFAULT 'DATALAB',
    collected_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (trend_id),
    INDEX idx_region_trend (region_id, collected_at DESC)
);

-- ── 장소 트렌드 ────────────────────────────────────
CREATE TABLE IF NOT EXISTS PLACE_TREND (
    trend_id     BIGINT      NOT NULL AUTO_INCREMENT,
    place_id     BIGINT      NOT NULL,
    trend_score  DOUBLE      NOT NULL DEFAULT 0,
    source       VARCHAR(50) NOT NULL DEFAULT 'DATALAB',
    collected_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (trend_id),
    INDEX idx_place_trend (place_id, collected_at DESC),
    INDEX idx_place_trend_score (trend_score DESC)
);

-- ── 기본 프랜차이즈 블랙리스트 데이터 ────────────────
INSERT IGNORE INTO FRANCHISE_BLACKLIST (brand_name) VALUES
    ('파리바게뜨'), ('뚜레쥬르'), ('이디야'), ('스타벅스'), ('투썸플레이스'),
    ('메가커피'), ('컴포즈커피'), ('빽다방'), ('할리스'), ('폴바셋'),
    ('맥도날드'), ('버거킹'), ('롯데리아'), ('맘스터치'), ('서브웨이'),
    ('KFC'), ('파파이스'), ('노브랜드버거'),
    ('CU'), ('GS25'), ('세븐일레븐'), ('이마트24'),
    ('올리브영'), ('다이소');

-- ── 교통 노선 캐시 (L2) ──────────────────────────────
-- 외부 API(TAGO·Odsay) 결과의 영속 캐시. 요청 시 없으면 API 호출 후 저장(write-through),
-- 배치(TransitRouteRefreshJob)가 묵은 행만 주기 갱신 — Odsay 일 1,000건 쿼터 보호.
CREATE TABLE IF NOT EXISTS TRANSIT_ROUTE (
    from_hub     VARCHAR(60) NOT NULL,           -- 역명 or 'ODSAY:터미널ID'
    to_hub       VARCHAR(60) NOT NULL,
    type         VARCHAR(20) NOT NULL,           -- KTX / 무궁화 / ITX-청춘 / 시외버스
    duration_min INT         NOT NULL DEFAULT -1, -- -1 = 미운행(부정 캐시)
    fare         INT         NOT NULL DEFAULT -1,
    daily_trips  INT         NOT NULL DEFAULT -1,
    updated_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (from_hub, to_hub, type),
    INDEX idx_transit_stale (type, updated_at)
);

-- ── 트렌드·이미지 L2 캐시 ────────────────────────────
CREATE TABLE IF NOT EXISTS TREND_CACHE (
    keyword     VARCHAR(40) NOT NULL,           -- 지역명
    score       DOUBLE      NOT NULL,           -- 앵커(서울=100) 기준 스케일 값
    updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (keyword)
);

CREATE TABLE IF NOT EXISTS REGION_IMAGE (
    region_name VARCHAR(40) NOT NULL,
    urls        TEXT        NOT NULL,            -- 개행 구분 이미지 URL 목록
    updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (region_name)
);

-- ── 편별 시간표 캐시 ─────────────────────────────────
-- 기차(TAGO API)·버스(Odsay API)의 편별 출발·도착 시각.
-- 기차: travel_date = 당일(20시간 TTL), 버스: travel_date = '1970-01-01'(정적, 6일 TTL).
CREATE TABLE IF NOT EXISTS TRANSPORT_SCHEDULE (
    id           BIGINT AUTO_INCREMENT,
    from_hub     VARCHAR(60) NOT NULL,    -- 역명 or 'ODSAY:터미널ID'
    to_hub       VARCHAR(60) NOT NULL,
    type         VARCHAR(20) NOT NULL,    -- KTX / 무궁화 / ITX-청춘 / 시외버스
    dep_time     CHAR(5)     NOT NULL,    -- 'HH:mm'
    arr_time     CHAR(5),                 -- 'HH:mm' (도착 시각)
    duration_min INT         DEFAULT -1,
    fare         INT         DEFAULT -1,
    train_no     VARCHAR(30),             -- 열차번호 (버스는 NULL)
    travel_date  DATE        NOT NULL,    -- 기준 날짜
    updated_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_sched (from_hub, to_hub, type, dep_time, travel_date),
    INDEX idx_sched_lookup (from_hub, to_hub, type, travel_date)
);

-- ── 지역 장소 후보 L2 캐시 ───────────────────────────
-- 좌표를 격자(약 5km)로 양자화한 키 → 인기 필터링된 장소 후보 목록(JSON).
-- 좌표는 연속값이지만 지역 선택 기반 호출이라 격자 양자화 시 적중률이 높다.
CREATE TABLE IF NOT EXISTS REGION_PLACES (
    cache_key   VARCHAR(120) NOT NULL,           -- "places:35.85,128.60:AT4,CE7,CT1,FD6"
    payload     MEDIUMTEXT   NOT NULL,           -- PlaceCandidate 목록 JSON
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (cache_key)
);
