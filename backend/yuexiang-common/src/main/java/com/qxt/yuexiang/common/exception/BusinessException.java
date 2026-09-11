package com.qxt.yuexiang.common.exception;

import com.qxt.yuexiang.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常：service 层校验失败时抛出，由全局异常处理器统一转成 Result
 */
@Getter
public class BusinessException extends RuntimeException {

    private final Integer code;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BusinessException(String message) {
        super(message);
        this.code = ResultCode.ERROR.getCode();
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
