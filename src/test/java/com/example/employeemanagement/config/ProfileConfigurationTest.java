package com.example.employeemanagement.config;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class ProfileConfigurationTest {
    private Properties read(String name) throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(name)) {
            assertNotNull(stream);
            Properties properties = new Properties();
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return properties;
        }
    }

    @Test
    void commonConfigurationHasNoEnvironmentTargetOrProfileSelection() throws Exception {
        Properties common = read("application.properties");
        for (String key : new String[]{"spring.datasource.url", "spring.datasource.username",
                "spring.datasource.password", "app.upload-dir", "logging.file.name"}) {
            assertNull(common.getProperty(key), key);
        }
        assertFalse(common.stringPropertyNames().stream().anyMatch(k -> k.startsWith("spring.data.redis.")));
        assertEquals("${JWT_SECRET}", common.getProperty("jwt.secret"));
        assertEquals("${JWT_EXPIRATION_MS:3600000}", common.getProperty("jwt.expiration-ms"));
        for (String file : new String[]{"application.properties", "application-local.properties",
                "application-prod.properties", "application-test.properties"}) {
            assertFalse(read(file).stringPropertyNames().stream().anyMatch(k -> k.startsWith("spring.profiles.")), file);
        }
    }

    @Test
    void localRetainsDevelopmentLocationsAndSwagger() throws Exception {
        Properties local = read("application-local.properties");
        assertEquals("jdbc:mysql://localhost:3306/employee_management?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Tokyo&characterEncoding=UTF-8", local.getProperty("spring.datasource.url"));
        assertEquals("${DB_USERNAME:root}", local.getProperty("spring.datasource.username"));
        assertEquals("localhost", local.getProperty("spring.data.redis.host"));
        assertEquals("6379", local.getProperty("spring.data.redis.port"));
        assertEquals("0", local.getProperty("spring.data.redis.database"));
        assertEquals("uploads", local.getProperty("app.upload-dir"));
        assertEquals("logs/employee-management.log", local.getProperty("logging.file.name"));
        assertSwagger(local, "true");
    }

    @Test
    void testTargetsAndDirectoriesRemainIsolated() throws Exception {
        Properties test = read("application-test.properties");
        assertEquals("jdbc:mysql://localhost:3306/employee_management_test?useSSL=false&serverTimezone=Asia/Tokyo&characterEncoding=UTF-8&allowPublicKeyRetrieval=true", test.getProperty("spring.datasource.url"));
        assertEquals("${TEST_DB_USERNAME:root}", test.getProperty("spring.datasource.username"));
        assertEquals("localhost", test.getProperty("spring.data.redis.host"));
        assertEquals("6379", test.getProperty("spring.data.redis.port"));
        assertEquals("1", test.getProperty("spring.data.redis.database"));
        assertEquals("2s", test.getProperty("spring.data.redis.connect-timeout"));
        assertEquals("2s", test.getProperty("spring.data.redis.timeout"));
        assertEquals("target/test-uploads", test.getProperty("app.upload-dir"));
        assertEquals("target/test-logs/employee-management.log", test.getProperty("logging.file.name"));
        assertEquals("3600000", test.getProperty("jwt.expiration-ms"));
        assertTrue(test.getProperty("jwt.secret").startsWith("TEST-ONLY-"));
        assertSwagger(test, "false");
    }

    @Test
    void productionRequiresExternalTargetsAndVerifiedDatabaseTls() throws Exception {
        Properties prod = read("application-prod.properties");
        assertEquals("jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}?sslMode=VERIFY_IDENTITY&serverTimezone=Asia/Tokyo&characterEncoding=UTF-8", prod.getProperty("spring.datasource.url"));
        String[][] bindings = {
                {"spring.datasource.username", "DB_USERNAME"},
                {"spring.datasource.password", "DB_PASSWORD"},
                {"spring.data.redis.host", "REDIS_HOST"},
                {"spring.data.redis.port", "REDIS_PORT"},
                {"spring.data.redis.password", "REDIS_PASSWORD"},
                {"spring.data.redis.database", "REDIS_DATABASE"},
                {"app.upload-dir", "UPLOAD_DIR"}, {"logging.file.name", "LOG_FILE"}
        };
        for (String[] binding : bindings) {
            assertEquals("${" + binding[1] + "}", prod.getProperty(binding[0]), binding[0]);
        }
        assertSwagger(prod, "false");
    }

    private void assertSwagger(Properties properties, String enabled) {
        assertEquals(enabled, properties.getProperty("springdoc.api-docs.enabled"));
        assertEquals(enabled, properties.getProperty("springdoc.swagger-ui.enabled"));
    }
}
