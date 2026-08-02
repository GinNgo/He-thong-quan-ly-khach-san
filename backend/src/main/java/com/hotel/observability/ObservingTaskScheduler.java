package com.hotel.observability;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.ScheduledMethodRunnable;
import org.springframework.scheduling.Trigger;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

public class ObservingTaskScheduler implements TaskScheduler, InitializingBean, DisposableBean {

    private final OperationalMetrics metrics;
    private final ThreadPoolTaskScheduler delegate = new ThreadPoolTaskScheduler();

    public ObservingTaskScheduler(OperationalMetrics metrics, int poolSize) {
        this.metrics = metrics;
        delegate.setPoolSize(Math.max(1, poolSize));
        delegate.setThreadNamePrefix("hotel-scheduled-");
        delegate.setWaitForTasksToCompleteOnShutdown(false);
    }

    @Override
    public void afterPropertiesSet() {
        delegate.initialize();
    }

    @Override
    public void destroy() {
        delegate.shutdown();
    }

    @Override
    public Clock getClock() {
        return delegate.getClock();
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
        return delegate.schedule(observed(task), trigger);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
        return delegate.schedule(observed(task), startTime);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Instant startTime, Duration period) {
        return delegate.scheduleAtFixedRate(observed(task), startTime, period);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) {
        return delegate.scheduleAtFixedRate(observed(task), period);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Instant startTime, Duration delay) {
        return delegate.scheduleWithFixedDelay(observed(task), startTime, delay);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) {
        return delegate.scheduleWithFixedDelay(observed(task), delay);
    }

    Runnable observed(Runnable task) {
        String jobName = task instanceof ScheduledMethodRunnable scheduledMethod
                ? scheduledMethod.getTarget().getClass().getSimpleName() + "." + scheduledMethod.getMethod().getName()
                : task.getClass().getSimpleName();
        return () -> metrics.observeJobRun(jobName, task);
    }
}
