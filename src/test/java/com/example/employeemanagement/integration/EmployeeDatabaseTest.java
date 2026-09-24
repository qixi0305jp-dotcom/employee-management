package com.example.employeemanagement.integration;

import com.example.employeemanagement.entity.Employee;
import com.example.employeemanagement.mapper.EmployeeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EmployeeDatabaseTest {

    @Autowired
    private EmployeeMapper employeeMapper;

    private Employee testEmployee;

    @BeforeEach
    void prepareTestData() {
        testEmployee = new Employee();
        testEmployee.setName("测试员工");
        testEmployee.setGender("男");
        testEmployee.setAge(30);

        int rows = employeeMapper.insert(testEmployee);

        assertEquals(1, rows);
        assertNotNull(testEmployee.getId());
    }

    @Test
    void testFindEmployee() {
        Employee employee =
                employeeMapper.findById(testEmployee.getId());

        assertNotNull(employee);
        assertEquals("测试员工", employee.getName());
        assertEquals(30, employee.getAge());
    }

    @Test
    @Sql("/sql/employee-test-data.sql")
    void testPrepareDataWithSqlFile() {

        List<Employee> employees =
                employeeMapper.findAll();

        Employee employee =
                employees.stream()
                        .filter(e ->
                                "SQL文件测试员工"
                                        .equals(e.getName()))
                        .findFirst()
                        .orElse(null);

        assertNotNull(employee);
        assertEquals(28, employee.getAge());
    }
}