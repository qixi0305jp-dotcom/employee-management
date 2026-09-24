package com.example.employeemanagement.controller;

import com.example.employeemanagement.config.SecurityConfig;
import com.example.employeemanagement.exception.GlobalExceptionHandler;
import com.example.employeemanagement.mapper.UserMapper;
import com.example.employeemanagement.security.JwtAuthenticationEntryPoint;
import com.example.employeemanagement.security.JwtAuthenticationFilter;
import com.example.employeemanagement.service.EmployeeService;
import com.example.employeemanagement.service.UserService;
import com.example.employeemanagement.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.http.MediaType;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({EmployeeController.class, UserController.class})
@TestPropertySource(properties = "jwt.secret=TEST-ONLY-jwt-signing-key-not-for-production-2026-0123456789abcdef")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class,
        JwtUtil.class, GlobalExceptionHandler.class, ReadAuthorizationTest.WebSecurity.class})
class ReadAuthorizationTest {
    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class WebSecurity { }
    @Autowired WebApplicationContext context;
    @MockitoBean EmployeeService service;
    @MockitoBean UserService userService;
    @MockitoBean UserMapper mapper;
    MockMvc mvc;

    @BeforeEach
    void setup() { mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }

    @ParameterizedTest
    @ValueSource(strings = {"all", "id", "page", "search", "searchPage", "department", "searchDepartment", "detail", "scope"})
    void readerAllowed(String endpoint) throws Exception {
        mvc.perform(read(endpoint).with(user("alice").authorities(new SimpleGrantedAuthority("employee:view"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        switch (endpoint) {
            case "all" -> verify(service).findAll();
            case "id" -> verify(service).findById(42);
            case "page" -> verify(service).findByPage(2, 10);
            case "search" -> verify(service).search("张", "男", 2);
            case "searchPage" -> verify(service).searchPage("张", "男", 2, 2, 10);
            case "department" -> verify(service).findAllWithDepartment();
            case "searchDepartment" -> verify(service).searchWithDepartment("张", "男", "研发");
            case "detail" -> verify(service).findDetailById(42);
            default -> verify(service).findByUserScope("alice", false);
        }
        verifyNoMoreInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"all", "id", "page", "search", "searchPage", "department", "searchDepartment", "detail", "scope"})
    void noViewDeniedForUserAndAdmin(String endpoint) throws Exception {
        for (String role : List.of("USER", "ADMIN")) {
            mvc.perform(read(endpoint).with(user("alice").roles(role)))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
        }
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"all", "id", "page", "search", "searchPage", "department", "searchDepartment", "detail", "scope"})
    void anonymousDenied(String endpoint) throws Exception {
        mvc.perform(read(endpoint)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
        verifyNoInteractions(service, mapper);
    }

    @Test
    void adminScopeKeepsAdminFlag() throws Exception {
        mvc.perform(read("scope").with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("employee:view"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(service).findByUserScope("admin", true);
    }

    @ParameterizedTest
    @CsvSource({"alice,USER,alice", "admin,ADMIN,admin", "admin,ADMIN,alice", "admin,ADMIN,missing"})
    void permittedPermissionQuery(String caller, String role, String target) throws Exception {
        List<String> permissions = target.equals("missing") ? List.of() : List.of("employee:view");
        when(userService.findPermissionsByUsername(target)).thenReturn(permissions);
        mvc.perform(get("/users/{username}/permissions", target).with(user(caller).roles(role)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(content().json(target.equals("missing")
                        ? "{\"code\":200,\"data\":[]}"
                        : "{\"code\":200,\"data\":[\"employee:view\"]}"));
        verify(userService).findPermissionsByUsername(target);
        verifyNoMoreInteractions(userService);
        verifyNoInteractions(mapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"bob", "missing"})
    void otherUserPermissionsDeniedWithoutQuery(String target) throws Exception {
        mvc.perform(get("/users/{username}/permissions", target).with(user("alice").roles("USER")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
        // Mock authentication, no Bearer token: the Filter performs no user lookup here.
        verifyNoInteractions(mapper, userService);
    }

    @Test
    void anonymousPermissionsDenied() throws Exception {
        mvc.perform(get("/users/alice/permissions"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
        verifyNoInteractions(mapper, userService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"add", "put", "patch", "delete", "batch"})
    void writeAuthorityDoesNotRequireView(String operation) throws Exception {
        String complete = """
                {"name":"张三","gender":"男","age":30,"salary":5000,"departmentId":2,"hireDate":"2026-01-02"}
                """;
        MockHttpServletRequestBuilder request = switch (operation) {
            case "add" -> post("/employees").content(complete);
            case "put" -> put("/employees/42").content(complete);
            case "patch" -> patch("/employees/42").content("{\"age\":31}");
            case "delete" -> delete("/employees/42");
            default -> delete("/employees/batch").content("[42]");
        };
        String authority = switch (operation) {
            case "add" -> "employee:add";
            case "put", "patch" -> "employee:update";
            default -> "employee:delete";
        };
        mvc.perform(request.contentType(MediaType.APPLICATION_JSON)
                        .with(user("writer").authorities(new SimpleGrantedAuthority(authority))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        switch (operation) {
            case "add" -> verify(service).add(any());
            case "put" -> verify(service).update(any());
            case "patch" -> verify(service).updateSelective(any());
            case "delete" -> verify(service).deleteById(42);
            default -> verify(service).deleteBatch(List.of(42));
        }
        verifyNoMoreInteractions(service);
    }

    private MockHttpServletRequestBuilder read(String endpoint) {
        return switch (endpoint) {
            case "all" -> get("/employees");
            case "id" -> get("/employees/42");
            case "page" -> get("/employees/page").param("page", "2").param("pageSize", "10");
            case "search" -> get("/employees/search").param("name", "张").param("gender", "男").param("departmentId", "2");
            case "searchPage" -> get("/employees/search-page").param("name", "张").param("gender", "男")
                    .param("departmentId", "2").param("page", "2").param("pageSize", "10");
            case "department" -> get("/employees/with-department");
            case "searchDepartment" -> get("/employees/search-with-department").param("name", "张")
                    .param("gender", "男").param("departmentName", "研发");
            case "detail" -> get("/employees/42/detail");
            default -> get("/employees/my-scope");
        };
    }
}
