package com.hotel.integration;

import com.hotel.notifications.delivery.NotificationDeliveryOutboxRepository;
import com.hotel.repositories.NotificationRepository;
import com.hotel.services.NotificationIdempotencyWriter;
import com.hotel.services.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({NotificationService.class, NotificationIdempotencyWriter.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class NotificationIdempotencyIntegrationTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationDeliveryOutboxRepository outboxRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        notificationRepository.deleteAll();
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS UX_test_notifications_event_key "
                + "ON notifications(event_key)");
    }

    @Test
    void concurrentEquivalentEventsCreateOneNotificationAndOneDelivery() throws Exception {
        int producerCount = 4;
        CountDownLatch ready = new CountDownLatch(producerCount);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(producerCount)) {
            List<Future<Long>> futures = new ArrayList<>();
            for (int index = 0; index < producerCount; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return notificationService.sendUserNotificationOnce(
                            "refund:approved:concurrent-1",
                            "customer",
                            7L,
                            "REFUND",
                            "Refund approved",
                            "Your refund was approved.").getId();
                }));
            }
            ready.await();
            start.countDown();

            List<Long> ids = new ArrayList<>();
            for (Future<Long> future : futures) {
                ids.add(future.get());
            }

            assertEquals(1, ids.stream().distinct().count());
            assertEquals(1, notificationRepository.count());
            assertEquals(1, outboxRepository.count());
        }
    }
}
