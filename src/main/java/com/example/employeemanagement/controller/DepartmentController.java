package com.example.employeemanagement.controller;

import com.example.employeemanagement.entity.Department;
import com.example.employeemanagement.common.Result;
import com.example.employeemanagement.service.DepartmentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    // 查询全部
    @PreAuthorize("hasAuthority('department:view')")
    @GetMapping("/departments")
    public Result<List<Department>> findAll() {
        return Result.success(departmentService.findAll());
    }

    // 根据ID查询
    @PreAuthorize("hasAuthority('department:view')")
    @GetMapping("/departments/{id}")
    public Result<Department> findById(@PathVariable Integer id) {
        return Result.success(departmentService.findById(id));
    }

    // 新增
    @PreAuthorize("hasAuthority('department:add')")
    @PostMapping("/departments")
    public Result<Void> add(@RequestBody Department department) {
        departmentService.add(department);
        return Result.success();
    }

    // 修改
    @PreAuthorize("hasAuthority('department:update')")
    @PutMapping("/departments/{id}")
    public Result<Void> update(
            @PathVariable Integer id,
            @RequestBody Department department) {

        department.setId(id);

        departmentService.update(department);
        return Result.success();
    }

    // 删除
    @PreAuthorize("hasAuthority('department:delete')")
    @DeleteMapping("/departments/{id}")
    public Result<Void> deleteById(@PathVariable Integer id) {
        departmentService.deleteById(id);
        return Result.success();
    }
}
