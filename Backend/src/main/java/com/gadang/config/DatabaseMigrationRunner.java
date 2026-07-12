package com.gadang.config;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DatabaseMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMigrationRunner.class);

    private final DataSource dataSource;

    public DatabaseMigrationRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection con = dataSource.getConnection()) {
            // USER 테이블 — 소셜 로그인 컬럼
            tryExec(con, "ALTER TABLE `USER` ADD COLUMN provider  VARCHAR(20)  NOT NULL DEFAULT 'local'");
            tryExec(con, "ALTER TABLE `USER` ADD COLUMN social_id VARCHAR(100) NULL");
            tryExec(con, "ALTER TABLE `USER` MODIFY COLUMN email    VARCHAR(100) NULL");
            tryExec(con, "ALTER TABLE `USER` MODIFY COLUMN password VARCHAR(255) NULL");

            // COMMUNITY_POST 테이블 — 머리말/꼬리말/합계/블라인드 컬럼
            tryExec(con, "ALTER TABLE COMMUNITY_POST ADD COLUMN intro              TEXT");
            tryExec(con, "ALTER TABLE COMMUNITY_POST ADD COLUMN outro              TEXT");
            tryExec(con, "ALTER TABLE COMMUNITY_POST ADD COLUMN total_cost         INT       NOT NULL DEFAULT 0");
            tryExec(con, "ALTER TABLE COMMUNITY_POST ADD COLUMN total_duration_min INT       NOT NULL DEFAULT 0");
            tryExec(con, "ALTER TABLE COMMUNITY_POST ADD COLUMN is_blinded         TINYINT(1) NOT NULL DEFAULT 0");

            // POST_PLACE_DETAIL 테이블 — 신규 생성
            tryExec(con, """
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
                    )
                    """);

            log.info("[Migration] DB migration completed.");
        } catch (SQLException e) {
            log.error("[Migration] Connection failed: {}", e.getMessage());
        }
    }

    private void tryExec(Connection con, String sql) {
        try (var stmt = con.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            // 1060 = Duplicate column (이미 존재하는 컬럼은 무시)
            if (e.getErrorCode() != 1060) {
                log.warn("[Migration] Skipped: {} — {}", sql.trim().substring(0, Math.min(60, sql.trim().length())), e.getMessage());
            }
        }
    }
}
