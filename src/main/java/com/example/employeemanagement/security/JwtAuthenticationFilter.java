package com.example.employeemanagement.security;

import com.example.employeemanagement.mapper.UserMapper;
import com.example.employeemanagement.entity.User;
import io.jsonwebtoken.JwtException;
import org.springframework.security.authentication.BadCredentialsException;
import com.example.employeemanagement.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;

    private final JwtAuthenticationEntryPoint entryPoint;

    public JwtAuthenticationFilter(UserMapper userMapper, JwtAuthenticationEntryPoint entryPoint, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
        this.entryPoint = entryPoint;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String authorization =
                request.getHeader("Authorization");

        // 没有 JWT，先交给后面的 Security 处理
        if (authorization == null ||
                !authorization.startsWith("Bearer ")) {

            filterChain.doFilter(request, response);
            return;
        }

        String token = authorization.substring(7);

        String username;
        Integer userId;
        try {
            Claims claims = jwtUtil.parseToken(token);
            username = claims.getSubject();
            userId = claims.get("userId", Integer.class);
        } catch (JwtException | IllegalArgumentException e) {
            rejectAuthentication(request, response);
            return;
        }
        if (username == null || username.isBlank() || userId == null) {
            rejectAuthentication(request, response);
            return;
        }

        // Database failures are server failures, not invalid JWTs.
        User user = userMapper.findByUsername(username);
        if (user == null || !userId.equals(user.getId())) {
            rejectAuthentication(request, response);
            return;
        }
        String role = user.getRole();
        List<String> permissions =
                userMapper.findPermissionsByUsername(username);

        List<SimpleGrantedAuthority> authorities =
                permissions.stream()
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());

        authorities.add(
                new SimpleGrantedAuthority("ROLE_" + role)
        );

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        authorities
                );

        // 放入 SecurityContext
        SecurityContextHolder
                .getContext()
                .setAuthentication(authentication);

        // 继续执行后面的 Filter
        filterChain.doFilter(request, response);
    }
    private void rejectAuthentication(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        SecurityContextHolder.clearContext();
        entryPoint.commence(request, response, new BadCredentialsException("Token无效"));
    }
}
