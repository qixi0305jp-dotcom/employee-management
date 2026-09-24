package com.example.employeemanagement.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class Employee {

    private Integer id;
    private String name;
    private String gender;
    private Integer age;
    private String phone;
    private String email;
    private BigDecimal salary;
    private Integer departmentId;
    private LocalDate hireDate;
}