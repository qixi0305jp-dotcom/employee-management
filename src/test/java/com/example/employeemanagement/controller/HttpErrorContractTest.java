package com.example.employeemanagement.controller;

import com.example.employeemanagement.config.SecurityConfig;
import com.example.employeemanagement.util.JwtUtil;
import com.example.employeemanagement.exception.BusinessException;
import com.example.employeemanagement.mapper.UserMapper;
import com.example.employeemanagement.security.JwtAuthenticationEntryPoint;
import com.example.employeemanagement.security.JwtAuthenticationFilter;
import com.example.employeemanagement.service.EmployeeService;
import com.example.employeemanagement.service.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@org.springframework.test.context.TestPropertySource(properties = "jwt.secret=TEST-ONLY-jwt-signing-key-not-for-production-2026-0123456789abcdef")
@WebMvcTest({EmployeeController.class, FileController.class})
@Import({JwtUtil.class, SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class,
        HttpErrorContractTest.TestWebSecurity.class})
class HttpErrorContractTest {
    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class TestWebSecurity { }

    @Autowired
    private WebApplicationContext context;
    @MockitoBean
    private EmployeeService employeeService;
    @MockitoBean
    private FileService fileService;
    @MockitoBean
    private UserMapper userMapper;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private void expectError(ResultActions response, int code, Class<? extends Exception> type)
            throws Exception {
        response.andExpect(status().is(code))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(result -> assertInstanceOf(type, result.getResolvedException()));
    }

    @Test
    void validation_Returns400WithFieldErrorsBeforeService() throws Exception {
        ResultActions response = mvc.perform(post("/employees")
                .with(user("creator").authorities(() -> "employee:add"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"));
        expectError(response, 400, MethodArgumentNotValidException.class);
        response.andExpect(jsonPath("$.data.name").value("员工姓名不能为空"))
                .andExpect(jsonPath("$.data.age").value("年龄不能为空"));
        verifyNoInteractions(employeeService);
    }

    @Test
    void malformedJson_Returns400() throws Exception {
        expectError(mvc.perform(post("/employees").with(user("caller"))
                .contentType(MediaType.APPLICATION_JSON).content("{broken")),
                400, HttpMessageNotReadableException.class);
        verifyNoInteractions(employeeService);
    }

    @Test
    void missingBody_Returns400() throws Exception {
        expectError(mvc.perform(post("/employees").with(user("caller"))
                .contentType(MediaType.APPLICATION_JSON)), 400, HttpMessageNotReadableException.class);
        verifyNoInteractions(employeeService);
    }

    @Test
    void invalidPathType_Returns400() throws Exception {
        expectError(mvc.perform(get("/employees/abc").with(user("caller"))),
                400, MethodArgumentTypeMismatchException.class);
        verifyNoInteractions(employeeService);
    }

    @Test
    void invalidQueryType_Returns400() throws Exception {
        expectError(mvc.perform(get("/employees/page").with(user("caller"))
                .param("page", "abc").param("pageSize", "10")),
                400, MethodArgumentTypeMismatchException.class);
        verifyNoInteractions(employeeService);
    }

    @Test
    void fileInvalidPage_Returns400BeforeService() throws Exception {
        ResultActions response = mvc.perform(get("/files").with(user("caller").roles("USER"))
                .param("page", "abc").param("size", "10"));
        expectError(response, 400, MethodArgumentTypeMismatchException.class);
        response.andExpect(jsonPath("$.message").value("请求格式或参数错误"));
        verifyNoInteractions(fileService);
    }

    @Test
    void fileInvalidSize_Returns400BeforeService() throws Exception {
        ResultActions response = mvc.perform(get("/files").with(user("caller").roles("USER"))
                .param("page", "1").param("size", "abc"));
        expectError(response, 400, MethodArgumentTypeMismatchException.class);
        response.andExpect(jsonPath("$.message").value("请求格式或参数错误"));
        verifyNoInteractions(fileService);
    }

    @Test
    void filePaginationBusinessException_Returns400() throws Exception {
        when(fileService.searchPageByUserScope(1, 101, "pdf", "caller", false))
                .thenThrow(new BusinessException("每页条数必须在1到100之间"));
        ResultActions response = mvc.perform(get("/files").with(user("caller").roles("USER"))
                .param("page", "1").param("size", "101").param("keyword", "pdf"));
        expectError(response, 400, BusinessException.class);
        response.andExpect(jsonPath("$.message").value("每页条数必须在1到100之间"));
        verify(fileService, times(1)).searchPageByUserScope(1, 101, "pdf", "caller", false);
    }

    @Test
    void missingQuery_Returns400() throws Exception {
        expectError(mvc.perform(get("/employees/page").with(user("caller"))
                .param("pageSize", "10")), 400, MissingServletRequestParameterException.class);
        verifyNoInteractions(employeeService);
    }

    @Test
    void missingFilePart_Returns400() throws Exception {
        expectError(mvc.perform(multipart("/files/upload").with(user("caller"))),
                400, MissingServletRequestPartException.class);
        verifyNoInteractions(fileService);
    }

    @Test
    void simulatedUploadLimit_MapsTo413_NotAContainerSizeTest() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "small.pdf", "application/pdf", new byte[]{1});
        when(fileService.saveFile(any(), eq("caller")))
                .thenThrow(new MaxUploadSizeExceededException(10 * 1024 * 1024));
        expectError(mvc.perform(multipart("/files/upload").file(file).with(user("caller"))),
                413, MaxUploadSizeExceededException.class);
        verify(fileService).saveFile(any(), eq("caller"));
    }

    @Test
    void unsupportedMethod_Returns405() throws Exception {
        expectError(mvc.perform(post("/files/8/download").with(user("caller"))),
                405, HttpRequestMethodNotSupportedException.class);
        verifyNoInteractions(fileService);
    }

    @Test
    void unsupportedContentType_Returns415() throws Exception {
        expectError(mvc.perform(post("/files/upload").with(user("caller"))
                .contentType(MediaType.APPLICATION_JSON).content("{}")),
                415, HttpMediaTypeNotSupportedException.class);
        verifyNoInteractions(fileService);
    }

    @Test
    void unsupportedAccept_Returns406WithJsonError() throws Exception {
        when(employeeService.findById(1)).thenReturn(null);
        expectError(mvc.perform(get("/employees/1").with(user("caller").authorities(() -> "employee:view"))
                .accept(MediaType.IMAGE_PNG)), 406, HttpMediaTypeNotAcceptableException.class);
    }

    @Test
    void businessException_Remains400() throws Exception {
        when(employeeService.findById(1)).thenThrow(new BusinessException("员工不存在"));
        expectError(mvc.perform(get("/employees/1").with(user("caller").authorities(() -> "employee:view"))), 400, BusinessException.class);
    }

    @Test
    void unknownException_Returns500WithoutInternalDetails() throws Exception {
        String internal = "SQL internal-secret SELECT password FROM user";
        when(employeeService.findById(1)).thenThrow(new RuntimeException(internal));
        ResultActions response = mvc.perform(get("/employees/1").with(user("caller").authorities(() -> "employee:view")));
        expectError(response, 500, RuntimeException.class);
        response.andExpect(jsonPath("$.message").value("系统异常"))
                .andExpect(content().string(not(containsString(internal))))
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void unauthenticated_Remains401WithResult() throws Exception {
        mvc.perform(get("/employees/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        verifyNoInteractions(employeeService);
    }
}
