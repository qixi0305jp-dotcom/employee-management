package com.example.employeemanagement.controller;

import com.example.employeemanagement.common.Result;
import com.example.employeemanagement.dto.EmployeeDTO;
import com.example.employeemanagement.dto.EmployeePatchDTO;
import com.example.employeemanagement.entity.Employee;
import com.example.employeemanagement.service.EmployeeService;
import com.example.employeemanagement.vo.EmployeePageVO;
import com.example.employeemanagement.vo.EmployeeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "员工管理", description = "员工相关接口")
@RestController
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }


    @Operation(summary = "查询所有员工")
    @PreAuthorize("hasAuthority('employee:view')")
    @GetMapping("/employees")
    public Result<List<Employee>> findAll() {

        return Result.success(
                employeeService.findAll()
        );
    }

    @Operation(summary = "根据ID查询员工")
    @PreAuthorize("hasAuthority('employee:view')")
    @GetMapping("/employees/{id}")
    public Result<Employee> findById(
            @Parameter(description = "员工ID", example = "1")
            @PathVariable Integer id) {

        return Result.success(
                employeeService.findById(id)
        );
    }

    @Operation(summary = "新增员工")
    @PreAuthorize("hasAuthority('employee:add')")
    @PostMapping("/employees")
    public Result<Void> add(
            @Valid @RequestBody EmployeeDTO employeeDTO) {

        Employee employee = toEmployee(employeeDTO);

        employeeService.add(employee);

        return Result.success();
    }

    @Operation(summary = "更新员工")
    @PreAuthorize("hasAuthority('employee:update')")
    @PutMapping("/employees/{id}")
    public Result<Void> update(
            @PathVariable Integer id,
            @Valid @RequestBody EmployeeDTO employeeDTO) {

        Employee employee = toEmployee(employeeDTO);
        employee.setId(id);

        employeeService.update(employee);

        return Result.success();
    }


    @PreAuthorize("hasAuthority('employee:delete')")
    @Operation(summary = "删除员工")
    @DeleteMapping("/employees/{id}")
    public Result<Void> deleteById(
            @PathVariable Integer id) {

        employeeService.deleteById(id);

        return Result.success();
    }

    @Operation(summary = "分页查询员工")
    @PreAuthorize("hasAuthority('employee:view')")
    @GetMapping("/employees/page")
    public Result<EmployeePageVO> findByPage(
            @RequestParam Integer page,
            @RequestParam Integer pageSize) {

        return Result.success(
                employeeService.findByPage(page, pageSize)
        );
    }

    @Operation(summary = "多条件查询员工")
    @PreAuthorize("hasAuthority('employee:view')")
    @GetMapping("/employees/search")
    public Result<List<Employee>> search(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) Integer departmentId) {

        return Result.success(
                employeeService.search(name, gender, departmentId)
        );
    }

    @Operation(summary = "多条件分页查询员工")
    @PreAuthorize("hasAuthority('employee:view')")
    @GetMapping("/employees/search-page")
    public Result<EmployeePageVO> searchPage(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) Integer departmentId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {

        return Result.success(
                employeeService.searchPage(
                        name, gender, departmentId, page, pageSize
                )
        );
    }

    @Operation(summary = "查询员工及部门信息")
    @PreAuthorize("hasAuthority('employee:view')")
    @GetMapping("/employees/with-department")
    public Result<List<EmployeeVO>> findAllWithDepartment() {

        return Result.success(
                employeeService.findAllWithDepartment()
        );
    }

    @Operation(summary = "多条件查询员工及部门信息")
    @PreAuthorize("hasAuthority('employee:view')")
    @GetMapping("/employees/search-with-department")
    public Result<List<EmployeeVO>> searchWithDepartment(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) String departmentName) {

        return Result.success(
                employeeService.searchWithDepartment(
                        name, gender, departmentName
                )
        );
    }

    @Operation(summary = "部分更新员工")
    @PreAuthorize("hasAuthority('employee:update')")
    @PatchMapping("/employees/{id}")
    public Result<Void> updateSelective(
            @PathVariable Integer id,
            @Valid @RequestBody EmployeePatchDTO employeeDTO) {

        Employee employee = new Employee();
        employee.setName(employeeDTO.getName());
        employee.setGender(employeeDTO.getGender());
        employee.setAge(employeeDTO.getAge());
        employee.setPhone(employeeDTO.getPhone());
        employee.setEmail(employeeDTO.getEmail());
        employee.setSalary(employeeDTO.getSalary());
        employee.setDepartmentId(employeeDTO.getDepartmentId());
        employee.setHireDate(employeeDTO.getHireDate());
        employee.setId(id);

        employeeService.updateSelective(employee);

        return Result.success();
    }

    @Operation(summary = "批量删除员工")
    @PreAuthorize("hasAuthority('employee:delete')")
    @DeleteMapping("/employees/batch")
    public Result<Integer> deleteBatch(
            @RequestBody List<Integer> ids) {

        int result = employeeService.deleteBatch(ids);

        return Result.success(result);
    }

    @Operation(summary = "查询员工详细信息")
    @PreAuthorize("hasAuthority('employee:view')")
    @GetMapping("/employees/{id}/detail")
    public Result<EmployeeVO> findDetailById(
            @Parameter(description = "员工ID", example = "1")
            @PathVariable Integer id) {

        EmployeeVO employeeVO =
                employeeService.findDetailById(id);

        return Result.success(employeeVO);
    }


    @PreAuthorize("hasAuthority('employee:view')")
    @GetMapping("/employees/my-scope")
    public Result<List<Employee>> findByMyScope(
            Authentication authentication) {

        String username = authentication.getName();

        boolean isAdmin =
                authentication.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        return Result.success(
                employeeService.findByUserScope(username, isAdmin)
        );
    }
    private Employee toEmployee(EmployeeDTO dto) {
        Employee employee = new Employee();

        employee.setName(dto.getName());
        employee.setGender(dto.getGender());
        employee.setAge(dto.getAge());
        employee.setPhone(dto.getPhone());
        employee.setEmail(dto.getEmail());
        employee.setSalary(dto.getSalary());
        employee.setDepartmentId(dto.getDepartmentId());
        employee.setHireDate(dto.getHireDate());
        return employee;
    }
}
