package com.gadang.config;

import com.gadang.user.User;
import com.gadang.user.UserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminAccountSeeder implements CommandLineRunner {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final String adminEmail;
    private final String adminPassword;
    private final String adminNickname;

    public AdminAccountSeeder(
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            JdbcTemplate jdbcTemplate,
            @Value("${gadang.admin.seed-email:admin@gadang.local}") String adminEmail,
            @Value("${gadang.admin.seed-password:Admin1234!}") String adminPassword,
            @Value("${gadang.admin.seed-nickname:admin}") String adminNickname) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.adminNickname = adminNickname;
    }

    @Override
    public void run(String... args) {
        ensureUserCompatibilityColumns();
        User existing = userMapper.findByEmail(adminEmail);
        if (existing != null) {
            if (!"ADMIN".equals(existing.getRole())) {
                userMapper.updateRole(existing.getUserId(), "ADMIN");
            }
            if (looksEncodingDamaged(existing.getNickname())) {
                userMapper.updateNickname(existing.getUserId(), adminNickname);
            }
            return;
        }

        User admin = new User();
        admin.setEmail(adminEmail);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setNickname(adminNickname);
        admin.setRole("ADMIN");
        admin.setProvider("local");
        userMapper.insert(admin);
    }

    private void ensureUserCompatibilityColumns() {
        if (!hasUserColumn("provider")) {
            jdbcTemplate.execute("ALTER TABLE `USER` ADD COLUMN provider VARCHAR(20) NOT NULL DEFAULT 'local'");
        }
        if (!hasUserColumn("social_id")) {
            jdbcTemplate.execute("ALTER TABLE `USER` ADD COLUMN social_id VARCHAR(100) NULL");
        }
        jdbcTemplate.execute("ALTER TABLE `USER` MODIFY COLUMN email VARCHAR(100) NULL");
        jdbcTemplate.execute("ALTER TABLE `USER` MODIFY COLUMN password VARCHAR(255) NULL");
    }

    private boolean hasUserColumn(String columnName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'USER'
                  AND COLUMN_NAME = ?
                """, Integer.class, columnName);
        return count != null && count > 0;
    }

    private boolean looksEncodingDamaged(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return value.indexOf('?') >= 0 || value.indexOf('\uFFFD') >= 0;
    }
}
