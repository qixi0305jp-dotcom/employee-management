package com.example.employeemanagement.service;

import com.example.employeemanagement.entity.Department;
import com.example.employeemanagement.exception.BusinessException;
import com.example.employeemanagement.exception.ResourceNotFoundException;
import com.example.employeemanagement.mapper.DepartmentMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DepartmentService {

    private final DepartmentMapper departmentMapper;

    public DepartmentService(DepartmentMapper departmentMapper) {
        this.departmentMapper = departmentMapper;
    }

    public List<Department> findAll() {
        return departmentMapper.findAll();
    }

    public Department findById(Integer id) {
        validateId(id);
        Department department = departmentMapper.findById(id);
        if (department == null) {
            throw new ResourceNotFoundException("部门不存在");
        }
        return department;
    }

    public int add(Department department) {
        int rows = departmentMapper.insert(department);
        if (rows == 0) {
            throw new IllegalStateException("部门新增未写入记录");
        }
        return rows;
    }

    public int update(Department department) {
        validateId(department.getId());
        int rows = departmentMapper.update(department);
        // 0 也可能表示值未改变，查询确认后再判断资源是否不存在。
        if (rows == 0 && departmentMapper.findById(department.getId()) == null) {
            throw new ResourceNotFoundException("部门不存在");
        }
        return rows;
    }

    public int deleteById(Integer id) {
        validateId(id);
        int rows = departmentMapper.deleteById(id);
        if (rows == 0) {
            throw new ResourceNotFoundException("部门不存在");
        }
        return rows;
    }

    private void validateId(Integer id) {
        if (id == null || id <= 0) {
            throw new BusinessException("部门ID必须为正整数");
        }
    }
}
