package com.example.employeemanagement.controller;

import com.example.employeemanagement.common.Result;
import com.example.employeemanagement.entity.FileInfo;
import com.example.employeemanagement.exception.BusinessException;
import com.example.employeemanagement.service.FileService;
import com.example.employeemanagement.vo.PageResult;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }


    @PostMapping(
            value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public Result<FileInfo> upload(
            @RequestPart("file") MultipartFile file,
            Authentication authentication) throws IOException {

        String username = authentication.getName();

        FileInfo fileInfo =
                fileService.saveFile(file, username);

        return Result.success(fileInfo);
    }


    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(
            @PathVariable Long id,
            Authentication authentication) throws IOException {

        String username = authentication.getName();

        boolean isAdmin =
                authentication.getAuthorities().stream()
                        .anyMatch(a ->
                                a.getAuthority().equals("ROLE_ADMIN"));

        // ① 根据ID查询文件信息并检查访问权限
        FileInfo fileInfo = fileService.findById(id, username, isAdmin);

        // ② 数据库告诉我们服务器真正保存的文件名
        Path filePath = fileService.resolveFilePath(fileInfo.getStoredName());

        // ③ 数据库有记录，但硬盘上的真实文件可能被删除了
        if (!Files.exists(filePath)) {
            throw new BusinessException("文件不存在");
        }

        // ④ 把磁盘文件包装成 Resource
        Resource resource =
                new UrlResource(filePath.toUri());

        // ⑤ 获取文件类型
        String contentType = fileInfo.getContentType();

        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        // ⑥ 返回文件
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" +
                                URLEncoder.encode(
                                        fileInfo.getOriginalName(),
                                        StandardCharsets.UTF_8
                                ).replace("+", "%20")
                )
                .body(resource);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @PathVariable Long id,
            Authentication authentication) throws IOException {

        String username = authentication.getName();

        boolean isAdmin =
                authentication.getAuthorities().stream()
                        .anyMatch(a ->
                                a.getAuthority().equals("ROLE_ADMIN"));

        fileService.deleteFile(id, username, isAdmin);

        return Result.success();
    }

    @GetMapping
    public Result<PageResult<FileInfo>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword,
            Authentication authentication) {

        String username = authentication.getName();

        boolean isAdmin =
                authentication.getAuthorities().stream()
                        .anyMatch(a ->
                                a.getAuthority().equals("ROLE_ADMIN"));

        PageResult<FileInfo> pageResult =
                fileService.searchPageByUserScope(
                        page,
                        size,
                        keyword,
                        username,
                        isAdmin
                );

        return Result.success(pageResult);
    }

    @GetMapping("/{id}")
    public Result<FileInfo> detail(
            @PathVariable Long id,
            Authentication authentication) {

        String username = authentication.getName();

        boolean isAdmin =
                authentication.getAuthorities().stream()
                        .anyMatch(a ->
                                a.getAuthority().equals("ROLE_ADMIN"));

        FileInfo fileInfo =
                fileService.findById(id, username, isAdmin);

        return Result.success(fileInfo);
    }
}
