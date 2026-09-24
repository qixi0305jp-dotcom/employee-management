package com.example.employeemanagement.controller;

import com.example.employeemanagement.entity.Employee;
import com.example.employeemanagement.exception.BusinessException;
import com.example.employeemanagement.exception.GlobalExceptionHandler;
import com.example.employeemanagement.mapper.EmployeeMapper;
import com.example.employeemanagement.mapper.UserMapper;
import com.example.employeemanagement.service.EmployeeService;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 聚焦 MVC 输入边界，使用真实 Service 和模拟 Mapper；授权由独立安全测试覆盖。
class EmployeeUpdateInputTest {
    private EmployeeMapper mapper;
    private EmployeeService service;
    private MockMvc mvc;
    private static final String COMPLETE = """
            {"id":999,"name":" 张三 ","gender":"男","age":30,"phone":"123",
             "email":"a@example.com","salary":5000,"departmentId":2,"hireDate":"2026-01-02"}
            """;

    @BeforeEach
    void setUp() {
        mapper = mock(EmployeeMapper.class);
        service = spy(new EmployeeService(mapper, mock(UserMapper.class),
                mock(StringRedisTemplate.class), JsonMapper.builder().build()));
        mvc = MockMvcBuilders.standaloneSetup(new EmployeeController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void completePostPassesAllFieldsWithoutClientId() throws Exception {
        doNothing().when(service).add(any());
        mvc.perform(post("/employees").contentType(MediaType.APPLICATION_JSON).content(COMPLETE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
        verify(service).add(captor.capture());
        Employee employee = captor.getValue();
        assertNull(employee.getId());
        assertEquals(" 张三 ", employee.getName());
        assertEquals("男", employee.getGender());
        assertEquals(30, employee.getAge());
        assertEquals("123", employee.getPhone());
        assertEquals("a@example.com", employee.getEmail());
        assertEquals(new BigDecimal("5000"), employee.getSalary());
        assertEquals(2, employee.getDepartmentId());
        assertEquals(LocalDate.of(2026, 1, 2), employee.getHireDate());
        verifyNoInteractions(mapper);
    }

    @Test
    void completePutUsesPathIdAndAllFields() throws Exception {
        when(mapper.update(any())).thenReturn(1);
        mvc.perform(put("/employees/42").contentType(MediaType.APPLICATION_JSON).content(COMPLETE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
        verify(service).update(captor.capture());
        Employee employee = captor.getValue();
        assertEquals(42, employee.getId());
        assertEquals(" 张三 ", employee.getName());
        assertEquals("男", employee.getGender());
        assertEquals(30, employee.getAge());
        assertEquals("123", employee.getPhone());
        assertEquals("a@example.com", employee.getEmail());
        assertEquals(new BigDecimal("5000"), employee.getSalary());
        assertEquals(2, employee.getDepartmentId());
        assertEquals(LocalDate.of(2026, 1, 2), employee.getHireDate());
        verify(mapper).update(employee);
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "gender", "age", "salary", "departmentId", "hireDate"})
    void putMissingRequiredFieldIsRejected(String field) throws Exception {
        var json = JsonMapper.builder().build().readTree(COMPLETE).deepCopy();
        ((tools.jackson.databind.node.ObjectNode) json).remove(field);
        rejectPut(json.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"age\":17}", "{\"age\":66}"})
    void invalidPutIsRejected(String body) throws Exception {
        if (!body.equals("{}")) {
            int age = body.contains("17") ? 17 : 66;
            body = COMPLETE.replace("\"age\":30", "\"age\":" + age);
        }
        rejectPut(body);
    }

    private void rejectPut(String body) throws Exception {
        mvc.perform(put("/employees/42").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(service, mapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"id\":999}",
            "{\"name\":null,\"gender\":null,\"age\":null,\"phone\":null,\"email\":null,\"salary\":null,\"departmentId\":null,\"hireDate\":null}"})
    void emptyPatchIsRejectedBeforeMapper(String body) throws Exception {
        mvc.perform(patch("/employees/42").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        verify(service).updateSelective(any());
        verifyNoInteractions(mapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"age\":17}", "{\"age\":66}", "{\"name\":\"\"}",
            "{\"name\":\"   \"}", "{\"gender\":\"\"}", "{\"gender\":\" \\t\\n\"}",
            "{\"email\":\"invalid\"}"})
    void invalidPatchFieldIsRejectedBeforeService(String body) throws Exception {
        mvc.perform(patch("/employees/42").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(service, mapper);
    }

    @ParameterizedTest
    @ValueSource(ints = {18, 65})
    void singleFieldPatchPreservesOmittedFieldsAndUsesPathId(int age) throws Exception {
        when(mapper.updateSelective(any())).thenReturn(1);
        mvc.perform(patch("/employees/42").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":999,\"age\":" + age + ",\"name\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        Employee expected = new Employee();
        expected.setId(42);
        expected.setAge(age);
        verify(mapper).updateSelective(expected);
        assertSql(expected, "UPDATE employee SET age = ? WHERE id = ?", List.of("age", "id"));
    }

    @Test
    void multipleFieldPatchOnlyUpdatesProvidedFields() throws Exception {
        when(mapper.updateSelective(any())).thenReturn(1);
        mvc.perform(patch("/employees/42").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" 李四 \",\"phone\":\"\",\"salary\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        Employee expected = new Employee();
        expected.setId(42);
        expected.setName(" 李四 ");
        expected.setPhone("");
        expected.setSalary(BigDecimal.ZERO);
        verify(mapper).updateSelective(expected);
        assertSql(expected, "UPDATE employee SET name = ?, phone = ?, salary = ? WHERE id = ?",
                List.of("name", "phone", "salary", "id"));
    }

    @Test
    void serviceRejectsNullWithoutMapper() {
        assertThrows(BusinessException.class, () -> service.updateSelective(null));
        verifyNoInteractions(mapper);
    }

    private void assertSql(Employee employee, String expectedSql, List<String> parameters) throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/EmployeeMapper.xml";
        try (var stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream);
            new XMLMapperBuilder(stream, configuration, resource, configuration.getSqlFragments()).parse();
        }
        var sql = configuration.getMappedStatement(EmployeeMapper.class.getName() + ".updateSelective")
                .getBoundSql(employee);
        assertEquals(expectedSql, sql.getSql().replaceAll("\\s+", " ").trim());
        assertEquals(parameters, sql.getParameterMappings().stream().map(p -> p.getProperty()).toList());
    }
}
