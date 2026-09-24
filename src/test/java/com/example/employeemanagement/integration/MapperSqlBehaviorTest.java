package com.example.employeemanagement.integration;

import com.example.employeemanagement.entity.FileInfo;
import com.example.employeemanagement.mapper.UserMapper;
import com.example.employeemanagement.mapper.FileInfoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Real test MySQL only: no application context, Redis, HTTP, or disk files. */
@SpringJUnitConfig(MapperSqlBehaviorTest.DatabaseConfig.class)
@ActiveProfiles("test")
@TestPropertySource("classpath:application-test.properties")
@Transactional
class MapperSqlBehaviorTest {
    @Configuration(proxyBeanMethods = false)
    static class DatabaseConfig {
        @Bean
        DataSource dataSource(Environment environment) {
            if (!List.of(environment.getActiveProfiles()).equals(List.of("test"))) {
                throw new IllegalStateException("Only the test profile is allowed");
            }
            String url = environment.getRequiredProperty("spring.datasource.url");
            if (!url.matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1):3306/employee_management_test(?:\\?.*)?")) {
                throw new IllegalStateException("Only local employee_management_test is allowed");
            }
            return new DriverManagerDataSource(url,
                    environment.getRequiredProperty("spring.datasource.username"),
                    environment.getRequiredProperty("spring.datasource.password"));
        }

