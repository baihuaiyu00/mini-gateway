package com.gateway.mini.gateway.base;

import lombok.Builder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author : baihuaiyu
 * @date : 2019/9/4 16:25
 * @version : 1.0
 */
@Builder
public class BreakerCommand {
    private String breakerName;
    private ExecutorService executorService;
    private BreakerBuckets totalBuckets;
    private BreakerBuckets errorBuckets;
    private AtomicLong circuitLatestOpenedTime;



    public String getBreakerName() {
        return breakerName;
    }

    public void setBreakerName(String breakerName) {
        this.breakerName = breakerName;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    public BreakerBuckets getTotalBuckets() {
        return totalBuckets;
    }

    public void setTotalBuckets(BreakerBuckets totalBuckets) {
        this.totalBuckets = totalBuckets;
    }

    public BreakerBuckets getErrorBuckets() {
        return errorBuckets;
    }

    public void setErrorBuckets(BreakerBuckets errorBuckets) {
        this.errorBuckets = errorBuckets;
    }

    public AtomicLong getCircuitLatestOpenedTime() {
        return circuitLatestOpenedTime;
    }

    public void setCircuitLatestOpenedTime(AtomicLong circuitLatestOpenedTime) {
        this.circuitLatestOpenedTime = circuitLatestOpenedTime;
    }
}
