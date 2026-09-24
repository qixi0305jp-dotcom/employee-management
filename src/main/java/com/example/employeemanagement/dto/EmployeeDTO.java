package com.example.employeemanagement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class EmployeeDTO {

    @Schema(description = "员工姓名", example = "张三")
    @NotBlank(message = "员工姓名不能为空")
    private String name;

    @Schema(description = "性别", example = "男")
    @NotBlank(message = "性别不能为空")
    private String gender;

    @Schema(description = "年龄", example = "30")
    @NotNull(message = "年龄不能为空")
    @Min(value = 18, message = "年龄不能小于18岁")
    @Max(value = 65, message = "年龄不能大于65岁")
    private Integer age;

    private String phone;

    @Schema(description = "邮箱", example = "zhangsan@example.com")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotNull(message = "工资不能为空")
    private BigDecimal salary;

    @Schema(description = "部门ID", example = "1")
    @NotNull(message = "部门不能为空")
    private Integer departmentId;

    @NotNull(message = "入职日期不能为空")
    private LocalDate hireDate;
}