package com.gateway.mini.gateway.aspect;

import com.gateway.mini.gateway.base.BreakerBuckets;
import com.gateway.mini.gateway.base.BreakerCommand;
import com.gateway.mini.gateway.retry.GatewayRetryListener;
import com.gateway.mini.gateway.retry.GatewayStopStopStrategy;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.WaitStrategies;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 网关熔断机制切面
 *
 * @author : baihuaiyu
 * @version : 1.0
 * @date : 2019/9/2 14:35
 */

@Slf4j
@Aspect
@Component
public class GatewayBreakerAspect {
    /**
     * 熔断请求数量阈值
     */
    private static final int THRESHOLD_VALUE = 3;
    /**
     * 熔断请求失败率阈值
     */
    private static final double ERROR_THRESHOLD_PERCENTAGE = 50.0;
    /**
     * 最短熔断持续时间
     */
    private static final long SLEEP_WINDOW_IN_MILLISECONDS = 5000;
    /**
     * 熔断器名称
     */
    private static final String BREAKER_ONE = "breakerDemo";
    private static final String BREAKER_TWO = "breakerExtra";
    /**
     * 默认负数标识熔断关闭
     */
    private static final Long DEFAULT_CIRCUIT_OPEN_TIME = -1L;

    /**
     * 线程池工厂
     */
    private static ThreadFactory namedThreadFactory = new ThreadFactoryBuilder().setNameFormat("gateway-thread-pool-%d").build();

    private static ConcurrentHashMap<String, BreakerCommand> breakerCommandMap = new ConcurrentHashMap<>(2 << 1);

    /**
     * 初始化网关
     */
    static {
        breakerCommandMap.put(BREAKER_ONE,
                BreakerCommand.builder()
                        .breakerName(BREAKER_ONE)
                        .executorService(new ThreadPoolExecutor(10, 10, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(1024), namedThreadFactory, new ThreadPoolExecutor.AbortPolicy()))
                        .totalBuckets(new BreakerBuckets(BREAKER_ONE))
                        .errorBuckets(new BreakerBuckets(BREAKER_ONE))
                        .circuitLatestOpenedTime(new AtomicLong(-1L)).build());
        breakerCommandMap.put(BREAKER_TWO,
                BreakerCommand.builder()
                        .breakerName(BREAKER_TWO)
                        .executorService(new ThreadPoolExecutor(10, 10, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(1024), namedThreadFactory, new ThreadPoolExecutor.AbortPolicy()))
                        .totalBuckets(new BreakerBuckets(BREAKER_TWO))
                        .errorBuckets(new BreakerBuckets(BREAKER_TWO))
                        .circuitLatestOpenedTime(new AtomicLong(-1L)).build());
    }

    @Pointcut(value = "@annotation(GatewayBreaker)")
    public void pointCut() {
    }

    @Around(value = "pointCut()&&@annotation(gatewayBreaker)")
    public Object doPointCut(ProceedingJoinPoint joinPoint, GatewayBreaker gatewayBreaker) throws Exception {
        log.info("Gateway Breaker execute");
        BreakerBuckets totalBucket = breakerCommandMap.get(gatewayBreaker.breakerName()).getTotalBuckets();
        BreakerBuckets errorBucket = breakerCommandMap.get(gatewayBreaker.breakerName()).getErrorBuckets();
        AtomicLong circuitLatestOpenedTime = breakerCommandMap.get(gatewayBreaker.breakerName()).getCircuitLatestOpenedTime();

        //1.若熔断开，执行fallback
        log.info("【gateway】若熔断开，执行fallback:{}", this.isOpen(gatewayBreaker.breakerName()));
        if (this.isOpen(gatewayBreaker.breakerName())) {
            return invokeFallbackMethod(joinPoint, gatewayBreaker.fallbackMethod());
        }

        //2.请求进入，请求总数量bucket自增
        log.info("【gateway】请求进入，请求总数量bucket自增");
        totalBucket.incrementCurrentIndex();
        Object returnValue;

        //3.执行接口请求,在此步骤判断是否抛出异常
        Future future = breakerCommandMap.get(gatewayBreaker.breakerName()).getExecutorService().submit(() -> {
            log.info("【gateway】start");
            try {
                if (attemptExecution(gatewayBreaker.breakerName())) {
                    log.info("【gateway】开始执行jointPoint");
                    return joinPoint.proceed();
                }
            } catch (Throwable throwable) {
                log.info("【gateway】jointPoint请求异常:{}", throwable.getMessage());
            }
            return null;
        });

        //4.获取接口请求结果,在此步骤判断是否超时
        try {
            returnValue = future.get(gatewayBreaker.timeoutValue(), TimeUnit.MILLISECONDS);
            checkReturnValue(returnValue, gatewayBreaker.breakerName());
            log.info("【gateway】切面获取到方法返回值: {}", returnValue);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            log.info("【gateway】任务执行超时，准备执行熔断判断: {}", e);
            errorBucket.incrementCurrentIndex();
            future.cancel(true);

            if (StringUtils.isEmpty(gatewayBreaker.fallbackMethod())) {
                throw new RuntimeException("fallbackMethod is null");
            }
            double errorPercentage = (double) errorBucket.getCurrentCountInBucket() / (double) totalBucket.getCurrentCountInBucket() * 100.0D;
            //1.滑动窗口内的请求数量到达熔断判断阈值 2.错误率超过错误率阈值
            if (totalBucket.getCurrentCountInBucket() >= THRESHOLD_VALUE && errorPercentage >= ERROR_THRESHOLD_PERCENTAGE) {
                log.debug("【gateway】达到熔断条件，执行熔断，目前错误率：{}", errorPercentage);
                //熔断跳起
                circuitLatestOpenedTime.set(System.currentTimeMillis());
                //开启重试机制
                retryRequestExecution(joinPoint, gatewayBreaker);
            }
            //熔断回调方法
            returnValue = invokeFallbackMethod(joinPoint, gatewayBreaker.fallbackMethod());
        }
        return returnValue;
    }