        @Bean
        DataSourceTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource source) { return new JdbcTemplate(source); }

        @Bean
        SqlSessionTemplate sqlSessionTemplate(DataSource source) throws Exception {
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(source);
            org.apache.ibatis.session.Configuration config = new org.apache.ibatis.session.Configuration();
            config.setUseActualParamName(false);
            config.addMapper(UserMapper.class);
            factory.setConfiguration(config);
            factory.setMapperLocations(new ClassPathResource("mapper/FileInfoMapper.xml"));
            return new SqlSessionTemplate(factory.getObject());
        }

        @Bean
        FileInfoMapper fileMapper(SqlSessionTemplate session) { return session.getMapper(FileInfoMapper.class); }

        @Bean
        UserMapper userMapper(SqlSessionTemplate session) { return session.getMapper(UserMapper.class); }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired FileInfoMapper files;
    @Autowired UserMapper users;
    private String prefix;
    private int owner;
    private int other;
    private String username;
    private final List<Long> ids = new ArrayList<>();

    @BeforeEach
    void prepareIsolatedFixture() {
        assertEquals("employee_management_test", jdbc.queryForObject("SELECT DATABASE()", String.class));
        // Refuse fixture writes if rollback would not protect any participating table.
        for (String table : List.of("file_info", "user", "role", "permission", "user_role", "role_permission")) {
            assertEquals("InnoDB", jdbc.queryForObject(
                    "SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                    String.class, table), "Transactional table required: " + table);
        }
        prefix = "b9" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        username = prefix + "a";
        owner = insertUser(username);
        other = insertUser(prefix + "b");
        ids.clear();
        ids.add(insertFile("hit-old", owner, "2025-01-01 00:00:00"));
        ids.add(insertFile("hit-tie1", owner, "2025-01-02 00:00:00"));
        ids.add(insertFile("hit-tie2", other, "2025-01-02 00:00:00"));
        ids.add(insertFile("miss-new", owner, "2025-01-03 00:00:00"));
        ids.add(insertFile("hit-newest", other, "2025-01-04 00:00:00"));
    }

    private int insertUser(String name) {
        jdbc.update("INSERT INTO user (username, password, name, role, department_id) VALUES (?, ?, ?, 'USER', NULL)",
                name, "test-only-unused-password", "Batch9 fixture");
        return Math.toIntExact(lastId());
    }

    private long lastId() { return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class); }

    private long insertFile(String suffix, int userId, String time) {
        jdbc.update("INSERT INTO file_info (original_name, stored_name, content_type, file_size, upload_user_id, upload_time) "
                        + "VALUES (?, ?, 'text/plain', 10, ?, ?)",
                prefix + suffix + ".txt", prefix + suffix + ".txt", userId, time);
        return lastId();
    }

    private List<Long> recordIds(List<FileInfo> records) { return records.stream().map(FileInfo::getId).toList(); }

    private List<Long> expectedAll() { return List.of(ids.get(4), ids.get(3), ids.get(2), ids.get(1), ids.get(0)); }

    @Test
    void newerTimeFirstAndEqualTimeUsesDescendingId() {
        assertTrue(ids.get(2) > ids.get(1));
        assertEquals(expectedAll(), recordIds(files.searchPage(prefix, null, 0, 10)));
    }

    @Test
    void adjacentPagesHaveDeterministicOrderForUnchangedFixture() {
        List<Long> combined = new ArrayList<>();
        for (int offset = 0; offset < 5; offset += 2) {
            combined.addAll(recordIds(files.searchPage(prefix, null, offset, 2)));
        }
        assertEquals(expectedAll(), combined);
    }

    @ParameterizedTest
    @CsvSource({"all,admin", "hit,admin", "none,owner", "empty,owner", "hit,owner"})
    void filtersAndCountMatchFullSetRatherThanPageLength(String keywordMode, String scope) {
        String keyword = switch (keywordMode) {
            case "none" -> null;
            case "empty" -> "";
            case "hit" -> prefix + "hit";
            default -> prefix;
        };
        Integer userId = scope.equals("owner") ? owner : null;
        List<Long> expected = scope.equals("owner")
                ? (keywordMode.equals("hit") ? List.of(ids.get(1), ids.get(0)) : List.of(ids.get(3), ids.get(1), ids.get(0)))
                : (keywordMode.equals("hit") ? List.of(ids.get(4), ids.get(2), ids.get(1), ids.get(0)) : expectedAll());
        assertEquals(expected, recordIds(files.searchPage(keyword, userId, 0, 20)));
        assertEquals((long) expected.size(), files.countSearch(keyword, userId));
        assertEquals(expected.subList(0, 1), recordIds(files.searchPage(keyword, userId, 0, 1)));
        assertTrue(files.countSearch(keyword, userId) > 1);
    }

    @Test
    void sharedPermissionAcrossRolesIsReturnedOnce() {
        long roleA = insertRole("a");
        long roleB = insertRole("b");
        long permissionA = insertPermission("a");
        long permissionB = insertPermission("b");
        jdbc.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?), (?, ?)", owner, roleA, owner, roleB);
        jdbc.update("INSERT INTO role_permission (role_id, permission_id) VALUES (?, ?), (?, ?), (?, ?)",
                roleA, permissionA, roleB, permissionA, roleB, permissionB);
        List<String> result = users.findPermissionsByUsername(username);
        assertEquals(2, result.size());
        assertEquals(1L, result.stream().filter((prefix + ":a")::equals).count());
        assertEquals(1L, result.stream().filter((prefix + ":b")::equals).count());
    }

    private long insertRole(String suffix) {
        jdbc.update("INSERT INTO role (role_name, role_code) VALUES (?, ?)", "Batch9 role", prefix + suffix);
        return lastId();
    }

    private long insertPermission(String suffix) {
        jdbc.update("INSERT INTO permission (permission_name, permission_code) VALUES (?, ?)", "Batch9 permission", prefix + ":" + suffix);
        return lastId();
    }

    @Test
    void missingUsernameReturnsEmptyList() {
        assertTrue(users.findPermissionsByUsername(prefix + "missing").isEmpty());
    }

    @Test
    void existingUserWithoutPermissionsReturnsEmptyList() {
        assertNotNull(users.findByUsername(username));
        assertTrue(users.findPermissionsByUsername(username).isEmpty());
    }

    @Test
    void reportFileIdUniqueConstraintInTestDatabase() {
        Integer constraints = jdbc.queryForObject("""
                SELECT COUNT(*) FROM (
                    SELECT INDEX_NAME FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'file_info' AND NON_UNIQUE = 0
                    GROUP BY INDEX_NAME HAVING COUNT(*) = 1 AND MAX(COLUMN_NAME) = 'id'
                ) unique_id_indexes
                """, Integer.class);
        assertTrue(constraints > 0, "file_info.id uniqueness is required for deterministic ordering");
        System.out.println("Batch9B test schema: file_info.id has a single-column PRIMARY/UNIQUE index");
    }
}
