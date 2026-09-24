package com.example.employeemanagement.controller;

import com.example.employeemanagement.entity.Employee;
import com.example.employeemanagement.exception.BusinessException;
import com.example.employeemanagement.exception.GlobalExceptionHandler;
import com.example.employeemanagement.mapper.EmployeeMapper;
import com.example.employeemanagement.mapper.UserMapper;
import com.example.employeemanagement.service.EmployeeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 使用真实 Controller、Service 和错误处理器；Mapper 模拟返回，不访问数据库。
class EmployeePaginationInputTest {
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
    @CsvSource({"0,10", "-1,10", "1,0", "1,-1", "1,101", "2147483647,100"})
    void bothEndpointsRejectInvalidPaginationBeforeAnyMapperCall(int page, int size) throws Exception {
        for (boolean search : new boolean[]{false, true}) {
            mvc.perform(request(search).param("page", String.valueOf(page)).param("pageSize", String.valueOf(size)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        }
        verifyNoInteractions(mapper);
    }

    @ParameterizedTest
    @CsvSource({"false,1,10,0", "false,2,10,10", "false,1,100,0", "false,1,1,0",
            "true,1,10,0", "true,2,10,10", "true,1,100,0", "true,1,1,0"})
    void validPaginationPassesOffsetAndPreservesResponse(boolean search, int page, int size, int offset) throws Exception {
        Employee employee = new Employee();
        employee.setId(42);
        stub(search, offset, size, List.of(employee), 120L);
        mvc.perform(request(search).param("page", String.valueOf(page)).param("pageSize", String.valueOf(size)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.page").value(page))
                .andExpect(jsonPath("$.data.pageSize").value(size))
                .andExpect(jsonPath("$.data.total").value(120))
                .andExpect(jsonPath("$.data.records[0].id").value(42));
        verifyQueries(search, offset, size);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void beyondLastPageRemainsSuccessful(boolean search) throws Exception {
        stub(search, 990, 10, List.of(), 3L);
        mvc.perform(request(search).param("page", "100").param("pageSize", "10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records").isEmpty());
        verifyQueries(search, 990, 10);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void largestReachableSafeOffsetIsAllowed(boolean search) throws Exception {
        stub(search, Integer.MAX_VALUE - 1, 1, List.of(), 0L);
        mvc.perform(request(search).param("page", "2147483647").param("pageSize", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isEmpty());
        verifyQueries(search, Integer.MAX_VALUE - 1, 1);
    }

    @Test
    void searchPageKeepsDefaultPagination() throws Exception {
        stub(true, 0, 10, List.of(), 0L);
        mvc.perform(request(true))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10));
        verifyQueries(true, 0, 10);
    }

    @Test
    void serviceRejectsNullPaginationBeforeMapper() {
        assertThrows(BusinessException.class, () -> service.findByPage(null, 10));
        assertThrows(BusinessException.class, () -> service.findByPage(1, null));
        assertThrows(BusinessException.class, () -> service.searchPage(null, null, null, null, 10));
        assertThrows(BusinessException.class, () -> service.searchPage(null, null, null, 1, null));
        verifyNoInteractions(mapper);
    }

    private MockHttpServletRequestBuilder request(boolean search) {
        return search ? get("/employees/search-page").param("name", "张").param("gender", "男").param("departmentId", "2")
                : get("/employees/page");
    }

    private void stub(boolean search, int offset, int size, List<Employee> records, long total) {
        if (search) {
            when(mapper.searchPage("张", "男", 2, offset, size)).thenReturn(records);
            when(mapper.countSearch("张", "男", 2)).thenReturn(total);
        } else {
            when(mapper.findByPage(offset, size)).thenReturn(records);
            when(mapper.count()).thenReturn(total);
        }
    }

    private void verifyQueries(boolean search, int offset, int size) {
        if (search) {
            verify(mapper).searchPage("张", "男", 2, offset, size);
            verify(mapper).countSearch("张", "男", 2);
        } else {
            verify(mapper).findByPage(offset, size);
            verify(mapper).count();
        }
        verifyNoMoreInteractions(mapper);
    }
}
