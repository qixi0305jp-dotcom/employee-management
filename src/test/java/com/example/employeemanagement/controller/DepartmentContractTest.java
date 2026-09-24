package com.example.employeemanagement.controller;

import com.example.employeemanagement.entity.Department;
import com.example.employeemanagement.exception.GlobalExceptionHandler;
import com.example.employeemanagement.mapper.DepartmentMapper;
import com.example.employeemanagement.service.DepartmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 聚焦业务与响应契约，使用真实 Controller、Service、异常处理器，Mock Mapper。
// standalone 不用于证明方法级授权；本批不修改现有权限注解。
class DepartmentContractTest {
    private DepartmentMapper mapper;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mapper = mock(DepartmentMapper.class);
        mvc = MockMvcBuilders.standaloneSetup(new DepartmentController(new DepartmentService(mapper)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private Department department() {
        Department department = new Department();
        department.setId(42);
        department.setName("研发");
        department.setDescription("描述");
        return department;
    }

    @Test
    void listReturnsDepartments() throws Exception {
        when(mapper.findAll()).thenReturn(List.of(department()));
        mvc.perform(get("/departments")).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data[0].id").value(42));
    }

    @Test
    void emptyListIsSuccessful() throws Exception {
        when(mapper.findAll()).thenReturn(List.of());
        mvc.perform(get("/departments")).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void existingDepartmentIsReturned() throws Exception {
        when(mapper.findById(42)).thenReturn(department());
        mvc.perform(get("/departments/42")).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.id").value(42));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "PUT", "DELETE"})
    void missingDepartmentReturns404(String method) throws Exception {
        mvc.perform(request(method, 42)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
        if (method.equals("GET")) verify(mapper).findById(42);
        if (method.equals("PUT")) {
            verify(mapper).update(department());
            verify(mapper).findById(42);
        }
        if (method.equals("DELETE")) verify(mapper).deleteById(42);
        verifyNoMoreInteractions(mapper);
    }

    @ParameterizedTest
    @CsvSource({"GET,0", "GET,-1", "PUT,0", "PUT,-1", "DELETE,0", "DELETE,-1"})
    void invalidIdReturns400BeforeMapper(String method, int id) throws Exception {
        mvc.perform(request(method, id)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(mapper);
    }

    @Test
    void updateUsesPathIdAndReturnsSuccess() throws Exception {
        when(mapper.update(any())).thenReturn(1);
        mvc.perform(request("PUT", 42)).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(mapper).update(department());
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void unchangedUpdateWithZeroRowsRemainsSuccessful() throws Exception {
        when(mapper.update(any())).thenReturn(0);
        when(mapper.findById(42)).thenReturn(department());
        mvc.perform(request("PUT", 42)).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(mapper).update(department());
        verify(mapper).findById(42);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void deleteReturnsSuccess() throws Exception {
        when(mapper.deleteById(42)).thenReturn(1);
        mvc.perform(delete("/departments/42")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(mapper).deleteById(42);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void postReturnsSuccess() throws Exception {
        when(mapper.insert(any())).thenReturn(1);
        mvc.perform(post("/departments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"研发\",\"description\":\"描述\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        Department expected = department();
        expected.setId(null);
        verify(mapper).insert(expected);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void insertZeroRowsIsUnexpectedServerError() throws Exception {
        mvc.perform(post("/departments").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value(500));
        verify(mapper).insert(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"LIST", "GET", "POST", "PUT", "DELETE", "UPDATE_LOOKUP"})
    void databaseFailureRemains500(String operation) throws Exception {
        var failure = new DataAccessResourceFailureException("private SQL connection detail");
        MockHttpServletRequestBuilder request;
        switch (operation) {
            case "LIST" -> { when(mapper.findAll()).thenThrow(failure); request = get("/departments"); }
            case "POST" -> {
                when(mapper.insert(any())).thenThrow(failure);
                request = post("/departments").contentType(MediaType.APPLICATION_JSON).content("{}");
            }
            case "PUT" -> { when(mapper.update(any())).thenThrow(failure); request = request("PUT", 42); }
            case "DELETE" -> { when(mapper.deleteById(42)).thenThrow(failure); request = request("DELETE", 42); }
            default -> {
                when(mapper.findById(42)).thenThrow(failure);
                request = request(operation.equals("UPDATE_LOOKUP") ? "PUT" : "GET", 42);
            }
        }
        mvc.perform(request).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(content().string(not(containsString("private SQL connection detail"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{"})
    void invalidRequestBodyReturns400WithoutMapper(String body) throws Exception {
        mvc.perform(put("/departments/42").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(mapper);
    }

    private MockHttpServletRequestBuilder request(String method, int id) {
        return switch (method) {
            case "GET" -> get("/departments/" + id);
            case "DELETE" -> delete("/departments/" + id);
            default -> put("/departments/" + id).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"id\":999,\"name\":\"研发\",\"description\":\"描述\"}");
        };
    }
}
