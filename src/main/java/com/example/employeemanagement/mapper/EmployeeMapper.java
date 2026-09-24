package com.example.employeemanagement.mapper;

import com.example.employeemanagement.entity.Employee;
import com.example.employeemanagement.vo.EmployeeVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface EmployeeMapper {

    @Select("SELECT * FROM employee")
    List<Employee> findAll();

    @Select("SELECT * FROM employee WHERE id = #{id}")
    Employee findById(Integer id);

    @Insert("""
        INSERT INTO employee
        (name, gender, age, phone, email, salary, department_id, hire_date)
        VALUES
        (#{name}, #{gender}, #{age}, #{phone}, #{email}, #{salary}, #{departmentId}, #{hireDate})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Employee employee);

    @Update("""
        UPDATE employee
        SET name = #{name},
            gender = #{gender},
            age = #{age},
            phone = #{phone},
            email = #{email},
            salary = #{salary},
            department_id = #{departmentId},
            hire_date = #{hireDate}
        WHERE id = #{id}
        """)
    int update(Employee employee);

    @Delete("DELETE FROM employee WHERE id = #{id}")
    int deleteById(Integer id);

    //查询当前页
    @Select("""
        SELECT *
        FROM employee
        ORDER BY id ASC
        LIMIT #{offset}, #{pageSize}
        """)
    List<Employee> findByPage(
            @Param("offset") Integer offset,
            @Param("pageSize") Integer pageSize
    );

    //查询总条数
    @Select("SELECT COUNT(*) FROM employee")
    Long count();

    List<Employee> search(
            @Param("name") String name,
            @Param("gender") String gender,
            @Param("departmentId") Integer departmentId
    );


    // 与 countSearch 配套返回当前页记录和符合条件的总数。
    List<Employee> searchPage(
            @Param("name") String name,
            @Param("gender") String gender,
            @Param("departmentId") Integer departmentId,
            @Param("offset") Integer offset,
            @Param("pageSize") Integer pageSize
    );

    Long countSearch(
            @Param("name") String name,
            @Param("gender") String gender,
            @Param("departmentId") Integer departmentId
    );

    List<EmployeeVO> findAllWithDepartment();

    List<EmployeeVO> searchWithDepartment(
            @Param("name") String name,
            @Param("gender") String gender,
            @Param("departmentName") String departmentName
    );

    int updateSelective(Employee employee);

    int deleteBatch(@Param("ids") List<Integer> ids);

    EmployeeVO findDetailById(@Param("id") Integer id);

    List<Employee> findByDepartmentId(Integer departmentId);
}