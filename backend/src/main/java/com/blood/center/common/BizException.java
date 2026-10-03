package com.blood.center.common;

/**
 * 业务异常：由业务规则主动抛出，统一异常处理会转成 {ok:false, message:"..."}
 */
public class BizException extends RuntimeException {

    public BizException(String message) {
        super(message);
    }
}
