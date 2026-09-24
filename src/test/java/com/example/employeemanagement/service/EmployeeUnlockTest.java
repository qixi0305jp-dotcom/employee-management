package com.example.employeemanagement.service;

import com.example.employeemanagement.entity.Employee;
import com.example.employeemanagement.exception.BusinessException;
import com.example.employeemanagement.mapper.EmployeeMapper;
import com.example.employeemanagement.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmployeeUnlockTest {
    private EmployeeMapper mapper;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private EmployeeService service;
    private Employee employee;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mapper = mock(EmployeeMapper.class);
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(eq("lock:employee:42"), anyString(), eq(10L), eq(TimeUnit.SECONDS))).thenReturn(true);
        service = new EmployeeService(mapper, mock(UserMapper.class), redis, JsonMapper.builder().build());
        employee = new Employee();
        employee.setId(42);
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 0})
    void successWithNormalUnlock(long result) {
        when(mapper.findById(42)).thenReturn(employee);
        when(redis.execute(any(RedisScript.class), eq(List.of("lock:employee:42")), anyString())).thenReturn(result);
        assertSame(employee, service.findById(42));
        verify(redis).execute(any(RedisScript.class), eq(List.of("lock:employee:42")), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"connection", "timeout", "system"})
    void successSurvivesRedisUnlockFailure(String type) {
        RuntimeException failure = switch (type) {
            case "connection" -> new RedisConnectionFailureException("unlock failure");
            case "timeout" -> new QueryTimeoutException("unlock failure");
            default -> new RedisSystemException("unlock failure", new RuntimeException());
        };
        failUnlock(failure);
        when(mapper.findById(42)).thenReturn(employee);
        assertSame(employee, service.findById(42));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void databaseFailureRemainsOriginalWithEitherUnlockOutcome(boolean unlockFails) {
        var original = new DataAccessResourceFailureException("database failure");
        when(mapper.findById(42)).thenThrow(original);
        if (unlockFails) failUnlock(new RedisConnectionFailureException("unlock failure"));
        else when(redis.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(1L);
        assertSame(original, assertThrows(DataAccessResourceFailureException.class, () -> service.findById(42)));
        verify(redis).execute(any(RedisScript.class), eq(List.of("lock:employee:42")), anyString());
    }

    @Test
    void missingEmployeeKeepsBusinessException() {
        failUnlock(new QueryTimeoutException("unlock failure"));
        BusinessException failure = assertThrows(BusinessException.class, () -> service.findById(42));
        assertEquals("员工不存在", failure.getMessage());
        verify(values).set("employee:42", "", 2L, TimeUnit.MINUTES);
    }

    @Test
    void cacheSetFailureRemainsOriginal() {
        when(mapper.findById(42)).thenReturn(employee);
        var original = new RedisConnectionFailureException("cache write failure");
        doThrow(original).when(values).set(eq("employee:42"), anyString(), anyLong(), eq(TimeUnit.MINUTES));
        failUnlock(new QueryTimeoutException("unlock failure"));
        assertSame(original, assertThrows(RedisConnectionFailureException.class, () -> service.findById(42)));
    }

    @Test
    void unrelatedUnlockProgrammingErrorIsNotSwallowed() {
        when(mapper.findById(42)).thenReturn(employee);
        var failure = new IllegalStateException("programming error");
        failUnlock(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> service.findById(42)));
    }

    private void failUnlock(RuntimeException failure) {
        when(redis.execute(any(RedisScript.class), eq(List.of("lock:employee:42")), anyString())).thenThrow(failure);
    }
}
