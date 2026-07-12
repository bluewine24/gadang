-- =============================================
-- 마이그레이션: 2026-06-25
-- MySQL Workbench 에서 1회 실행 (이미 적용됐으면 SKIP)
-- =============================================

USE gadang;

-- CONTINUE HANDLER 로 "Duplicate column" 에러 무시 — 이미 있는 컬럼은 건너뜀
DROP PROCEDURE IF EXISTS _migrate;
DELIMITER //
CREATE PROCEDURE _migrate()
BEGIN
    DECLARE CONTINUE HANDLER FOR SQLSTATE '42S21' BEGIN END;  -- duplicate column
    DECLARE CONTINUE HANDLER FOR 1060              BEGIN END;  -- duplicate column (alternate code)

    -- ── USER 테이블: 소셜 로그인 컬럼 ─────────────────────
    ALTER TABLE `USER` ADD COLUMN provider  VARCHAR(20)  NOT NULL DEFAULT 'local';
    ALTER TABLE `USER` ADD COLUMN social_id VARCHAR(100) NULL;
    ALTER TABLE `USER` MODIFY COLUMN email    VARCHAR(100) NULL;
    ALTER TABLE `USER` MODIFY COLUMN password VARCHAR(255) NULL;

    -- ── COMMUNITY_POST 테이블: 머리말/꼬리말/합계/블라인드 컬럼 ───
    ALTER TABLE COMMUNITY_POST ADD COLUMN intro              TEXT;
    ALTER TABLE COMMUNITY_POST ADD COLUMN outro              TEXT;
    ALTER TABLE COMMUNITY_POST ADD COLUMN total_cost         INT       NOT NULL DEFAULT 0;
    ALTER TABLE COMMUNITY_POST ADD COLUMN total_duration_min INT       NOT NULL DEFAULT 0;
    ALTER TABLE COMMUNITY_POST ADD COLUMN is_blinded         TINYINT(1) NOT NULL DEFAULT 0;
END //
DELIMITER ;

CALL _migrate();
DROP PROCEDURE IF EXISTS _migrate;

-- ── 게시글 장소별 상세 테이블 (신규 — IF NOT EXISTS 이므로 안전) ──
CREATE TABLE IF NOT EXISTS POST_PLACE_DETAIL (
    detail_id    BIGINT       NOT NULL AUTO_INCREMENT,
    post_id      BIGINT       NOT NULL,
    seq          INT          NOT NULL DEFAULT 0,
    place_id     BIGINT       NULL,
    place_name   VARCHAR(100) NOT NULL,
    text_content TEXT,
    cost         INT          NOT NULL DEFAULT 0,
    duration_min INT          NOT NULL DEFAULT 0,
    images       TEXT,
    PRIMARY KEY (detail_id),
    FOREIGN KEY (post_id) REFERENCES COMMUNITY_POST(post_id) ON DELETE CASCADE
);

SELECT 'migration-2026-06-25 완료' AS result;
