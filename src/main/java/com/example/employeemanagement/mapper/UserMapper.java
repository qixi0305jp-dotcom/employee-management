package com.example.employeemanagement.mapper;

import com.example.employeemanagement.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserMapper {

    @Select("SELECT * FROM user WHERE username = #{username}")
    User findByUsername(String username);

    @Select("""
        SELECT DISTINCT p.permission_code
        FROM user u
        JOIN user_role ur
            ON u.id = ur.user_id
        JOIN role r
            ON ur.role_id = r.id
        JOIN role_permission rp
            ON r.id = rp.role_id
        JOIN permission p
            ON rp.permission_id = p.id
        WHERE u.username = #{username}
        """)
    List<String> findPermissionsByUsername(String username);

}