package com.example.employeemanagement.controller;

import com.example.employeemanagement.entity.FileInfo;
import com.example.employeemanagement.exception.BusinessException;
import com.example.employeemanagement.exception.GlobalExceptionHandler;
import com.example.employeemanagement.service.FileService;
import com.example.employeemanagement.vo.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Arrays;


import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class FileControllerTest {

    @Mock
    private FileService fileService;

    private MockMvc mockMvc;



    @BeforeEach
    void setUp() {

        FileController fileController =
                new FileController(fileService);

        mockMvc =
                MockMvcBuilders
                        .standaloneSetup(fileController)
                        .setControllerAdvice(
                                new GlobalExceptionHandler()
                        )
                        .build();
    }


    @Test
    void testList_NormalUser() throws Exception {

        Authentication authentication =
                authentication("zhangsan", "ROLE_USER");

        FileInfo file = new FileInfo();
        file.setId(1L);
        file.setOriginalName("test.pdf");

        PageResult<FileInfo> pageResult =
                new PageResult<>(
                        List.of(file),
                        1L,
                        2,
                        5,
                        1L
                );

        when(fileService.searchPageByUserScope(
                2,
                5,
                "pdf",
                "zhangsan",
                false
        )).thenReturn(pageResult);

        mockMvc.perform(
                        get("/files")
                                .param("page", "2")
                                .param("size", "5")
                                .param("keyword", "pdf")
                                .principal(authentication)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(5))
                .andExpect(jsonPath("$.data.pages").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(1))
                .andExpect(jsonPath("$.data.records[0].originalName").value("test.pdf")
                );

        verify(fileService, times(1))
                .searchPageByUserScope(
                        2,
                        5,
                        "pdf",
                        "zhangsan",
                        false
                );
    }

    @Test
    void testList_Admin() throws Exception {

        Authentication authentication =
                authentication("admin", "ROLE_ADMIN");

        PageResult<FileInfo> pageResult =
                new PageResult<>(
                        List.of(),
                        0L,
                        1,
                        10,
                        0L
                );

        when(fileService.searchPageByUserScope(
                1,
                10,
                null,
                "admin",
                true
        )).thenReturn(pageResult);

        mockMvc.perform(
                        get("/files")
                                .principal(authentication)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.pages").value(0));

        verify(fileService, times(1))
                .searchPageByUserScope(
                        1,
                        10,
                        null,
                        "admin",
                        true
                );
    }

    @Test
    void testDetail_NormalUser() throws Exception {

        Authentication authentication =
                authentication("zhangsan", "ROLE_USER");

        FileInfo fileInfo = new FileInfo();
        fileInfo.setId(8L);
        fileInfo.setOriginalName("test.pdf");

        when(fileService.findById(
                8L,
                "zhangsan",
                false
        )).thenReturn(fileInfo);

        mockMvc.perform(
                        get("/files/8")
                                .principal(authentication)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(8))
                .andExpect(jsonPath("$.data.originalName").value("test.pdf"));

        verify(fileService, times(1))
                .findById(
                        8L,
                        "zhangsan",
                        false
                );
    }

    @Test
    void testDelete_NormalUser() throws Exception {

        Authentication authentication =
                authentication("zhangsan", "ROLE_USER");

        mockMvc.perform(
                        delete("/files/8")
                                .principal(authentication)
                )
                .andExpect(status().isOk());

        verify(fileService, times(1))
                .deleteFile(
                        8L,
                        "zhangsan",
                        false
                );
    }

    @Test
    void testDelete_Admin() throws Exception {

        Authentication authentication =
                authentication("admin", "ROLE_ADMIN");

        mockMvc.perform(
                        delete("/files/8")
                                .principal(authentication)
                )
                .andExpect(status().isOk());

        verify(fileService, times(1))
                .deleteFile(
                        8L,
                        "admin",
                        true
                );
    }

    @Test
    void testDelete_FileNotFound() throws Exception {

        Authentication authentication =
                authentication("zhangsan", "ROLE_USER");


        //        当调用 deleteFile(...)
        //        ↓
        //        不要正常执行
        //        ↓
        //        直接模拟抛出异常
        //        ↓
        //        BusinessException("文件不存在")
        doThrow(new BusinessException("文件不存在"))
                .when(fileService)
                .deleteFile(
                        8L,
                        "zhangsan",
                        false
                );

        mockMvc.perform(
                        delete("/files/8")
                                .principal(authentication)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("文件不存在"));

        verify(fileService, times(1))
                .deleteFile(
                        8L,
                        "zhangsan",
                        false
                );
    }

    @Test
    void testDelete_AccessDenied() throws Exception {

        Authentication authentication =
                authentication("zhangsan", "ROLE_USER");

        doThrow(new AccessDeniedException("无权删除该文件"))
                .when(fileService)
                .deleteFile(
                        8L,
                        "zhangsan",
                        false
                );

        mockMvc.perform(
                        delete("/files/8")
                                .principal(authentication)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("没有权限"));

        verify(fileService, times(1))
                .deleteFile(
                        8L,
                        "zhangsan",
                        false
                );
    }

    @Test
    void testUpload_NormalUser() throws Exception {

        Authentication authentication =
                authentication("zhangsan", "ROLE_USER");

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "test.pdf",
                        "application/pdf",
                        "hello".getBytes()
                );

        FileInfo savedFile = new FileInfo();
        savedFile.setId(100L);
        savedFile.setOriginalName("test.pdf");
        savedFile.setContentType("application/pdf");

        when(fileService.saveFile(file, "zhangsan"))
                .thenReturn(savedFile);

        mockMvc.perform(
                        multipart("/files/upload")
                                .file(file)
                                .principal(authentication)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.originalName").value("test.pdf"))
                .andExpect(jsonPath("$.data.contentType").value("application/pdf"));

        verify(fileService, times(1))
                .saveFile(
                        file,
                        "zhangsan"
                );
    }

    @Test
    void testDownload_Success(@TempDir Path directory) throws Exception {

        Path testFile = directory.resolve("test.pdf");

        Files.writeString(
                testFile,
                "hello"
        );

        FileInfo fileInfo = new FileInfo();
        fileInfo.setId(8L);
        fileInfo.setStoredName("test.pdf");
        fileInfo.setOriginalName("test.pdf");
        fileInfo.setContentType("application/pdf");

        when(fileService.findById(8L, "zhangsan", false))
                .thenReturn(fileInfo);
        when(fileService.resolveFilePath("test.pdf")).thenReturn(testFile);

        mockMvc.perform(
                        get("/files/8/download")
                                .principal(authentication("zhangsan", "ROLE_USER"))
                )
                .andExpect(status().isOk())
                .andExpect(
                        content().contentType("application/pdf")
                )
                .andExpect(
                        header().string(
                                "Content-Disposition",
                                "attachment; filename*=UTF-8''test.pdf"
                        )
                )
                .andExpect(
                        content().string("hello")
                );

        verify(fileService, times(1))
                .findById(8L, "zhangsan", false);

        Files.deleteIfExists(testFile);
    }

    @Test
    void testDownload_PhysicalFileNotFound(@TempDir Path directory) throws Exception {

        FileInfo fileInfo = new FileInfo();
        fileInfo.setId(8L);
        fileInfo.setStoredName("missing.pdf");
        fileInfo.setOriginalName("test.pdf");
        fileInfo.setContentType("application/pdf");

        when(fileService.findById(8L, "zhangsan", false))
                .thenReturn(fileInfo);
        when(fileService.resolveFilePath("missing.pdf"))
                .thenReturn(directory.resolve("missing.pdf"));

        mockMvc.perform(
                        get("/files/8/download")
                                .principal(authentication("zhangsan", "ROLE_USER"))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("文件不存在"));

        verify(fileService, times(1))
                .findById(8L, "zhangsan", false);
    }
    private Authentication authentication(String username, String... authorities) {
        var authoritiesList = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toList();
        return new UsernamePasswordAuthenticationToken(username, null, authoritiesList);
    }
}
