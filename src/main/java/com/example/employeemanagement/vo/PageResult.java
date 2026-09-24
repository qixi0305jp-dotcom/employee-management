package com.example.employeemanagement.vo;

import java.util.List;

public class PageResult<T> {

    private List<T> records;
    private Long total;
    private Integer page;
    private Integer size;
    private Long pages;

    public PageResult() {
    }

    public PageResult(
            List<T> records,
            Long total,
            Integer page,
            Integer size,
            Long pages) {

        this.records = records;
        this.total = total;
        this.page = page;
        this.size = size;
        this.pages = pages;
    }

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public Long getPages() {
        return pages;
    }

    public void setPages(Long pages) {
        this.pages = pages;
    }
}