package com.example.employeemanagement.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class EmployeeVO {

    private Integer id;
    private String name;
    private String gender;
    private Integer age;
    private String phone;
    private String email;
    private BigDecimal salary;

    private Integer departmentId;

    // JOIN查询出来的部门名称
    private String departmentName;

    private LocalDate hireDate;
}