    /**
     * 判断熔断开关是否开启
     *
     * @param breakerName
     * @return
     */
    private boolean isOpen(String breakerName) {
        return breakerCommandMap.get(breakerName).getCircuitLatestOpenedTime().get() >= 0;
    }

    /**
     * 尝试执行
     *
     * @param breakerName
     * @return
     */
    private boolean attemptExecution(String breakerName) {
        long circuitLatestOpenedTime = breakerCommandMap.get(breakerName).getCircuitLatestOpenedTime().get();
        if (circuitLatestOpenedTime == -1) {
            return true;
        } else {
            long currentTime = System.currentTimeMillis();
            //当前时间已经超过上次熔断的时间戳 + 试探窗口
            return currentTime > circuitLatestOpenedTime + SLEEP_WINDOW_IN_MILLISECONDS;
        }
    }

    /**
     * 检查返回值
     *
     * @param returnValue
     * @param breakerName
     */
    private void checkReturnValue(Object returnValue, String breakerName) {
        breakerCommandMap.get(breakerName).getErrorBuckets().incrementCurrentIndex();
    }

    /**
     * 重试机制
     *
     * @param joinPoint
     * @param gatewayBreaker
     * @throws Exception
     */
    private void retryRequestExecution(ProceedingJoinPoint joinPoint, GatewayBreaker gatewayBreaker) throws Exception {
        Retryer<String> retryer = RetryerBuilder.<String>newBuilder()
                .retryIfException()
                .retryIfResult(Objects::isNull)
                .withWaitStrategy(WaitStrategies.incrementingWait(
                        SLEEP_WINDOW_IN_MILLISECONDS, TimeUnit.MILLISECONDS, 5, TimeUnit.SECONDS))
                .withStopStrategy(new GatewayStopStopStrategy())
                .withRetryListener(new GatewayRetryListener())
                .build();
        try {
            retryer.call(() -> {
                log.debug("【gateway retry】重试请求开始执行");
//                joinPoint.proceed();TODO baihuaiyu 根据尝试请求判断是否关闭开关
                log.debug("【gateway retry】关闭熔断开关");
                breakerCommandMap.get(gatewayBreaker.breakerName()).getCircuitLatestOpenedTime().set(DEFAULT_CIRCUIT_OPEN_TIME);
                throw new RuntimeException();
            });
        } catch (Exception e) {
            log.debug("【gateway retry】重试请求执行异常: {}", e.getMessage());
        }
    }


    /**
     * 通过反射执行fallback方法
     *
     * @param joinPoint
     * @param fallback
     * @return
     * @throws Exception
     */
    private String invokeFallbackMethod(ProceedingJoinPoint joinPoint, String fallback) throws Exception {
        Method method = findFallbackMethod(joinPoint, fallback);
        if (method == null) {
            throw new Exception("未找到对应的请求熔断方法:" + fallback);
        } else {
            method.setAccessible(true);
            return (String) method.invoke(joinPoint.getTarget(), joinPoint.getArgs());
        }
    }

    /**
     * 通过反射获取fallback方法
     *
     * @param joinPoint
     * @param fallbackMethodName
     * @return
     * @throws NoSuchMethodException
     */
    private Method findFallbackMethod(ProceedingJoinPoint joinPoint, String fallbackMethodName) throws NoSuchMethodException {
        Signature signature = joinPoint.getSignature();
        MethodSignature methodSignature = null;
        if (signature instanceof MethodSignature) {
            methodSignature = (MethodSignature) signature;
        }
        assert methodSignature != null;
        Method method = methodSignature.getMethod();
        Class<?>[] parameterTypes = method.getParameterTypes();
        Method fallbackMethod = null;
        //这里通过判断必须取和原方法一样参数的fallback方法
        fallbackMethod = joinPoint.getTarget().getClass().getMethod(fallbackMethodName, parameterTypes);
        return fallbackMethod;
    }
}
