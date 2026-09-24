package com.example.employeemanagement.controller;

import com.example.employeemanagement.exception.BusinessException;
import com.example.employeemanagement.exception.GlobalExceptionHandler;
import com.example.employeemanagement.mapper.EmployeeMapper;
import com.example.employeemanagement.mapper.UserMapper;
import com.example.employeemanagement.service.EmployeeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 输入边界使用真实 Controller/Service；方法级授权由 AuthorizationRegressionTest 覆盖。
class EmployeeBatchDeleteInputTest {
    private EmployeeMapper mapper;
    private EmployeeService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mapper = mock(EmployeeMapper.class);
        service = new EmployeeService(mapper, mock(UserMapper.class),
                mock(StringRedisTemplate.class), JsonMapper.builder().build());
        mvc = MockMvcBuilders.standaloneSetup(new EmployeeController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "null", "[null]", "[0]", "[-1]", "[1,2,-1]", "[1,null]"})
    void invalidRequestRejectsWholeBatchBeforeMapper(String body) throws Exception {
        mvc.perform(delete("/employees/batch").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(mapper);
    }

    @Test
    void serviceAlsoRejectsNullForNonHttpCallers() {
        assertThrows(BusinessException.class, () -> service.deleteBatch(null));
        verifyNoInteractions(mapper);
    }

    @Test
    void validIdsReturnActualDeletedCount() throws Exception {
        assertSuccessfulBatch("[1,2,3]", List.of(1, 2, 3), 2);
    }

    @Test
    void duplicatePositiveIdsAreAllowed() throws Exception {
        assertSuccessfulBatch("[1,1,2]", List.of(1, 1, 2), 2);
    }

    @Test
    void missingPositiveIdsReturnZeroWithoutPreQuery() throws Exception {
        assertSuccessfulBatch("[999999]", List.of(999999), 0);
    }

    private void assertSuccessfulBatch(String body, List<Integer> ids, int deleted) throws Exception {
        when(mapper.deleteBatch(ids)).thenReturn(deleted);
        mvc.perform(delete("/employees/batch").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(deleted));
        verify(mapper).deleteBatch(ids);
        verifyNoMoreInteractions(mapper);
    }
}
