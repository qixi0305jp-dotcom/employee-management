package com.example.employeemanagement.controller;

import com.example.employeemanagement.config.SecurityConfig;
import com.example.employeemanagement.entity.User;
import com.example.employeemanagement.mapper.UserMapper;
import com.example.employeemanagement.security.JwtAuthenticationEntryPoint;
import com.example.employeemanagement.security.JwtAuthenticationFilter;
import com.example.employeemanagement.service.EmployeeService;
import com.example.employeemanagement.util.JwtUtil;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@org.springframework.test.context.TestPropertySource(properties = "jwt.secret=TEST-ONLY-jwt-signing-key-not-for-production-2026-0123456789abcdef")
@WebMvcTest(EmployeeController.class)
@Import({JwtUtil.class, SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class,
        JwtFilterChainTest.TestSecurity.class, JwtFilterChainTest.IdentityController.class})
class JwtFilterChainTest {

    @Autowired
    private JwtUtil jwtUtil;
    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class TestSecurity { }

    @RestController
    static class IdentityController {
        @GetMapping("/test/identity")
        Map<String, Object> identity(Authentication authentication) {
            return Map.of("username", authentication.getName(), "authenticated", authentication.isAuthenticated(),
                    "authorities", authentication.getAuthorities().stream().map(a -> a.getAuthority()).toList());
        }
    }

    @Autowired
    WebApplicationContext context;
    @MockitoBean
    UserMapper userMapper;
    @MockitoBean
    EmployeeService employeeService;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private String token() {
        return jwtUtil.generateToken(1, "alice", "ADMIN");
    }

    private void currentUser(int id, String role) {
        User user = new User();
        user.setId(id);
        user.setUsername("alice");
        user.setRole(role);
        when(userMapper.findByUsername("alice")).thenReturn(user);
    }

    private void unauthorized(String token) throws Exception {
        mvc.perform(get("/employees/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        verifyNoInteractions(employeeService);
    }

    @Test
    void noToken_Returns401() throws Exception {
        mvc.perform(get("/employees/1")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        verifyNoInteractions(userMapper, employeeService);
    }

    @Test
    void malformedToken_Returns401WithoutUserQuery() throws Exception {
        unauthorized("not-a-jwt");
        verifyNoInteractions(userMapper);
    }

    @Test
    void wrongSigningKey_Returns401WithoutUserQuery() throws Exception {
        String wrongKeyToken = Jwts.builder().subject("alice").claim("userId", 1)
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(Jwts.SIG.HS256.key().build()).compact();
        unauthorized(wrongKeyToken);
        verifyNoInteractions(userMapper);
    }

    @Test
    void pastExpiration_Returns401WithoutUserQuery() throws Exception {
        // Existing helper sets expiration to one day in the past; no sleep or timing race.
        unauthorized(jwtUtil.generateTokenWithExpireTime(1, "alice", "USER", -86400000L));
        verifyNoInteractions(userMapper);
    }

    @Test
    void missingSubject_Returns401WithoutUserQuery() throws Exception {
        unauthorized(jwtUtil.generateToken(1, null, "USER"));
        verifyNoInteractions(userMapper);
    }

    @Test
    void missingUserId_Returns401WithoutUserQuery() throws Exception {
        unauthorized(jwtUtil.generateToken(null, "alice", "USER"));
        verifyNoInteractions(userMapper);
    }

    @Test
    void deletedUser_Returns401WithoutPermissionQuery() throws Exception {
        when(userMapper.findByUsername("alice")).thenReturn(null);
        unauthorized(token());
        verify(userMapper).findByUsername("alice");
        verifyNoMoreInteractions(userMapper);
    }

    @Test
    void recreatedUsernameWithDifferentId_Returns401() throws Exception {
        currentUser(2, "ADMIN");
        unauthorized(token());
        verify(userMapper).findByUsername("alice");
        verifyNoMoreInteractions(userMapper);
    }

    @Test
    void validUser_AuthenticationUsesCurrentRoleAndPermissions() throws Exception {
        currentUser(1, "USER");
        when(userMapper.findPermissionsByUsername("alice")).thenReturn(List.of("employee:view"));
        mvc.perform(get("/test/identity").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.authorities", containsInAnyOrder("ROLE_USER", "employee:view")))
                .andExpect(jsonPath("$.authorities", not(hasItem("ROLE_ADMIN"))));
        verify(userMapper).findByUsername("alice");
        verify(userMapper).findPermissionsByUsername("alice");
    }

    @Test
    void authenticatedWithoutDeletePermission_Returns403BeforeService() throws Exception {
        currentUser(1, "USER");
        when(userMapper.findPermissionsByUsername("alice")).thenReturn(List.of("employee:view"));
        mvc.perform(delete("/employees/batch").header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON).content("[1]"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
        verifyNoInteractions(employeeService);
    }

    @Test
    void userDatabaseFailure_IsNotConvertedTo401() {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("test database failure");
        when(userMapper.findByUsername("alice")).thenThrow(failure);
        Exception thrown = assertThrows(Exception.class,
                () -> mvc.perform(get("/employees/1").header("Authorization", "Bearer " + token())));
        assertSame(failure, rootCause(thrown));
        verify(userMapper).findByUsername("alice");
        verifyNoMoreInteractions(userMapper);
        verifyNoInteractions(employeeService);
    }

    @Test
    void permissionDatabaseFailure_IsNotConvertedTo401() {
        currentUser(1, "USER");
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("test permissions failure");
        when(userMapper.findPermissionsByUsername("alice")).thenThrow(failure);
        Exception thrown = assertThrows(Exception.class,
                () -> mvc.perform(get("/employees/1").header("Authorization", "Bearer " + token())));
        assertSame(failure, rootCause(thrown));
        verifyNoInteractions(employeeService);
    }

    private Throwable rootCause(Throwable error) {
        while (error.getCause() != null) {
            error = error.getCause();
        }
        return error;
    }
}
