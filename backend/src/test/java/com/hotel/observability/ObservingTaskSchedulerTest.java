package com.hotel.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.ScheduledMethodRunnable;

import static org.assertj.core.api.Assertions.assertThat;

class ObservingTaskSchedulerTest {

    @Test
    void wrapsScheduledMethodsWithAStableJobNameAndMetrics() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ObservingTaskScheduler scheduler = new ObservingTaskScheduler(new OperationalMetrics(registry), 1);
        SampleJob target = new SampleJob();
        Runnable task = new ScheduledMethodRunnable(target, SampleJob.class.getDeclaredMethod("run"));

        scheduler.observed(task).run();

        assertThat(target.executed).isTrue();
        assertThat(registry.get("hotel.scheduled.jobs")
                .tags("job", "samplejob.run", "outcome", "success")
                .timer().count()).isEqualTo(1);
    }

    private static final class SampleJob {
        private boolean executed;

        public void run() {
            executed = true;
        }
    }
}
