package com.example.employeemanagement.vo;

import com.example.employeemanagement.entity.Employee;
import lombok.Data;

import java.util.List;

@Data
public class EmployeePageVO {

    private Long total;

    private Integer page;

    private Integer pageSize;

    private List<Employee> records;
}