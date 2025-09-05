package com.example.demo.exception;

import lombok.Getter;

/**
 * @Description 模型异常处理类
 * @Author charles
 * @Date 2025/9/3 20:51
 * @Version 1.0.0
 */
@Getter
public class ModelException extends RuntimeException {
    private final String message;

    public ModelException(String message) {
        super(message);
        this.message = message;
    }

    public ModelException(String message, Throwable cause) {
        super(message, cause);
        this.message = message;
    }

    public ModelException(String message, String nullFieldConfig) {
        this.message = message;
    }
}
