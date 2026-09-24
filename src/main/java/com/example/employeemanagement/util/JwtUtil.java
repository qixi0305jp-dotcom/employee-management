package com.example.employeemanagement.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

@org.springframework.stereotype.Component
public class JwtUtil {

    private final SecretKey key;
    private final long expirationMs;

    public JwtUtil(@org.springframework.beans.factory.annotation.Value("${jwt.secret}") String secret,
                   @org.springframework.beans.factory.annotation.Value("${jwt.expiration-ms}") long expirationMs) {
        if (expirationMs <= 0) {
            throw new IllegalArgumentException("jwt.expiration-ms must be greater than 0");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }


    public String generateToken(
            Integer userId,
            String username,
            String role) {

        Date now = new Date();

        Date expiration =
                new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(key)
                .compact();
    }

    //给 JwtUtil 增加解析方法
    public Claims parseToken(String token) {

        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    //增加一个判断 Token 是否有效的方法
    public boolean validateToken(String token) {

        try {
            parseToken(token);
            return true;

        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String generateTokenWithExpireTime(
            Integer userId,
            String username,
            String role,
            long expireTime) {

        Date now = new Date();

        Date expiration =
                new Date(now.getTime() + expireTime);

        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(key)
                .compact();
    }

}
