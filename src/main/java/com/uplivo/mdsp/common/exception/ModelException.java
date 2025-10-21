package com.uplivo.mdsp.common.exception;

import com.uplivo.mdsp.common.enums.ErrorCode;
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
    private final int code;

    public ModelException(String message) {
        super(message);
        this.message = message;
        this.code = ErrorCode.SYSTEM_ERROR.getCode();
    }

    public ModelException(String message, Throwable cause) {
        super(message, cause);
        this.message = message;
        this.code = ErrorCode.SYSTEM_ERROR.getCode();
    }

    public ModelException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.message = errorCode.getMessage();
        this.code = errorCode.getCode();
    }

    public ModelException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.message = errorCode.getMessage();
        this.code = errorCode.getCode();
    }

    public ModelException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.message = customMessage;
        this.code = errorCode.getCode();
    }
}