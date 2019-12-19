package com.gateway.mini.gateway.base;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * 网关请求计数器滑动窗口buckets
 *
 * @author : baihuaiyu
 * @version : 1.0
 * @date : 2019/9/3 9:49
 */
@Slf4j
public class BreakerBuckets {

    /**
     * 滑动窗口桶数量
     */
    private static final int BUCKET_SIZE = 10;
    /**
     * 窗口滑动线程开启延迟
     */
    private static final int WINDOW_SLIDING_INITIAL_DELAY = 1000;
    /**
     * 窗口滑动频率
     */
    private static final int WINDOW_SLIDING_PERIOD = 1000;
    /**
     * 桶对象
     */
    private final AtomicLongArray buckets = new AtomicLongArray(BUCKET_SIZE);
    /**
     * 窗口滑出标志位
     */
    private AtomicLong clearIndex;
    /**
     * 窗口更新标志位
     */
    private AtomicLong insertIndex;

    /**
     * 对标志位进行初始化，同时开启窗口滑动线程
     * @param bucketName
     */
    public BreakerBuckets(String bucketName) {
        clearIndex = new AtomicLong(0L);
        insertIndex = new AtomicLong(0L);
        ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(1,
                new BasicThreadFactory.Builder().namingPattern("buckets-" + bucketName + "-pool-%d").daemon(true).build());
        executorService.scheduleAtFixedRate(() -> {
            try {
                log.debug("网关滑动窗口开始滑动:" + buckets + ", insertIndex:{}, clearIndex:{}", insertIndex, clearIndex);
                clearIndex.set(clearIndex.get() >= BUCKET_SIZE - 1 ? 0L : clearIndex.incrementAndGet());
                refreshBucket(clearIndex.get());
            } catch (Exception e) {
                log.error("网关窗口滑动线程出错", e);
            }
        }, WINDOW_SLIDING_INITIAL_DELAY, WINDOW_SLIDING_PERIOD, TimeUnit.MILLISECONDS);
    }

    /**
     * 滑动窗口计数方法
     * 在bucket中插入的位置应该是目前十个bucket中最后被滑出的
     */
    public void incrementCurrentIndex() {
        insertIndex.set((clearIndex.get() - 1) < 0L ? 9 : (clearIndex.get() - 1));
        buckets.incrementAndGet((int) insertIndex.get());
        log.debug("网关滑动窗口计数标记位:{}", insertIndex);
    }

    /**
     * 窗口滑动刷新桶
     * @param bucketIndex
     */
    private void refreshBucket(long bucketIndex) {
        buckets.set((int) ((bucketIndex >= 9L) ? 0L : (bucketIndex + 1)), 0);
    }

    /**
     * 获取滑动窗口内计数总和
     * @return total
     */
    public long getCurrentCountInBucket() {
        long total = 0;
        for (int i = 0; i < BUCKET_SIZE; i++) {
            total += buckets.get(i);
        }
        return total;
    }

}
