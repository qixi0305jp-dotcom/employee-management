package com.example.employeemanagement.util;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    // Public test fixture, not a production secret.
    private final JwtUtil jwtUtil = new JwtUtil("TEST-ONLY-jwt-signing-key-not-for-production-2026-0123456789abcdef", 3600000L);

    @Test
    void configuredExpirationUsesMilliseconds() {
        JwtUtil custom = new JwtUtil("TEST-ONLY-jwt-signing-key-not-for-production-2026-0123456789abcdef", 120000L);
        Claims claims = custom.parseToken(custom.generateToken(1, "alice", "USER"));
        assertEquals(120000L, claims.getExpiration().getTime() - claims.getIssuedAt().getTime());
        Claims defaults = jwtUtil.parseToken(jwtUtil.generateToken(1, "alice", "USER"));
        assertEquals(3600000L, defaults.getExpiration().getTime() - defaults.getIssuedAt().getTime());
    }

    @Test
    void nonPositiveExpirationRejected() {
        for (long value : new long[]{0, -1}) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> new JwtUtil("TEST-ONLY-jwt-signing-key-not-for-production-2026-0123456789abcdef", value));
            assertEquals("jwt.expiration-ms must be greater than 0", error.getMessage());
        }
    }

    @Test
    void testDifferentInjectedKeyRejectsToken() {
        JwtUtil otherSigner = new JwtUtil(
                "TEST-ONLY-other-signing-key-not-for-production-0123456789abcdef", 3600000L);
        String token = otherSigner.generateToken(1, "zhangsan", "USER");

        assertFalse(jwtUtil.validateToken(token));
        assertThrows(io.jsonwebtoken.security.SignatureException.class,
                () -> jwtUtil.parseToken(token));
    }

    @Test
    void testGenerateAndParseToken() {

        String token =
                jwtUtil.generateToken(
                        1,
                        "zhangsan",
                        "USER"
                );

        assertNotNull(token);

        Claims claims =
                jwtUtil.parseToken(token);

        assertEquals(
                "zhangsan",
                claims.getSubject()
        );

        assertEquals(
                1,
                claims.get("userId", Integer.class)
        );

        assertEquals(
                "USER",
                claims.get("role", String.class)
        );
    }

    @Test
    void testValidateToken_ValidToken() {

        String token =
                jwtUtil.generateToken(
                        1,
                        "zhangsan",
                        "USER"
                );

        boolean result =
                jwtUtil.validateToken(token);

        assertTrue(result);
    }

    @Test
    void testValidateToken_InvalidToken() {

        String token =
                jwtUtil.generateToken(
                        1,
                        "zhangsan",
                        "USER"
                );

        String invalidToken =
                "x" + token.substring(1);

        boolean result =
                jwtUtil.validateToken(invalidToken);

        assertFalse(result);
    }

    @Test
    void testValidateToken_ExpiredToken()
            throws InterruptedException {

        String token =
                jwtUtil.generateTokenWithExpireTime(
                        1,
                        "zhangsan",
                        "USER",
                        100
                );

        Thread.sleep(200);

        boolean result =
                jwtUtil.validateToken(token);

        assertFalse(result);
    }

    @Test
    void testParseToken_ExpiredToken()
            throws InterruptedException {

        String token =
                jwtUtil.generateTokenWithExpireTime(
                        1,
                        "zhangsan",
                        "USER",
                        100
                );

        Thread.sleep(200);

        assertThrows(
                io.jsonwebtoken.ExpiredJwtException.class,
                () -> jwtUtil.parseToken(token)
        );
    }
}
