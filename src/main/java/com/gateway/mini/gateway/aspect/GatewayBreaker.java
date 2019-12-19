package com.gateway.mini.gateway.aspect;

import java.lang.annotation.*;

/**
 * 网关熔断注解类
 *
 * @author : baihuaiyu
 * @date : 2019/9/2 14:33
 * @version : 1.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GatewayBreaker {
    /**
     * 超时时间阈值
     * @return
     */
    int timeoutValue() default 15000;

    /**
     * 熔断标识值（服务唯一）
     * @return
     */
    String breakerName();

    /**
     * 熔断回调方法
     * @return
     */
    String fallbackMethod() default "";

}
