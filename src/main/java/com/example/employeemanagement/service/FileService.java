package com.example.employeemanagement.service;

import com.example.employeemanagement.entity.FileInfo;
import com.example.employeemanagement.entity.User;
import com.example.employeemanagement.exception.BusinessException;
import com.example.employeemanagement.mapper.FileInfoMapper;
import com.example.employeemanagement.mapper.UserMapper;
import com.example.employeemanagement.vo.PageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
public class FileService {

    private final FileInfoMapper fileInfoMapper;
    private final UserMapper userMapper;
    private final Path uploadDir;


    private static final Logger log =
            LoggerFactory.getLogger(FileService.class);

    public FileService(
            FileInfoMapper fileInfoMapper,
            UserMapper userMapper,
            @Value("${app.upload-dir}") String uploadDir) {

        this.fileInfoMapper = fileInfoMapper;
        this.userMapper = userMapper;
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    public Path resolveFilePath(String storedName) {
        Path name = Paths.get(storedName);
        Path resolved = uploadDir.resolve(name).normalize();
        if (name.isAbsolute() || !resolved.startsWith(uploadDir)) {
            throw new BusinessException("非法文件路径");
        }
        return resolved;
    }

    @Transactional
    public FileInfo saveFile(
            MultipartFile file,
            String username) throws IOException {

        if (file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }

        long maxSize = 10 * 1024 * 1024;

        if (file.getSize() > maxSize) {
            throw new BusinessException("文件大小不能超过10MB");
        }


        User user = userMapper.findByUsername(username);

        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        String originalFilename = file.getOriginalFilename();

        String extension = validateFileType(originalFilename, file);

        String storedFilename =
                UUID.randomUUID() + extension;

        Files.createDirectories(uploadDir);

        Path filePath =
                resolveFilePath(storedFilename);

        FileInfo fileInfo = new FileInfo();
        fileInfo.setOriginalName(originalFilename);
        fileInfo.setStoredName(storedFilename);
        fileInfo.setContentType(file.getContentType());
        fileInfo.setFileSize(file.getSize());
        fileInfo.setUploadUserId(user.getId());

        try {
            file.transferTo(filePath);


            fileInfoMapper.insert(fileInfo);
        } catch (Exception e) {
            try {
                Files.deleteIfExists(filePath);
            } catch (IOException deleteException) {

                log.error(
                        "上传失败后删除文件失败，filePath={}",
                        filePath,
                        deleteException
                );
            }

            throw e;
        }

        return fileInfo;
    }

    private String validateFileType(String originalFilename, MultipartFile file) {
        String extension = "";

        if (originalFilename != null &&
                originalFilename.contains(".")) {

            extension = originalFilename.substring(
                    originalFilename.lastIndexOf(".")
            );
        }

        List<String> allowedExtensions =
                List.of(".pdf", ".doc", ".docx", ".jpg", ".jpeg", ".png");

        if (!allowedExtensions.contains(extension.toLowerCase())) {
            throw new BusinessException("不支持的文件类型");
        }

        // 扩展名和 Content-Type 分别校验白名单。
        String contentType = file.getContentType();

        List<String> allowedContentTypes = List.of(
                "application/pdf",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "image/jpeg",
                "image/png"
        );

        if (contentType == null ||
                !allowedContentTypes.contains(contentType)) {

            throw new BusinessException("不支持的文件类型");
        }

        return extension;
    }

    public FileInfo findById(
            Long id,
            String username,
            boolean isAdmin) {

        FileInfo fileInfo = fileInfoMapper.findById(id);

        if (fileInfo == null) {
            throw new BusinessException("文件不存在");
        }

        if (isAdmin) {
            return fileInfo;
        }

        User user = userMapper.findByUsername(username);

        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        if (!user.getId().equals(fileInfo.getUploadUserId())) {
            throw new AccessDeniedException("没有权限");
        }

        return fileInfo;
    }

    public void deleteFile(Long id, String username, boolean isAdmin) throws IOException {

        FileInfo fileInfo = fileInfoMapper.findById(id);

        if (fileInfo == null) {
            throw new BusinessException("文件不存在");
        }

        // 2. 查询当前登录用户
        User user = userMapper.findByUsername(username);

        if (user == null) {
            throw new BusinessException("用户不存在");
        }


        // 3. 数据权限判断
        // 管理员：可以删除任何文件
        // 普通用户：只能删除自己上传的文件
        if (!isAdmin &&
                !user.getId().equals(fileInfo.getUploadUserId())) {

            throw new AccessDeniedException("无权删除该文件");
        }


        Path filePath = resolveFilePath(fileInfo.getStoredName());

        // 先删除数据库记录；磁盘删除失败时可能留下待清理文件。
        int rows = fileInfoMapper.deleteById(id);

        if (rows == 0) {
            throw new BusinessException("文件删除失败");
        }

        Files.deleteIfExists(filePath);
    }

    public PageResult<FileInfo> searchPageByUserScope(
            Integer page,
            Integer size,
            String keyword,
            String username,
            boolean isAdmin) {

        if (page == null || page < 1) {
            throw new BusinessException("页码必须大于等于1");
        }

        if (size == null || size < 1 || size > 100) {
            throw new BusinessException("每页条数必须在1到100之间");
        }

        long offsetLong = ((long) page - 1) * size;
        if (offsetLong > Integer.MAX_VALUE) {
            throw new BusinessException("分页偏移量超出支持范围");
        }
        int offset = (int) offsetLong;

        Integer userId = null;

        if (!isAdmin) {

            User user = userMapper.findByUsername(username);

            if (user == null) {
                throw new BusinessException("用户不存在");
            }

            userId = user.getId();
        }

        List<FileInfo> records =
                fileInfoMapper.searchPage(
                        keyword,
                        userId,
                        offset,
                        size
                );

        Long total =
                fileInfoMapper.countSearch(
                        keyword,
                        userId
                );

        long pages =
                (total + size - 1) / size;

        return new PageResult<>(
                records,
                total,
                page,
                size,
                pages
        );
    }
}
