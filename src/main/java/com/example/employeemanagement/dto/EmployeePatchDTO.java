package com.example.employeemanagement.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class EmployeePatchDTO {
    // null 表示不修改；提供字符串时必须包含非空白字符。
    @Pattern(regexp = "(?s).*[^\\p{javaWhitespace}].*", message = "员工姓名不能为空")
    private String name;

    @Pattern(regexp = "(?s).*[^\\p{javaWhitespace}].*", message = "性别不能为空")
    private String gender;

    @Min(value = 18, message = "年龄不能小于18岁")
    @Max(value = 65, message = "年龄不能大于65岁")
    private Integer age;

    private String phone;

    @Email(message = "邮箱格式不正确")
    private String email;

    private BigDecimal salary;
    private Integer departmentId;
    private LocalDate hireDate;
}
