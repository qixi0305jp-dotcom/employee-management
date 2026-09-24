package com.example.employeemanagement.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabasePasswordConfigurationTest {

    @Test
    void localPassword_UsesOnlyDbPasswordPlaceholder() throws IOException {
        Properties properties = readProperties("application-local.properties");
        assertTrue("${DB_PASSWORD}".equals(properties.getProperty("spring.datasource.password")),
                "Local datasource password must use only the designated environment placeholder");
    }

    @Test
    void testPassword_UsesOnlyTestDbPasswordPlaceholder() throws IOException {
        Properties properties = readProperties("application-test.properties");
        assertTrue("${TEST_DB_PASSWORD}".equals(properties.getProperty("spring.datasource.password")),
                "Test datasource password must use only the designated environment placeholder");
    }

    @Test
    void prodPassword_UsesOnlyDbPasswordPlaceholder() throws IOException {
        Properties properties = readProperties("application-prod.properties");
        assertTrue("${DB_PASSWORD}".equals(properties.getProperty("spring.datasource.password")),
                "Production password must have no literal or fallback");
    }

    private Properties readProperties(String resource) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, "Required configuration resource must exist");
            try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                Properties properties = new Properties();
                properties.load(reader);
                return properties;
            }
        }
    }
}
