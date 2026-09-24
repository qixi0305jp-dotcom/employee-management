package com.example.employeemanagement.service;

import com.example.employeemanagement.entity.Employee;
import com.example.employeemanagement.exception.BusinessException;
import com.example.employeemanagement.mapper.EmployeeMapper;
import com.example.employeemanagement.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmployeeCacheReadTest {
    private EmployeeMapper mapper;
    private UserMapper users;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private EmployeeService service;
    private static final String JSON = "{\"id\":42,\"name\":\"cached employee\"}";

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mapper = mock(EmployeeMapper.class);
        users = mock(UserMapper.class);
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        service = new EmployeeService(mapper, users, redis, JsonMapper.builder().build());
    }

    @Test
    void firstHit_ReturnsEmployee() {
        when(values.get("employee:42")).thenReturn(JSON);
        Employee result = service.findById(42);
        assertEquals(42, result.getId());
        assertEquals("cached employee", result.getName());
        verifyFirstHitOnly();
    }

    @Test
    void firstHit_NegativeCacheThrows() {
        when(values.get("employee:42")).thenReturn("");
        BusinessException failure = assertThrows(BusinessException.class, () -> service.findById(42));
        assertEquals("员工不存在", failure.getMessage());
        verifyFirstHitOnly();
    }

    @Test
    void firstHit_JsonNullReturnsNull() {
        when(values.get("employee:42")).thenReturn("null");
        assertNull(service.findById(42));
        verifyFirstHitOnly();
    }

    @Test
    void firstHit_InvalidJsonPropagates() {
        when(values.get("employee:42")).thenReturn("{");
        assertThrows(JacksonException.class, () -> service.findById(42));
        verifyFirstHitOnly();
    }

    @Test
    void doubleCheckHit_ReturnsEmployeeAndUnlocks() {
        doubleCheck(JSON);
        Employee result = service.findById(42);
        assertEquals(42, result.getId());
        assertEquals("cached employee", result.getName());
        verifyDoubleCheckOnly();
    }

    @Test
    void doubleCheckHit_NegativeCacheThrowsAndUnlocks() {
        doubleCheck("");
        BusinessException failure = assertThrows(BusinessException.class, () -> service.findById(42));
        assertEquals("员工不存在", failure.getMessage());
        verifyDoubleCheckOnly();
    }

    @Test
    void doubleCheckHit_JsonFailureSurvivesUnlockFailure() {
        doubleCheck("{");
        when(redis.execute(any(RedisScript.class), eq(List.of("lock:employee:42")), anyString()))
                .thenThrow(new RedisConnectionFailureException("test unlock failure"));
        assertThrows(JacksonException.class, () -> service.findById(42));
        verifyDoubleCheckOnly();
    }

    private void doubleCheck(String cached) {
        when(values.get("employee:42")).thenReturn(null, cached);
        when(values.setIfAbsent(eq("lock:employee:42"), anyString(), eq(10L), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
    }

    private void verifyFirstHitOnly() {
        verify(values).get("employee:42");
        verify(redis).opsForValue();
        verifyNoMoreInteractions(values, redis);
        verifyNoInteractions(mapper, users);
    }

    private void verifyDoubleCheckOnly() {
        verify(values, times(2)).get("employee:42");
        ArgumentCaptor<String> owner = ArgumentCaptor.forClass(String.class);
        verify(values).setIfAbsent(eq("lock:employee:42"), owner.capture(), eq(10L), eq(TimeUnit.SECONDS));
        verify(redis).execute(any(RedisScript.class), eq(List.of("lock:employee:42")), eq(owner.getValue()));
        verify(redis, times(3)).opsForValue();
        verifyNoMoreInteractions(values, redis);
        verifyNoInteractions(mapper, users);
    }
}
