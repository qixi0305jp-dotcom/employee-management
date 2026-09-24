package com.example.employeemanagement.service;

import com.example.employeemanagement.entity.Employee;
import com.example.employeemanagement.exception.BusinessException;
import com.example.employeemanagement.mapper.EmployeeMapper;
import com.example.employeemanagement.mapper.UserMapper;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmployeeCacheEvictionTest {
    private EmployeeMapper mapper;
    private StringRedisTemplate redis;
    private EmployeeService service;
    private Employee employee;

    @BeforeEach
    void setUp() {
        mapper = mock(EmployeeMapper.class);
        redis = mock(StringRedisTemplate.class);
        service = new EmployeeService(mapper, mock(UserMapper.class), redis, JsonMapper.builder().build());
        employee = new Employee();
        employee.setId(42);
        employee.setName("员工");
    }

    @Test
    void addEvictsGeneratedIdAfterInsert() {
        employee.setId(null);
        when(mapper.insert(employee)).thenAnswer(invocation -> {
            employee.setId(123);
            return 1;
        });
        service.add(employee);
        var order = inOrder(mapper, redis);
        order.verify(mapper).insert(employee);
        order.verify(redis).delete("employee:123");
        verifyNoMoreInteractions(mapper, redis);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "PATCH", "DELETE"})
    void successfulWriteEvictsOnlyTargetKeyAfterMapper(String operation) {
        stubWrite(operation, 1);
        write(operation);
        var order = inOrder(mapper, redis);
        switch (operation) {
            case "PUT" -> order.verify(mapper).update(employee);
            case "PATCH" -> order.verify(mapper).updateSelective(employee);
            default -> order.verify(mapper).deleteById(42);
        }
        order.verify(redis).delete("employee:42");
        verifyNoMoreInteractions(mapper, redis);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3})
    void batchEvictsEveryRequestedIdRegardlessOfAffectedRows(int rows) {
        List<Integer> ids = List.of(1, 2, 999999);
        when(mapper.deleteBatch(ids)).thenReturn(rows);
        assertEquals(rows, service.deleteBatch(ids));
        var order = inOrder(mapper, redis);
        order.verify(mapper).deleteBatch(ids);
        for (Integer id : ids) order.verify(redis).delete("employee:" + id);
        verifyNoMoreInteractions(mapper, redis);
    }

    @Test
    void duplicateIdsRemainAllowedAndAreEvicted() {
        List<Integer> ids = List.of(1, 1, 2);
        when(mapper.deleteBatch(ids)).thenReturn(2);
        assertEquals(2, service.deleteBatch(ids));
        verify(mapper).deleteBatch(ids);
        verify(redis, times(2)).delete("employee:1");
        verify(redis).delete("employee:2");
        verifyNoMoreInteractions(mapper, redis);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADD", "PUT", "PATCH", "DELETE"})
    void zeroRowsForSingleWriteDoesNotEvict(String operation) {
        stubWrite(operation, 0);
        assertThrows(BusinessException.class, () -> write(operation));
        verifyNoInteractions(redis);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADD", "PUT", "PATCH", "DELETE", "BATCH"})
    void mapperFailureDoesNotEvict(String operation) {
        var failure = new DataAccessResourceFailureException("database unavailable");
        switch (operation) {
            case "ADD" -> when(mapper.insert(employee)).thenThrow(failure);
            case "PUT" -> when(mapper.update(employee)).thenThrow(failure);
            case "PATCH" -> when(mapper.updateSelective(employee)).thenThrow(failure);
            case "DELETE" -> when(mapper.deleteById(42)).thenThrow(failure);
            default -> when(mapper.deleteBatch(List.of(42))).thenThrow(failure);
        }
        assertSame(failure, assertThrows(DataAccessResourceFailureException.class, () -> write(operation)));
        verifyNoInteractions(redis);
    }

    @Test
    void emptyPatchDoesNotWriteOrEvict() {
        employee.setName(null);
        assertThrows(BusinessException.class, () -> service.updateSelective(employee));
        verifyNoInteractions(mapper, redis);
    }

    @Test
    void invalidBatchDoesNotWriteOrEvict() {
        assertThrows(BusinessException.class, () -> service.deleteBatch(List.of(1, -1)));
        verifyNoInteractions(mapper, redis);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADD", "PUT", "PATCH", "DELETE", "BATCH"})
    void redisDeleteFailureDoesNotFailSuccessfulWrite(String operation) {
        stubWrite(operation, 1);
        when(redis.delete("employee:42")).thenThrow(new RedisConnectionFailureException("private command data"));
        assertDoesNotThrow(() -> {
            if (operation.equals("BATCH")) assertEquals(1, service.deleteBatch(List.of(42)));
            else write(operation);
        });
        verifySingleWrite(operation);
        verify(redis).delete("employee:42");
        verifyNoMoreInteractions(mapper, redis);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADD", "PUT", "PATCH", "DELETE", "BATCH"})
    void absentCacheIsNormalWithoutErrorLog(String operation) {
        stubWrite(operation, 1);
        when(redis.delete("employee:42")).thenReturn(false);
        Logger logger = (Logger) LoggerFactory.getLogger(EmployeeService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            if (operation.equals("BATCH")) assertEquals(1, service.deleteBatch(List.of(42)));
            else assertDoesNotThrow(() -> write(operation));
            assertTrue(appender.list.stream().noneMatch(event -> event.getLevel().equals(Level.ERROR)));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
        verifySingleWrite(operation);
        verify(redis).delete("employee:42");
        verifyNoMoreInteractions(mapper, redis);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void batchContinuesAfterMiddleEvictionFailure(int rows) {
        List<Integer> ids = List.of(1, 2, 3);
        when(mapper.deleteBatch(ids)).thenReturn(rows);
        when(redis.delete("employee:2")).thenThrow(new RedisConnectionFailureException("unavailable"));
        assertEquals(rows, service.deleteBatch(ids));
        var order = inOrder(mapper, redis);
        order.verify(mapper).deleteBatch(ids);
        order.verify(redis).delete("employee:1");
        order.verify(redis).delete("employee:2");
        order.verify(redis).delete("employee:3");
        verifyNoMoreInteractions(mapper, redis);
    }

    @Test
    void evictionFailureLogsContextWithoutExceptionMessage() {
        when(mapper.update(employee)).thenReturn(1);
        when(redis.delete("employee:42")).thenThrow(new RedisConnectionFailureException("private command data"));
        Logger logger = (Logger) LoggerFactory.getLogger(EmployeeService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            service.update(employee);
            assertEquals(1, appender.list.size());
            ILoggingEvent event = appender.list.getFirst();
            assertEquals(Level.ERROR, event.getLevel());
            assertTrue(event.getFormattedMessage().contains("employeeId=42"));
            assertTrue(event.getFormattedMessage().contains("operation=update"));
            assertTrue(event.getFormattedMessage().contains(RedisConnectionFailureException.class.getName()));
            assertFalse(event.getFormattedMessage().contains("private command data"));
            assertNull(event.getThrowableProxy());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void unrelatedProgrammingErrorIsNotSwallowed() {
        when(mapper.update(employee)).thenReturn(1);
        var failure = new IllegalStateException("programming error");
        when(redis.delete("employee:42")).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> service.update(employee)));
    }

    private void verifySingleWrite(String operation) {
        switch (operation) {
            case "ADD" -> verify(mapper).insert(employee);
            case "PUT" -> verify(mapper).update(employee);
            case "PATCH" -> verify(mapper).updateSelective(employee);
            case "DELETE" -> verify(mapper).deleteById(42);
            default -> verify(mapper).deleteBatch(List.of(42));
        }
    }

    private void stubWrite(String operation, int rows) {
        switch (operation) {
            case "ADD" -> when(mapper.insert(employee)).thenReturn(rows);
            case "PUT" -> when(mapper.update(employee)).thenReturn(rows);
            case "PATCH" -> when(mapper.updateSelective(employee)).thenReturn(rows);
            case "BATCH" -> when(mapper.deleteBatch(List.of(42))).thenReturn(rows);
            default -> when(mapper.deleteById(42)).thenReturn(rows);
        }
    }

    private void write(String operation) {
        switch (operation) {
            case "ADD" -> service.add(employee);
            case "PUT" -> service.update(employee);
            case "PATCH" -> service.updateSelective(employee);
            case "DELETE" -> service.deleteById(42);
            default -> service.deleteBatch(List.of(42));
        }
    }
}
