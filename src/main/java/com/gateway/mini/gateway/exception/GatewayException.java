package com.gateway.mini.gateway.exception;

/**
 * @auther: baihuaiyu
 * @Date: 2019/12/16 19:50
 * @Version: 1.0
 */
public class GatewayException extends RuntimeException {

    public GatewayException() {
    }

    public GatewayException(String message) {
        super(message);
    }

    public GatewayException(String message, Throwable cause) {
        super(message, cause);
    }

    public GatewayException(Throwable cause) {
        super(cause);
    }

    public GatewayException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
