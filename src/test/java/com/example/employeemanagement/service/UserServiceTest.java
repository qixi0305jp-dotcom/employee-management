package com.example.employeemanagement.service;

import com.example.employeemanagement.mapper.UserMapper;
import com.example.employeemanagement.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock UserMapper userMapper;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtUtil jwtUtil;
    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(userMapper, passwordEncoder, jwtUtil);
    }

    @Test
    void findPermissionsByUsername_ReturnsMapperListUnchanged() {
        List<String> permissions = List.of("employee:view", "employee:add");
        when(userMapper.findPermissionsByUsername("alice")).thenReturn(permissions);

        assertSame(permissions, service.findPermissionsByUsername("alice"));

        verify(userMapper).findPermissionsByUsername("alice");
        verifyNoMoreInteractions(userMapper);
        verifyNoInteractions(passwordEncoder, jwtUtil);
    }

    @Test
    void findPermissionsByUsername_PreservesEmptyList() {
        List<String> permissions = List.of();
        when(userMapper.findPermissionsByUsername("missing-user")).thenReturn(permissions);

        List<String> result = service.findPermissionsByUsername("missing-user");

        assertSame(permissions, result);
        assertTrue(result.isEmpty());
        verify(userMapper).findPermissionsByUsername("missing-user");
        verifyNoMoreInteractions(userMapper);
        verifyNoInteractions(passwordEncoder, jwtUtil);
    }
}
