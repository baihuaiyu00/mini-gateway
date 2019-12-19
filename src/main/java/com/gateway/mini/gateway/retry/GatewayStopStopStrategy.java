package com.gateway.mini.gateway.retry;

import com.github.rholder.retry.Attempt;
import com.github.rholder.retry.StopStrategy;

/**
 * 网关熔断重试机制重试中断策略
 *
 * @author : baihuaiyu
 * @date : 2019/9/3 15:16
 * @version : 1.0
 */
public class GatewayStopStopStrategy implements StopStrategy {
    @Override
    public boolean shouldStop(Attempt attempt) {
        //当返回成功时停止重试
        return attempt.hasResult();
    }
}
