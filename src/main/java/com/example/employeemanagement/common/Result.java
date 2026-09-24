package com.example.employeemanagement.common;

import lombok.Data;

@Data
public class Result<T> {

    private Integer code;
    private String message;
    private T data;

    public Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // 成功，有数据
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    // 成功，没有数据
    public static <T> Result<T> success() {
        return new Result<>(200, "success", null);
    }

    // 失败
    public static <T> Result<T> error(Integer code, String message) {
        return new Result<>(code, message, null);
    }

    // 失败，同时携带数据
    public static <T> Result<T> error(
            Integer code,
            String message,
            T data) {

        return new Result<>(code, message, data);
    }
}