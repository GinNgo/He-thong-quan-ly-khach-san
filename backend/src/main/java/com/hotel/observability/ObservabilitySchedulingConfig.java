package com.hotel.observability;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservabilitySchedulingConfig {

    @Bean(name = "taskScheduler")
    public ObservingTaskScheduler taskScheduler(
            OperationalMetrics metrics,
            @Value("${app.observability.scheduler.pool-size:2}") int poolSize) {
        return new ObservingTaskScheduler(metrics, poolSize);
    }
}
