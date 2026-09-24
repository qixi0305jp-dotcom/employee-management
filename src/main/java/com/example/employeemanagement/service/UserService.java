package com.example.employeemanagement.service;

import com.example.employeemanagement.dto.LoginDTO;
import com.example.employeemanagement.entity.User;
import com.example.employeemanagement.exception.BusinessException;
import com.example.employeemanagement.mapper.UserMapper;
import com.example.employeemanagement.util.JwtUtil;
import com.example.employeemanagement.vo.LoginVO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@Service
public class UserService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;

    private final PasswordEncoder passwordEncoder;

    public UserService(UserMapper userMapper,
                       PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
    }

    public List<String> findPermissionsByUsername(String username) {
        return userMapper.findPermissionsByUsername(username);
    }

    public LoginVO login(LoginDTO loginDTO) {

        User user = userMapper.findByUsername(loginDTO.getUsername());

        log.debug("开始执行登录业务，username={}",
                loginDTO.getUsername());

        log.info("用户尝试登录，username={}",
                loginDTO.getUsername());

        if (user == null) {
            log.warn("登录失败，用户不存在，username={}",
                    loginDTO.getUsername());
            throw new BusinessException("用户不存在");
        }

        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            log.warn("登录失败，密码错误，username={}",
                    loginDTO.getUsername());
            throw new BusinessException("密码错误");
        }

        LoginVO loginVO = new LoginVO();

        loginVO.setId(user.getId());
        loginVO.setUsername(user.getUsername());
        loginVO.setName(user.getName());
        loginVO.setRole(user.getRole());

        // 生成 JWT
        String token = jwtUtil.generateToken(
                user.getId(),
                user.getUsername(),
                user.getRole()
        );

        loginVO.setToken(token);
        log.info("用户登录成功，username={}",
                loginDTO.getUsername());
        return loginVO;
    }
}