package com.example.employeemanagement.integration;

import com.example.employeemanagement.entity.FileInfo;
import com.example.employeemanagement.entity.User;
import com.example.employeemanagement.mapper.FileInfoMapper;
import com.example.employeemanagement.mapper.UserMapper;
import com.example.employeemanagement.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


import static org.junit.jupiter.api.Assertions.*;

@AutoConfigureMockMvc
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FileIntegrationTest {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private FileInfoMapper fileInfoMapper;

    @Test
    @Sql("/sql/security-test-data.sql")
    void testSecurityTestData() {

        User user = userMapper.findByUsername("test_user_a");

        assertNotNull(user);
        assertEquals(9201, user.getId());
        assertEquals("USER", user.getRole());

        FileInfo file = fileInfoMapper.findById(9301L);

        assertNotNull(file);
        assertEquals(9201, file.getUploadUserId());

        System.out.println("测试用户：" + user.getUsername());
        System.out.println("测试文件：" + file.getOriginalName());
    }

    @Test
    @Sql("/sql/security-test-data.sql")
    void testFileDetail_UserCanAccessOwnFile() throws Exception {

        String token = jwtUtil.generateToken(
                9201,
                "test_user_a",
                "USER"
        );

        mockMvc.perform(get("/files/9301")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @Sql("/sql/security-test-data.sql")
    void testFileDetail_UserCannotAccessOtherUsersFile() throws Exception {

        String token = jwtUtil.generateToken(
                9201,
                "test_user_a",
                "USER"
        );

        mockMvc.perform(get("/files/9302")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @Sql("/sql/security-test-data.sql")
    void testFileDetail_AdminCanAccessOtherUsersFile() throws Exception {

        String token = jwtUtil.generateToken(
                9203,
                "test_admin",
                "ADMIN"
        );

        mockMvc.perform(get("/files/9302")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @Sql("/sql/security-test-data.sql")
    void testFileDetail_NoToken_Returns401() throws Exception {

        mockMvc.perform(get("/files/9301"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Sql("/sql/security-test-data.sql")
    void testFileDetail_TamperedToken_Returns401() throws Exception {

        String token = jwtUtil.generateToken(
                9201,
                "test_user_a",
                "USER"
        );

        // 故意篡改 Token
        String tamperedToken = "x" + token.substring(1);

        mockMvc.perform(get("/files/9301")
                        .header("Authorization", "Bearer " + tamperedToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Sql("/sql/security-test-data.sql")
    void testFileDetail_ExpiredToken_Returns401() throws Exception {

        String token = jwtUtil.generateTokenWithExpireTime(
                9201,
                "test_user_a",
                "USER",
                100
        );

        Thread.sleep(200);

        mockMvc.perform(get("/files/9301")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}