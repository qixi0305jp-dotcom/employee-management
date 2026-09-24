package com.example.employeemanagement.vo;

import lombok.Data;

@Data
public class LoginVO {

    private Integer id;
    private String username;
    private String name;
    private String role;

    private String token;
}