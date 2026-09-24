package com.example.employeemanagement.mapper;

import com.example.employeemanagement.entity.FileInfo;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface FileInfoMapper {

    @Insert("""
            INSERT INTO file_info
            (original_name, stored_name, content_type, file_size, upload_user_id)
            VALUES
            (#{originalName}, #{storedName}, #{contentType}, #{fileSize}, #{uploadUserId})
            """)
    // 将数据库生成的 ID 回填到实体。
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(FileInfo fileInfo);


    @Select("""
            SELECT
                id,
                original_name AS originalName,
                stored_name AS storedName,
                content_type AS contentType,
                file_size AS fileSize,
                upload_user_id AS uploadUserId,
                upload_time AS uploadTime
            FROM file_info
            WHERE id = #{id}
            """)
    FileInfo findById(Long id);

    @Delete("""
        DELETE FROM file_info
        WHERE id = #{id}
        """)
    int deleteById(Long id);

    List<FileInfo> searchPage(
            @Param("keyword") String keyword,
            @Param("userId") Integer userId,
            @Param("offset") Integer offset,
            @Param("size") Integer size
    );

    Long countSearch(
            @Param("keyword") String keyword,
            @Param("userId") Integer userId
    );
}