package com.gateway.mini.gateway.retry;

import com.github.rholder.retry.Attempt;
import com.github.rholder.retry.RetryListener;
import lombok.extern.slf4j.Slf4j;


/**
 * 网关熔断重试机制监听器
 *
 * @author : baihuaiyu
 * @date : 2019/9/3 14:44
 * @version : 1.0
 */
@Slf4j
public class GatewayRetryListener implements RetryListener {

    @Override
    public <V> void onRetry(Attempt<V> attempt) {
        log.info("【gateway retry】网关请求尝试 次数={}，delay={}", attempt.getAttemptNumber(), attempt.getAttemptNumber() );
        log.info("【gateway retry】网关请求尝试尝试 hasException={},hasResult={}", attempt.hasException(), attempt.hasResult());
        // 导致异常原因
        if (attempt.hasException()) {
            log.info("【gateway retry】数据请求第{}次失败,{}", attempt.getAttemptNumber(), attempt.getExceptionCause().getMessage());
        } else {
            // 正常返回
            log.info("【gateway retry】数据请求第{}次成功，结果={}", attempt.getAttemptNumber(), attempt.getResult());
        }
    }
}
