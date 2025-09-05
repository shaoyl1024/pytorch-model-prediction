package com.example.demo.exception;

import lombok.Getter;

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
