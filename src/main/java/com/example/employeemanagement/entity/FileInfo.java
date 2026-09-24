package com.example.employeemanagement.entity;

import java.time.LocalDateTime;

public class FileInfo {

    private Long id;
    private String originalName;
    private String storedName;
    private String contentType;
    private Long fileSize;
    private Integer uploadUserId;
    private LocalDateTime uploadTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    public String getStoredName() {
        return storedName;
    }

    public void setStoredName(String storedName) {
        this.storedName = storedName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public Integer getUploadUserId() {
        return uploadUserId;
    }

    public void setUploadUserId(Integer uploadUserId) {
        this.uploadUserId = uploadUserId;
    }

    public LocalDateTime getUploadTime() {
        return uploadTime;
    }

    public void setUploadTime(LocalDateTime uploadTime) {
        this.uploadTime = uploadTime;
    }
}