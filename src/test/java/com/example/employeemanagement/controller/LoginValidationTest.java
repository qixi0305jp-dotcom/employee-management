package com.example.employeemanagement.controller;

import com.example.employeemanagement.config.SecurityConfig;
import com.example.employeemanagement.util.JwtUtil;
import com.example.employeemanagement.dto.LoginDTO;
import com.example.employeemanagement.exception.GlobalExceptionHandler;
import com.example.employeemanagement.mapper.UserMapper;
import com.example.employeemanagement.security.JwtAuthenticationEntryPoint;
import com.example.employeemanagement.security.JwtAuthenticationFilter;
import com.example.employeemanagement.service.UserService;
import com.example.employeemanagement.vo.LoginVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@org.springframework.test.context.TestPropertySource(properties = "jwt.secret=TEST-ONLY-jwt-signing-key-not-for-production-2026-0123456789abcdef")
@WebMvcTest(UserController.class)
@Import({JwtUtil.class, SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class,
        GlobalExceptionHandler.class, LoginValidationTest.TestWebSecurity.class})
class LoginValidationTest {
    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class TestWebSecurity { }

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JsonMapper jsonMapper;
    @MockitoBean
    private UserService userService;
    @MockitoBean
    private UserMapper userMapper;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private ResultActions invalidLogin(String json) throws Exception {
        ResultActions response = mvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(result -> assertInstanceOf(
                        MethodArgumentNotValidException.class, result.getResolvedException()));
        verifyNoInteractions(userService, userMapper);
        return response;
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"password\":\"valid\"}",
            "{\"username\":null,\"password\":\"valid\"}",
            "{\"username\":\"\",\"password\":\"valid\"}",
            "{\"username\":\"   \",\"password\":\"valid\"}"
    })
    void invalidUsername_Returns400BeforeService(String json) throws Exception {
        invalidLogin(json).andExpect(jsonPath("$.data", aMapWithSize(1)))
                .andExpect(jsonPath("$.data.username").value("用户名不能为空"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"username\":\"alice\"}",
            "{\"username\":\"alice\",\"password\":null}",
            "{\"username\":\"alice\",\"password\":\"\"}",
            "{\"username\":\"alice\",\"password\":\"   \"}"
    })
    void invalidPassword_Returns400BeforeService(String json) throws Exception {
        invalidLogin(json).andExpect(jsonPath("$.data", aMapWithSize(1)))
                .andExpect(jsonPath("$.data.password").value("密码不能为空"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"username\":null,\"password\":null}",
            "{\"username\":\"\",\"password\":\"\"}",
            "{\"username\":\"   \",\"password\":\"   \"}"
    })
    void bothInvalid_ReturnsBothFieldErrorsBeforeService(String json) throws Exception {
        invalidLogin(json).andExpect(jsonPath("$.data", aMapWithSize(2)))
                .andExpect(jsonPath("$.data.username").value("用户名不能为空"))
                .andExpect(jsonPath("$.data.password").value("密码不能为空"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"x", "  x  "})
    void validInput_PassesUnchangedToServiceWithoutAuthentication(String input) throws Exception {
        LoginVO login = new LoginVO();
        login.setToken("mock-login-result");
        when(userService.login(any(LoginDTO.class))).thenReturn(login);

        mvc.perform(post("/users/login").contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(
                                Map.of("username", input, "password", input))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").value("mock-login-result"));

        ArgumentCaptor<LoginDTO> dto = ArgumentCaptor.forClass(LoginDTO.class);
        verify(userService, times(1)).login(dto.capture());
        assertEquals(input, dto.getValue().getUsername());
        assertEquals(input, dto.getValue().getPassword());
        verifyNoMoreInteractions(userService);
        verifyNoInteractions(userMapper);
    }
}
