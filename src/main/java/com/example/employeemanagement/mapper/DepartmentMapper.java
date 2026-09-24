package com.example.employeemanagement.mapper;

import com.example.employeemanagement.entity.Department;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface DepartmentMapper {

    // 查询全部部门
    @Select("SELECT * FROM department ORDER BY id")
    List<Department> findAll();

    // 根据ID查询部门
    @Select("SELECT * FROM department WHERE id = #{id}")
    Department findById(Integer id);

    // 新增部门
    @Insert("""
            INSERT INTO department (name, description)
            VALUES (#{name}, #{description})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Department department);

    // 修改部门
    @Update("""
            UPDATE department
            SET name = #{name},
                description = #{description}
            WHERE id = #{id}
            """)
    int update(Department department);

    // 删除部门
    @Delete("DELETE FROM department WHERE id = #{id}")
    int deleteById(Integer id);
}