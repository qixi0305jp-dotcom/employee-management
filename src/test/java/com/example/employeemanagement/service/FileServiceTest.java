package com.example.employeemanagement.service;

import com.example.employeemanagement.entity.FileInfo;
import com.example.employeemanagement.entity.User;
import com.example.employeemanagement.exception.BusinessException;
import com.example.employeemanagement.mapper.FileInfoMapper;
import com.example.employeemanagement.mapper.UserMapper;
import com.example.employeemanagement.vo.PageResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock
    private FileInfoMapper fileInfoMapper;

    @Mock
    private UserMapper userMapper;

    private FileService fileService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        fileService = new FileService(fileInfoMapper, userMapper, tempDir.toString());
    }

    @Test
    void testUpload_UsesConfiguredDirectory() throws IOException {
        User user = new User();
        user.setId(1);
        when(userMapper.findByUsername("zhangsan")).thenReturn(user);
        MockMultipartFile upload = new MockMultipartFile(
                "file", "original.pdf", "application/pdf", "uploaded-content".getBytes());

        FileInfo saved = fileService.saveFile(upload, "zhangsan");

        assertTrue(saved.getStoredName().matches("[0-9a-f-]{36}\\.pdf"));
        Path expected = tempDir.resolve(saved.getStoredName());
        assertEquals("uploaded-content", Files.readString(expected));
        assertEquals(expected.toAbsolutePath().normalize(),
                fileService.resolveFilePath(saved.getStoredName()));
        ArgumentCaptor<FileInfo> metadata = ArgumentCaptor.forClass(FileInfo.class);
        verify(fileInfoMapper).insert(metadata.capture());
        assertEquals(saved.getStoredName(), metadata.getValue().getStoredName());
        assertFalse(Path.of(metadata.getValue().getStoredName()).isAbsolute());
    }

    @Test
    void testUpload_EmptyFileRejectedBeforeUserLookup() {
        MultipartFile upload = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);
        BusinessException failure = assertThrows(BusinessException.class,
                () -> fileService.saveFile(upload, "zhangsan"));
        assertEquals("上传文件不能为空", failure.getMessage());
        verifyNoInteractions(userMapper, fileInfoMapper);
    }

    @Test
    void testUpload_OversizeRejectedBeforeUserLookup() {
        MultipartFile upload = mock(MultipartFile.class);
        when(upload.isEmpty()).thenReturn(false);
        when(upload.getSize()).thenReturn(10L * 1024 * 1024 + 1);
        BusinessException failure = assertThrows(BusinessException.class,
                () -> fileService.saveFile(upload, "zhangsan"));
        assertEquals("文件大小不能超过10MB", failure.getMessage());
        verifyNoInteractions(userMapper, fileInfoMapper);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"no-extension", "trailing.", "program.exe"})
    void testUpload_InvalidExtensionStopsBeforeContentType(String filename) throws IOException {
        User user = new User();
        user.setId(1);
        when(userMapper.findByUsername("zhangsan")).thenReturn(user);
        MultipartFile upload = mock(MultipartFile.class);
        when(upload.getOriginalFilename()).thenReturn(filename);

        BusinessException failure = assertThrows(BusinessException.class,
                () -> fileService.saveFile(upload, "zhangsan"));

        assertEquals("不支持的文件类型", failure.getMessage());
        verify(userMapper).findByUsername("zhangsan");
        verify(upload, never()).getContentType();
        verify(upload, never()).transferTo(any(Path.class));
        verifyNoInteractions(fileInfoMapper);
        try (var files = Files.list(tempDir)) {
            assertEquals(0L, files.count());
        }
    }

    @Test
    void testUpload_UppercaseExtensionPreserved() throws IOException {
        User user = new User();
        user.setId(1);
        when(userMapper.findByUsername("zhangsan")).thenReturn(user);
        MultipartFile upload = new MockMultipartFile(
                "file", "archive.part.PDF", "application/pdf", "content".getBytes());

        FileInfo result = fileService.saveFile(upload, "zhangsan");

        assertTrue(result.getStoredName().matches("[0-9a-f-]{36}\\.PDF"));
        assertEquals("archive.part.PDF", result.getOriginalName());
        assertEquals("content", Files.readString(tempDir.resolve(result.getStoredName())));
        ArgumentCaptor<FileInfo> metadata = ArgumentCaptor.forClass(FileInfo.class);
        verify(fileInfoMapper).insert(metadata.capture());
        assertSame(result, metadata.getValue());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"text/plain"})
    void testUpload_InvalidContentTypeRejected(String contentType) throws IOException {
        User user = new User();
        user.setId(1);
        when(userMapper.findByUsername("zhangsan")).thenReturn(user);
        MultipartFile upload = mock(MultipartFile.class);
        when(upload.getOriginalFilename()).thenReturn("file.pdf");
        when(upload.getContentType()).thenReturn(contentType);

        BusinessException failure = assertThrows(BusinessException.class,
                () -> fileService.saveFile(upload, "zhangsan"));

        assertEquals("不支持的文件类型", failure.getMessage());
        verify(upload, never()).transferTo(any(Path.class));
        verifyNoInteractions(fileInfoMapper);
        try (var files = Files.list(tempDir)) {
            assertEquals(0L, files.count());
        }
    }

    @Test
    void testUpload_MissingUserPrecedesTypeValidation() throws IOException {
        MultipartFile upload = spy(new MockMultipartFile(
                "file", "program.exe", "text/plain", "content".getBytes()));
        when(userMapper.findByUsername("missing-user")).thenReturn(null);

        BusinessException failure = assertThrows(BusinessException.class,
                () -> fileService.saveFile(upload, "missing-user"));

        assertEquals("用户不存在", failure.getMessage());
        verify(userMapper).findByUsername("missing-user");
        verify(upload, never()).getOriginalFilename();
        verify(upload, never()).getContentType();
        verify(upload, never()).transferTo(any(Path.class));
        verifyNoInteractions(fileInfoMapper);
    }

    @Test
    void testUpload_InsertFailureDeletesWrittenFile() throws IOException {
        User user = new User();
        user.setId(1);
        when(userMapper.findByUsername("zhangsan")).thenReturn(user);
        MultipartFile upload = new MockMultipartFile(
                "file", "file.pdf", "application/pdf", "content".getBytes());
        RuntimeException original = new IllegalStateException("test insert failure");
        when(fileInfoMapper.insert(any(FileInfo.class))).thenAnswer(invocation -> {
            FileInfo metadata = invocation.getArgument(0);
            assertEquals("content", Files.readString(tempDir.resolve(metadata.getStoredName())));
            throw original;
        });

        assertSame(original, assertThrows(IllegalStateException.class,
                () -> fileService.saveFile(upload, "zhangsan")));

        verify(fileInfoMapper).insert(any(FileInfo.class));
        try (var files = Files.list(tempDir)) {
            assertEquals(0L, files.count());
        }
    }

    @Test
    void testUpload_TransferFailureDeletesPartialFile() throws IOException {
        User user = new User();
        user.setId(1);
        when(userMapper.findByUsername("zhangsan")).thenReturn(user);
        MultipartFile upload = mock(MultipartFile.class);
        when(upload.getOriginalFilename()).thenReturn("file.pdf");
        when(upload.getContentType()).thenReturn("application/pdf");
        IOException original = new IOException("test transfer failure");
        doAnswer(invocation -> {
            Path destination = invocation.getArgument(0);
            Files.writeString(destination, "partial");
            throw original;
        }).when(upload).transferTo(any(Path.class));

        assertSame(original, assertThrows(IOException.class,
                () -> fileService.saveFile(upload, "zhangsan")));

        ArgumentCaptor<Path> destination = ArgumentCaptor.forClass(Path.class);
        verify(upload).transferTo(destination.capture());
        assertFalse(Files.exists(destination.getValue()));
        verifyNoInteractions(fileInfoMapper);
    }

    @Test
    void testResolveFilePath_RejectsTraversal() {
        assertThrows(BusinessException.class,
                () -> fileService.resolveFilePath("../outside.pdf"));
    }

    @Test
    void testResolveFilePath_RejectsAbsolutePath() {
        assertThrows(BusinessException.class,
                () -> fileService.resolveFilePath(tempDir.resolve("inside.pdf").toString()));
    }

    @Test
    void testDeleteFile_RejectsTraversalBeforeDatabaseDelete() throws IOException {
        assertInvalidDeletePath("../outside.pdf");
    }

    @Test
    void testDeleteFile_RejectsAbsolutePathBeforeDatabaseDelete() throws IOException {
        assertInvalidDeletePath(tempDir.resolve("inside.pdf").toString());
    }

    private void assertInvalidDeletePath(String storedName) throws IOException {
        FileInfo fileInfo = new FileInfo();
        fileInfo.setStoredName(storedName);
        fileInfo.setUploadUserId(1);
        User user = new User();
        user.setId(1);
        when(fileInfoMapper.findById(8L)).thenReturn(fileInfo);
        when(userMapper.findByUsername("zhangsan")).thenReturn(user);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> fileService.deleteFile(8L, "zhangsan", false));

        assertEquals("非法文件路径", exception.getMessage());
        verify(fileInfoMapper, never()).deleteById(anyLong());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, -1})
    void testSearchPageByUserScope_InvalidPage(Integer page) {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> fileService.searchPageByUserScope(page, 10, "pdf", "zhangsan", false));
        assertEquals("页码必须大于等于1", exception.getMessage());
        verifyNoInteractions(userMapper, fileInfoMapper);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, -1, 101})
    void testSearchPageByUserScope_InvalidSize(Integer size) {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> fileService.searchPageByUserScope(1, size, "pdf", "zhangsan", false));
        assertEquals("每页条数必须在1到100之间", exception.getMessage());
        verifyNoInteractions(userMapper, fileInfoMapper);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 100})
    void testSearchPageByUserScope_SizeBoundaryAccepted(int size) {
        FileInfo file = new FileInfo();
        file.setId(301L);
        List<FileInfo> records = List.of(file);
        when(fileInfoMapper.searchPage("pdf", null, 0, size)).thenReturn(records);
        when(fileInfoMapper.countSearch("pdf", null)).thenReturn(1L);

        PageResult<FileInfo> result = fileService.searchPageByUserScope(1, size, "pdf", "admin", true);

        verifyNoInteractions(userMapper);
        verify(fileInfoMapper).searchPage("pdf", null, 0, size);
        verify(fileInfoMapper).countSearch("pdf", null);
        assertSame(records, result.getRecords());
        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getPage());
        assertEquals(size, result.getSize());
        assertEquals(1L, result.getPages());
    }

    @Test
    void testSearchPageByUserScope_MaxSafeOffsetAccepted() {
        List<FileInfo> records = List.of();
        when(fileInfoMapper.searchPage("pdf", null, 2_147_483_646, 2)).thenReturn(records);
        when(fileInfoMapper.countSearch("pdf", null)).thenReturn(5L);

        PageResult<FileInfo> result = fileService.searchPageByUserScope(
                1_073_741_824, 2, "pdf", "admin", true);

        verifyNoInteractions(userMapper);
        verify(fileInfoMapper).searchPage("pdf", null, 2_147_483_646, 2);
        verify(fileInfoMapper).countSearch("pdf", null);
        assertSame(records, result.getRecords());
        assertTrue(result.getRecords().isEmpty());
        assertEquals(5L, result.getTotal());
        assertEquals(1_073_741_824, result.getPage());
        assertEquals(2, result.getSize());
        assertEquals(3L, result.getPages());
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 4})
    void testSearchPageByUserScope_OffsetOverflow(int size) {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> fileService.searchPageByUserScope(1_073_741_825, size, "pdf", "zhangsan", false));
        assertEquals("分页偏移量超出支持范围", exception.getMessage());
        verifyNoInteractions(userMapper, fileInfoMapper);
    }

    @Test
    void testSearchPageByUserScope_BeyondLastPageReturnsEmptyRecords() {
        List<FileInfo> records = List.of();
        when(fileInfoMapper.searchPage("pdf", null, 6, 2)).thenReturn(records);
        when(fileInfoMapper.countSearch("pdf", null)).thenReturn(5L);

        PageResult<FileInfo> result = fileService.searchPageByUserScope(4, 2, "pdf", "admin", true);

        verifyNoInteractions(userMapper);
        verify(fileInfoMapper).searchPage("pdf", null, 6, 2);
        verify(fileInfoMapper).countSearch("pdf", null);
        assertSame(records, result.getRecords());
        assertTrue(result.getRecords().isEmpty());
        assertEquals(5L, result.getTotal());
        assertEquals(4, result.getPage());
        assertEquals(2, result.getSize());
        assertEquals(3L, result.getPages());
    }

    @Test
    void testSearchPageByUserScope_NormalUser() {
        User user = new User();
        user.setId(41);
        FileInfo first = new FileInfo();
        first.setId(101L);
        FileInfo second = new FileInfo();
        second.setId(102L);
        List<FileInfo> records = List.of(first, second);
        when(userMapper.findByUsername("zhangsan")).thenReturn(user);
        when(fileInfoMapper.searchPage("pdf", 41, 2, 2)).thenReturn(records);
        when(fileInfoMapper.countSearch("pdf", 41)).thenReturn(5L);

        PageResult<FileInfo> result = fileService.searchPageByUserScope(2, 2, "pdf", "zhangsan", false);

        verify(userMapper, times(1)).findByUsername("zhangsan");
        verify(fileInfoMapper, times(1)).searchPage("pdf", 41, 2, 2);
        verify(fileInfoMapper, times(1)).countSearch("pdf", 41);
        assertSame(records, result.getRecords());
        assertEquals(2, result.getRecords().size());
        assertEquals(5L, result.getTotal());
        assertEquals(2, result.getPage());
        assertEquals(2, result.getSize());
        assertEquals(3L, result.getPages());
    }

    @Test
    void testSearchPageByUserScope_Admin() {
        FileInfo first = new FileInfo();
        first.setId(201L);
        FileInfo second = new FileInfo();
        second.setId(202L);
        List<FileInfo> records = List.of(first, second);
        when(fileInfoMapper.searchPage("report", null, 4, 2)).thenReturn(records);
        when(fileInfoMapper.countSearch("report", null)).thenReturn(7L);

        PageResult<FileInfo> result = fileService.searchPageByUserScope(3, 2, "report", "admin", true);

        verifyNoInteractions(userMapper);
        verify(fileInfoMapper, times(1)).searchPage("report", null, 4, 2);
        verify(fileInfoMapper, times(1)).countSearch("report", null);
        assertSame(records, result.getRecords());
        assertEquals(2, result.getRecords().size());
        assertEquals(7L, result.getTotal());
        assertEquals(3, result.getPage());
        assertEquals(2, result.getSize());
        assertEquals(4L, result.getPages());
    }

    @Test
    void testSearchPageByUserScope_UserNotFound() {
        when(userMapper.findByUsername("missing-user")).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> fileService.searchPageByUserScope(1, 2, "pdf", "missing-user", false));

        assertEquals("用户不存在", exception.getMessage());
        verify(userMapper, times(1)).findByUsername("missing-user");
        verifyNoInteractions(fileInfoMapper);
    }

    @Test
    void testDeleteFile_NormalUserDeleteOthersFile() throws IOException {

        // 1. 准备假的文件
        FileInfo fileInfo = new FileInfo();
        fileInfo.setId(8L);
        fileInfo.setStoredName("test.docx");
        fileInfo.setUploadUserId(2);

        // 2. 准备假的当前用户
        User user = new User();
        user.setId(1);
        user.setUsername("zhangsan");

        // 3. Mock Mapper 返回值
        when(fileInfoMapper.findById(8L))
                .thenReturn(fileInfo);

        when(userMapper.findByUsername("zhangsan"))
                .thenReturn(user);

        // 4. 执行 Service，并检查必须抛 AccessDeniedException
        AccessDeniedException exception =
                assertThrows(
                        AccessDeniedException.class,
                        () -> fileService.deleteFile(
                                8L,
                                "zhangsan",
                                false
                        )
                );

        // 5. 检查异常信息
        assertEquals(
                "无权删除该文件",
                exception.getMessage()
        );

        // 6. 验证前面的查询确实执行了
        verify(fileInfoMapper, times(1))
                .findById(8L);

        verify(userMapper, times(1))
                .findByUsername("zhangsan");

        // 7. 因为权限检查已经失败，绝对不能继续删除数据库
        verify(fileInfoMapper, never())
                .deleteById(anyLong());
    }

    @Test
    void testDeleteFile_FileNotFound() {

        // 1. 模拟数据库查不到文件
        when(fileInfoMapper.findById(999L))
                .thenReturn(null);

        // 2. 执行，并检查异常
        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> fileService.deleteFile(
                                999L,
                                "zhangsan",
                                false
                        )
                );

        // 3. 检查异常信息
        assertEquals(
                "文件不存在",
                exception.getMessage()
        );

        // 4. 文件查询执行1次
        verify(fileInfoMapper, times(1))
                .findById(999L);

        // 5. 文件都不存在，后面的操作绝对不能执行
        verify(userMapper, never())
                .findByUsername(anyString());

        verify(fileInfoMapper, never())
                .deleteById(anyLong());
    }

    @Test
    void testDeleteFile_UserNotFound() {

        // 1. 文件存在
        FileInfo fileInfo = new FileInfo();
        fileInfo.setId(8L);
        fileInfo.setStoredName("test.docx");
        fileInfo.setUploadUserId(2);

        when(fileInfoMapper.findById(8L))
                .thenReturn(fileInfo);

        // 2. 但是用户不存在
        when(userMapper.findByUsername("zhangsan"))
                .thenReturn(null);

        // 3. 执行并检查异常
        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> fileService.deleteFile(
                                8L,
                                "zhangsan",
                                false
                        )
                );

        // 4. 检查异常信息
        assertEquals(
                "用户不存在",
                exception.getMessage()
        );

        // 5. 验证执行过程
        verify(fileInfoMapper, times(1))
                .findById(8L);

        verify(userMapper, times(1))
                .findByUsername("zhangsan");

        // 用户不存在，所以绝对不能删除
        verify(fileInfoMapper, never())
                .deleteById(anyLong());
    }

    @Test
    void testDeleteFile_DatabaseDeleteFailed() {

        // 1. 文件属于 zhangsan 自己
        FileInfo fileInfo = new FileInfo();
        fileInfo.setId(8L);
        fileInfo.setStoredName("test.docx");
        fileInfo.setUploadUserId(1);

        User user = new User();
        user.setId(1);
        user.setUsername("zhangsan");

        // 2. Mock 查询结果
        when(fileInfoMapper.findById(8L))
                .thenReturn(fileInfo);

        when(userMapper.findByUsername("zhangsan"))
                .thenReturn(user);

        // 3. 模拟数据库删除失败
        when(fileInfoMapper.deleteById(8L))
                .thenReturn(0);

        // 4. 预期 BusinessException
        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> fileService.deleteFile(
                                8L,
                                "zhangsan",
                                false
                        )
                );

        // 5. 检查异常信息
        assertEquals(
                "文件删除失败",
                exception.getMessage()
        );

        // 6. 检查执行过程
        verify(fileInfoMapper, times(1))
                .findById(8L);

        verify(userMapper, times(1))
                .findByUsername("zhangsan");

        verify(fileInfoMapper, times(1))
                .deleteById(8L);
    }

    @Test
    void testDeleteFile_Success() throws IOException {

        // 1. 在JUnit临时目录创建一个真正的临时文件
        Path testFile = tempDir.resolve("test.docx");
        Files.createFile(testFile);

        // 先确认文件真的存在
        assertTrue(Files.exists(testFile));

        // 3. 准备文件数据库信息
        FileInfo fileInfo = new FileInfo();
        fileInfo.setId(8L);
        fileInfo.setStoredName("test.docx");
        fileInfo.setUploadUserId(1);

        // 4. 准备当前用户
        User user = new User();
        user.setId(1);
        user.setUsername("zhangsan");

        // 5. Mock数据库
        when(fileInfoMapper.findById(8L))
                .thenReturn(fileInfo);

        when(userMapper.findByUsername("zhangsan"))
                .thenReturn(user);

        // 模拟数据库删除成功
        when(fileInfoMapper.deleteById(8L))
                .thenReturn(1);

        // 6. 真正执行Service
        fileService.deleteFile(
                8L,
                "zhangsan",
                false
        );

        // 7. 检查数据库删除确实调用1次
        verify(fileInfoMapper, times(1))
                .deleteById(8L);

        // 8. 检查临时文件真的被删除
        assertFalse(Files.exists(testFile));
    }
}
