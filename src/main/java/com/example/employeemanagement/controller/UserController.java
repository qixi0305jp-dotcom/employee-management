package com.example.employeemanagement.controller;

import com.example.employeemanagement.common.Result;
import com.example.employeemanagement.dto.LoginDTO;
import com.example.employeemanagement.service.UserService;
import com.example.employeemanagement.vo.LoginVO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(
            UserService userService) {

        this.userService = userService;
    }
    @PostMapping("/login")
    public Result<LoginVO> login(
            @Valid @RequestBody LoginDTO loginDTO) {

        LoginVO loginVO = userService.login(loginDTO);


        return Result.success(loginVO);
    }

    @GetMapping("/{username}/permissions")
    @PreAuthorize("#username == authentication.name or hasAuthority('ROLE_ADMIN')")
    public Result<List<String>> findPermissions(
            @PathVariable String username) {

        return Result.success(
                userService.findPermissionsByUsername(username)
        );
    }
}
