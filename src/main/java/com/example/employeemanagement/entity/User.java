package com.example.employeemanagement.entity;

import lombok.Data;

@Data
public class User {

    private Integer id;
    private String username;
    private String password;
    private String name;
    private String role;

    private Integer departmentId;
}