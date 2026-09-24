package com.example.employeemanagement.controller;

import com.example.employeemanagement.config.SecurityConfig;
import com.example.employeemanagement.util.JwtUtil;
import com.example.employeemanagement.entity.FileInfo;
import com.example.employeemanagement.entity.User;
import com.example.employeemanagement.mapper.FileInfoMapper;
import com.example.employeemanagement.mapper.UserMapper;
import com.example.employeemanagement.security.JwtAuthenticationEntryPoint;
import com.example.employeemanagement.security.JwtAuthenticationFilter;
import com.example.employeemanagement.service.EmployeeService;
import com.example.employeemanagement.service.FileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@org.springframework.test.context.TestPropertySource(properties = "jwt.secret=TEST-ONLY-jwt-signing-key-not-for-production-2026-0123456789abcdef")
@WebMvcTest({EmployeeController.class, FileController.class})
@Import({JwtUtil.class, SecurityConfig.class, JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class, FileService.class,
        AuthorizationRegressionTest.TestWebSecurity.class})
class AuthorizationRegressionTest {

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class TestWebSecurity {
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity()).build();
    }

    @MockitoBean
    private EmployeeService employeeService;

    @MockitoBean
    private FileInfoMapper fileInfoMapper;

    @MockitoBean
    private UserMapper userMapper;

    @TempDir
    static Path directory;

    @DynamicPropertySource
    static void fileStorageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.upload-dir", () -> directory.toString());
    }

    @Test
    void batchDelete_WithoutDeleteAuthority_Returns403BeforeService() throws Exception {
        mockMvc.perform(delete("/employees/batch")
                        .with(user("reader").authorities(new SimpleGrantedAuthority("employee:view")))
                        .contentType(MediaType.APPLICATION_JSON).content("[9401,9402]"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(employeeService);
    }

    @Test
    void batchDelete_WithDeleteAuthority_CallsService() throws Exception {
        when(employeeService.deleteBatch(List.of(9401, 9402))).thenReturn(2);

        mockMvc.perform(delete("/employees/batch")
                        .with(user("deleter").authorities(new SimpleGrantedAuthority("employee:delete")))
                        .contentType(MediaType.APPLICATION_JSON).content("[9401,9402]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(2));

        verify(employeeService).deleteBatch(List.of(9401, 9402));
    }

    @Test
    void download_OwnFile_ReturnsContent() throws Exception {
        prepareFile(1);
        prepareUser("alice", 1);

        mockMvc.perform(get("/files/8/download").with(user("alice").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(content().string("batch-2-file"));

        verify(userMapper).findByUsername("alice");
        verify(fileInfoMapper).findById(8L);
    }

    @Test
    void download_OtherUsersFile_Returns403() throws Exception {
        prepareFile(2);
        prepareUser("alice", 1);

        mockMvc.perform(get("/files/8/download").with(user("alice").roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        verify(userMapper).findByUsername("alice");
        verify(fileInfoMapper).findById(8L);
    }

    @Test
    void download_AdminOtherUsersFile_ReturnsContent() throws Exception {
        prepareFile(2);

        mockMvc.perform(get("/files/8/download").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string("batch-2-file"));

        verify(fileInfoMapper).findById(8L);
        verifyNoInteractions(userMapper);
    }

    private void prepareUser(String username, int id) {
        User currentUser = new User();
        currentUser.setId(id);
        when(userMapper.findByUsername(username)).thenReturn(currentUser);
    }

    private void prepareFile(int ownerId) throws Exception {
        Path file = directory.resolve("download.pdf");
        Files.writeString(file, "batch-2-file");
        FileInfo info = new FileInfo();
        info.setId(8L);
        info.setUploadUserId(ownerId);
        info.setStoredName("download.pdf");
        info.setOriginalName("download.pdf");
        info.setContentType("application/pdf");
        when(fileInfoMapper.findById(8L)).thenReturn(info);
    }
}
